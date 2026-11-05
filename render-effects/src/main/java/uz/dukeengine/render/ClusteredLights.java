package uz.dukeengine.render;

import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingSphere;
import com.jme3.light.Light;
import com.jme3.light.LightClusters;
import com.jme3.light.LightFilter;
import com.jme3.light.LightList;
import com.jme3.light.LightProbe;
import com.jme3.light.WeightedProbeBlendingStrategy;
import com.jme3.material.MaterialDef;
import com.jme3.material.TechniqueDef;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.math.FastMath;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.Camera;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.TextureUnitException;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.shader.Shader;
import com.jme3.shader.VarType;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.jme3.util.TempVars;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The view's spot and point lights sorted each frame into clusters of its frustum: {@value #TILES_X} x {@value #TILES_Y}
 * tiles of the screen by {@value #SLICES} slices of depth, spaced evenly in its logarithm. {@code PBRLighting} reads a
 * pixel's cluster and draws its lights in one pass, with the same light and falloff as the fork's pass for each light,
 * so hundreds of lights cost what the few that reach a pixel do. The sun, the probes and the ambient stay as they were.
 *
 * <p>Only lights on the scene's root node with a radius (a spot's range) are sorted, as {@link LocalShadowFilter} sees
 * them; a light further down the tree lights only its own part of it, which a cluster cannot tell, and is drawn the old
 * way, as are lights past {@link #MOST} in view. shortcut: a perspective camera only, a parallel one draws every light
 * the old way.
 *
 * <p>While it is on the view's light filter gives what the clusters draw only the lights they do not, found from the
 * geometry's own nodes up, instead of testing every light of the scene against every geometry: with hundreds of lights that
 * test, not the shading, was what a frame paid for.
 */
public final class ClusteredLights implements SceneProcessor, LightClusters {

    static final int TILES_X = 16;
    static final int TILES_Y = 9;
    static final int SLICES = 24;
    static final int CLUSTERS = TILES_X * TILES_Y * SLICES;
    /** Texels a row of every buffer, as in {@code LightClusters.glsllib}. */
    static final int ROW = 1024;
    /** Lights in view a frame sorts; past them a light is drawn the old way, a pass for each thing it reaches. */
    public static final int MOST = 4096;
    /** Wider than this a spot is a point light to the test, whose cone's sides no longer bound it. */
    private static final float WIDEST_CONE = 0.49f * FastMath.PI;

    private final Set<Light> held = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Light> candidates = new ArrayList<>();
    private final List<Light> unheld = new ArrayList<>();
    private final IdentityHashMap<MaterialDef, Boolean> clusteredDefs = new IdentityHashMap<>();
    private final int[] counts = new int[CLUSTERS];
    private final int[] firsts = new int[CLUSTERS];
    private final float[] lights = new float[MOST * 12];
    private final Vector3f centre = new Vector3f();
    private final Vector3f aim = new Vector3f();
    private final Vector4f scale = new Vector4f();
    private final Vector2f origin = new Vector2f();
    private int[] pairCluster = new int[1024];
    private int[] pairLight = new int[1024];
    private int pairs;
    private int sorted;
    private float sortMillis;
    private Texture2D lightTexture;
    private Texture2D gridTexture;
    private Texture2D indexTexture;
    private Camera camera;
    private RenderManager renderManager;
    private ViewPort viewPort;
    private OwnLights filter;

    public ClusteredLights() {
        lightTexture = buffer(Format.RGBA32F, 1);
        gridTexture = buffer(Format.RG32F, (CLUSTERS + ROW - 1) / ROW);
        indexTexture = buffer(Format.R32F, 16);
    }

    /** Whether this GPU can read the clusters: what it cannot is lit the old way. */
    public static boolean supported(EnumSet<Caps> caps) {
        return caps.contains(Caps.GLSL150) && caps.contains(Caps.FloatTexture);
    }

    /** The light per cluster as {@code PBRLighting} reads it: for each, the first of its lights and how many, red and green. */
    public Texture2D grid() {
        return gridTexture;
    }

    /** Tiles per pixel across and up, slices per unit of the logarithm of depth, and the slice depth 1 would be in. */
    public Vector4f scale() {
        return scale;
    }

    /** How many lights the last frame sorted into its clusters. */
    public int sorted() {
        return sorted;
    }

    /** How long the last frame took to sort them and write the buffers, on the CPU. */
    public float sortMillis() {
        return sortMillis;
    }

    @Override
    public void initialize(RenderManager rm, ViewPort vp) {
        renderManager = rm;
        viewPort = vp;
        if (rm.getLightFilter() != null) {
            filter = new OwnLights(rm.getLightFilter());
            rm.setLightFilter(filter);
        }
    }

    @Override
    public void reshape(ViewPort vp, int w, int h) {
    }

    @Override
    public boolean isInitialized() {
        return viewPort != null;
    }

    @Override
    public void preFrame(float tpf) {
        camera = null;
    }

    @Override
    public void postQueue(RenderQueue rq) {
        var view = viewPort.getCamera();
        if (view.isParallelProjection()) {
            return;
        }
        candidates.clear();
        for (var scene : viewPort.getScenes()) {
            for (var light : scene.getLocalLightList()) {
                candidates.add(light);
            }
        }
        long start = System.nanoTime();
        sort(view, candidates);
        sortMillis = (System.nanoTime() - start) / 1e6f;
        camera = view;
        renderManager.setLightClusters(this);
    }

    @Override
    public void postFrame(FrameBuffer out) {
    }

    @Override
    public void cleanup() {
        if (renderManager != null && renderManager.getLightClusters() == this) {
            renderManager.setLightClusters(null);
        }
        if (filter != null && renderManager.getLightFilter() == filter) {
            renderManager.setLightFilter(filter.others);
        }
        filter = null;
        camera = null;
        viewPort = null;
    }

    @Override
    public void setProfiler(AppProfiler profiler) {
    }

    @Override
    public boolean drawsFor(Camera camera) {
        return camera != null && camera == this.camera;
    }

    @Override
    public boolean holds(Light light) {
        return held.contains(light);
    }

    @Override
    public int bind(Renderer renderer, Shader shader, int unit) {
        try {
            renderer.setTexture(unit, lightTexture);
            renderer.setTexture(unit + 1, gridTexture);
            renderer.setTexture(unit + 2, indexTexture);
        } catch (TextureUnitException e) {
            throw new IllegalStateException("no texture units left for the light clusters from " + unit, e);
        }
        shader.getUniform("g_ClusterLights").setValue(VarType.Int, unit);
        shader.getUniform("g_ClusterGrid").setValue(VarType.Int, unit + 1);
        shader.getUniform("g_ClusterIndices").setValue(VarType.Int, unit + 2);
        shader.getUniform("g_ClusterScale").setValue(VarType.Vector4, scale);
        shader.getUniform("g_ClusterOrigin").setValue(VarType.Vector2, origin);
        return unit + 3;
    }

    /**
     * Sorts {@code lights} into the clusters of {@code view} and writes the buffers: every light {@link #holds} is either in
     * them or cannot reach a pixel of the view. shortcut: on one thread, 0.15 ms for 256 lights over a field and 2.3 ms
     * for 600 in a room by the camera; split the lights over threads when a game keeps that many close.
     */
    void sort(Camera view, List<Light> lights) {
        held.clear();
        unheld.clear();
        pairs = 0;
        sorted = 0;
        float near = view.getFrustumNear();
        float far = view.getFrustumFar();
        float logRange = (float) Math.log(far / near);
        // The tiles' edges as x and y over depth, which is how far across a tile reaches at each depth.
        float left = view.getFrustumLeft() / near;
        float across = (view.getFrustumRight() - view.getFrustumLeft()) / near / TILES_X;
        float bottom = view.getFrustumBottom() / near;
        float up = (view.getFrustumTop() - view.getFrustumBottom()) / near / TILES_Y;
        var matrix = view.getViewMatrix();
        for (var light : lights) {
            float reach;
            Vector3f at;
            if (light instanceof PointLight point) {
                reach = point.getRadius();
                at = point.getPosition();
            } else if (light instanceof SpotLight spot) {
                reach = spot.getSpotRange();
                at = spot.getPosition();
            } else {
                continue;
            }
            if (!light.isEnabled() || !(reach > 0f)) {
                continue;
            }
            // In view space with depth for z, positive ahead of the camera: a mirror, which keeps lengths and angles.
            matrix.mult(at, centre);
            centre.z = -centre.z;
            float nearest = Math.max(centre.z - reach, near);
            float farthest = Math.min(centre.z + reach, far);
            int x0 = tile(Math.min((centre.x - reach) / nearest, (centre.x - reach) / farthest), left, across);
            int x1 = tile(Math.max((centre.x + reach) / nearest, (centre.x + reach) / farthest), left, across);
            int y0 = tile(Math.min((centre.y - reach) / nearest, (centre.y - reach) / farthest), bottom, up);
            int y1 = tile(Math.max((centre.y + reach) / nearest, (centre.y + reach) / farthest), bottom, up);
            if (nearest > farthest || x1 < 0 || x0 >= TILES_X || y1 < 0 || y0 >= TILES_Y) {
                held.add(light);
                continue;
            }
            if (sorted == MOST) {
                continue;
            }
            float cosCone = 0f;
            float sinCone = 0f;
            boolean cone = light instanceof SpotLight spot && spot.getSpotOuterAngle() < WIDEST_CONE;
            if (cone) {
                var spot = (SpotLight) light;
                matrix.multNormal(spot.getDirection(), aim);
                aim.z = -aim.z;
                aim.normalizeLocal();
                cosCone = FastMath.cos(spot.getSpotOuterAngle());
                sinCone = FastMath.sin(spot.getSpotOuterAngle());
            }
            int k0 = slice(nearest, near, logRange);
            int k1 = slice(farthest, near, logRange);
            for (int k = k0; k <= k1; k++) {
                float d0 = sliceDepth(k, near, logRange);
                float d1 = sliceDepth(k + 1, near, logRange);
                // The tiles of the sphere's part in this slice, no wider than its widest cut there: a lamp by the camera
                // spans the screen near it, only a few tiles further on.
                float s0 = Math.max(d0, nearest);
                float s1 = Math.min(d1, farthest);
                float off = Math.max(Math.max(s0 - centre.z, centre.z - s1), 0f);
                float cut = FastMath.sqrt(Math.max(reach * reach - off * off, 0f));
                int sx0 = Math.max(x0, tile(Math.min((centre.x - cut) / s0, (centre.x - cut) / s1), left, across));
                int sx1 = Math.min(x1, tile(Math.max((centre.x + cut) / s0, (centre.x + cut) / s1), left, across));
                int sy0 = Math.max(y0, tile(Math.min((centre.y - cut) / s0, (centre.y - cut) / s1), bottom, up));
                int sy1 = Math.min(y1, tile(Math.max((centre.y + cut) / s0, (centre.y + cut) / s1), bottom, up));
                for (int j = Math.max(sy0, 0); j <= Math.min(sy1, TILES_Y - 1); j++) {
                    float b0 = bottom + j * up;
                    float b1 = b0 + up;
                    float yLow = Math.min(b0 * d0, b0 * d1);
                    float yHigh = Math.max(b1 * d0, b1 * d1);
                    for (int i = Math.max(sx0, 0); i <= Math.min(sx1, TILES_X - 1); i++) {
                        float a0 = left + i * across;
                        float a1 = a0 + across;
                        float xLow = Math.min(a0 * d0, a0 * d1);
                        float xHigh = Math.max(a1 * d0, a1 * d1);
                        if (!sphereTouches(centre.x, centre.y, centre.z, reach, xLow, yLow, d0, xHigh, yHigh, d1)) {
                            continue;
                        }
                        if (cone && !coneTouches(centre, aim, cosCone, sinCone, reach, xLow, yLow, d0, xHigh, yHigh, d1)) {
                            continue;
                        }
                        pair(i + TILES_X * (j + TILES_Y * k), sorted);
                    }
                }
            }
            pack(light, sorted++);
            held.add(light);
        }
        for (var light : lights) {
            if (!held.contains(light)) {
                unheld.add(light);
            }
        }
        upload(view, near, logRange);
    }

    /** Whether this material is lit by the logic that reads the clusters. */
    private boolean clustered(MaterialDef def) {
        return clusteredDefs.computeIfAbsent(def, d -> {
            var defs = d.getTechniqueDefs(TechniqueDef.DEFAULT_TECHNIQUE_NAME);
            return defs != null && defs.stream().allMatch(t -> t.getLightMode() == TechniqueDef.LightMode.SinglePassAndImageBased);
        });
    }

    /**
     * The view's light filter while the clusters are on. A geometry the clusters light, in their view, gets the lights of
     * its own nodes and the scene's lights the clusters do not hold, tested as the fork's filter tests them; anything else
     * is the fork's filter's.
     */
    final class OwnLights implements LightFilter {

        final LightFilter others;
        private final WeightedProbeBlendingStrategy probes = new WeightedProbeBlendingStrategy();
        private Camera current;

        OwnLights(LightFilter others) {
            this.others = others;
        }

        @Override
        public void setCamera(Camera camera) {
            current = camera;
            others.setCamera(camera);
        }

        @Override
        public void filterLights(Geometry geometry, LightList filteredLightList) {
            if (!drawsFor(current) || renderManager.getForcedTechnique() != null || !clustered(geometry.getMaterial().getMaterialDef())) {
                others.filterLights(geometry, filteredLightList);
                return;
            }
            var vars = TempVars.get();
            try {
                var scenes = viewPort.getScenes();
                boolean inScene = false;
                for (Spatial s = geometry; s != null; s = s.getParent()) {
                    if (scenes.contains(s)) {
                        inScene = true;
                    } else {
                        for (var light : s.getLocalLightList()) {
                            keep(light, geometry, filteredLightList, vars);
                        }
                    }
                }
                if (inScene) {
                    for (var light : unheld) {
                        keep(light, geometry, filteredLightList, vars);
                    }
                }
                probes.populateProbes(geometry, filteredLightList);
            } finally {
                vars.release();
            }
        }

        private void keep(Light light, Geometry geometry, LightList kept, TempVars vars) {
            if (!light.isEnabled() || !light.intersectsFrustum(current, vars)) {
                return;
            }
            var bound = geometry.getWorldBound();
            if (bound instanceof BoundingBox box && !light.intersectsBox(box, vars)) {
                return;
            }
            if (bound instanceof BoundingSphere sphere && !Float.isInfinite(sphere.getRadius()) && !light.intersectsSphere(sphere, vars)) {
                return;
            }
            if (light instanceof LightProbe probe) {
                probes.registerProbe(probe);
            } else {
                kept.add(light);
            }
        }
    }

    /** The tile across (or up) that a slope of {@code x} over depth falls in; outside the screen, past its ends. */
    private static int tile(float x, float start, float size) {
        return (int) Math.floor((x - start) / size);
    }

    /** The slice depth {@code depth} is in, as {@code LightClusters.glsllib} finds it. */
    static int slice(float depth, float near, float logRange) {
        int k = (int) Math.floor(Math.log(depth / near) / logRange * SLICES);
        return Math.max(0, Math.min(k, SLICES - 1));
    }

    /** Where slice {@code k} starts. */
    static float sliceDepth(int k, float near, float logRange) {
        return near * (float) Math.exp(logRange * k / SLICES);
    }

    /** Whether a sphere touches a box, both in the same space. */
    static boolean sphereTouches(float x, float y, float z, float radius, float x0, float y0, float z0, float x1, float y1, float z1) {
        float dx = Math.max(Math.max(x0 - x, x - x1), 0f);
        float dy = Math.max(Math.max(y0 - y, y - y1), 0f);
        float dz = Math.max(Math.max(z0 - z, z - z1), 0f);
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    /**
     * Whether a cone from {@code apex} along {@code axis} (unit), of this half angle and length, may touch a box: tested
     * against the sphere round the box (Wronski 2017), so it may say yes for a box the cone just misses, never no for one
     * it touches.
     */
    static boolean coneTouches(Vector3f apex, Vector3f axis, float cos, float sin, float length, float x0, float y0, float z0,
            float x1, float y1, float z1) {
        float hx = (x1 - x0) * 0.5f;
        float hy = (y1 - y0) * 0.5f;
        float hz = (z1 - z0) * 0.5f;
        float radius = FastMath.sqrt(hx * hx + hy * hy + hz * hz);
        float vx = x0 + hx - apex.x;
        float vy = y0 + hy - apex.y;
        float vz = z0 + hz - apex.z;
        float along = vx * axis.x + vy * axis.y + vz * axis.z;
        float aside = FastMath.sqrt(Math.max(vx * vx + vy * vy + vz * vz - along * along, 0f));
        return cos * aside - along * sin <= radius && along <= radius + length && along >= -radius;
    }

    private void pair(int cluster, int light) {
        if (pairs == pairCluster.length) {
            pairCluster = Arrays.copyOf(pairCluster, pairs * 2);
            pairLight = Arrays.copyOf(pairLight, pairs * 2);
        }
        pairCluster[pairs] = cluster;
        pairLight[pairs++] = light;
    }

    /** A light as three texels, as the fork's lighting logic writes {@code g_LightData}. */
    private void pack(Light light, int index) {
        int o = index * 12;
        var c = light.getColor();
        lights[o] = c.r;
        lights[o + 1] = c.g;
        lights[o + 2] = c.b;
        lights[o + 3] = light.getType().getId();
        if (light instanceof SpotLight spot) {
            var p = spot.getPosition();
            var d = spot.getDirection();
            set(o + 4, p.x, p.y, p.z, spot.getInvSpotRange());
            set(o + 8, d.x, d.y, d.z, spot.getPackedAngleCos());
        } else {
            var point = (PointLight) light;
            var p = point.getPosition();
            set(o + 4, p.x, p.y, p.z, point.getInvRadius());
            set(o + 8, 0f, 0f, 0f, 0f);
        }
    }

    private void set(int o, float x, float y, float z, float w) {
        lights[o] = x;
        lights[o + 1] = y;
        lights[o + 2] = z;
        lights[o + 3] = w;
    }

    private void upload(Camera view, float near, float logRange) {
        Arrays.fill(counts, 0);
        for (int p = 0; p < pairs; p++) {
            counts[pairCluster[p]]++;
        }
        var grid = floats(gridTexture);
        int first = 0;
        for (int c = 0; c < CLUSTERS; c++) {
            firsts[c] = first;
            grid.put(c * 2, first).put(c * 2 + 1, counts[c]);
            first += counts[c];
        }
        indexTexture = grown(indexTexture, Format.R32F, pairs);
        var indices = floats(indexTexture);
        // Pairs were made light by light, so each cluster's list keeps the lights' order.
        for (int p = 0; p < pairs; p++) {
            indices.put(firsts[pairCluster[p]]++, pairLight[p]);
        }
        lightTexture = grown(lightTexture, Format.RGBA32F, sorted * 3);
        floats(lightTexture).put(lights, 0, sorted * 12);
        for (var texture : new Texture2D[] {lightTexture, gridTexture, indexTexture}) {
            texture.getImage().setUpdateNeeded();
        }
        float width = view.getWidth() * (view.getViewPortRight() - view.getViewPortLeft());
        float height = view.getHeight() * (view.getViewPortTop() - view.getViewPortBottom());
        scale.set(TILES_X / width, TILES_Y / height, SLICES / logRange, -(float) Math.log(near) * SLICES / logRange);
        origin.set(view.getViewPortLeft() * view.getWidth(), view.getViewPortBottom() * view.getHeight());
    }

    /** The first light of {@code cluster} and how many it has, as uploaded. */
    int[] cluster(int cluster) {
        var grid = floats(gridTexture);
        return new int[] {(int) grid.get(cluster * 2), (int) grid.get(cluster * 2 + 1)};
    }

    /** The {@code n}th entry of the index list, as uploaded. */
    int index(int n) {
        return (int) floats(indexTexture).get(n);
    }

    /** The {@code n}th float of the light buffer, as uploaded. */
    float light(int n) {
        return floats(lightTexture).get(n);
    }

    private static FloatBuffer floats(Texture2D texture) {
        return texture.getImage().getData(0).asFloatBuffer();
    }

    /** {@code texture} if it holds {@code texels}, else a taller one: rows double, so it grows a few times at most. */
    private static Texture2D grown(Texture2D texture, Format format, int texels) {
        int rows = texture.getImage().getHeight();
        if (rows * ROW >= texels) {
            return texture;
        }
        while (rows * ROW < texels) {
            rows *= 2;
        }
        return buffer(format, rows);
    }

    private static Texture2D buffer(Format format, int rows) {
        var image = new Image(format, ROW, rows, BufferUtils.createByteBuffer(ROW * rows * format.getBitsPerPixel() / 8),
                ColorSpace.Linear);
        var texture = new Texture2D(image);
        texture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        texture.setMagFilter(Texture.MagFilter.Nearest);
        return texture;
    }
}
