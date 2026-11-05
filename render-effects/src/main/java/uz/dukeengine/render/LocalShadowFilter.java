package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.light.Light;
import com.jme3.light.LightFilter;
import com.jme3.light.NullLightFilter;
import com.jme3.light.PointLight;
import com.jme3.light.SpotLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import com.jme3.post.Filter;
import com.jme3.renderer.Camera;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.OpaqueComparator;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.instancing.InstancedGeometry;
import com.jme3.shader.VarType;
import com.jme3.shadow.ShadowUtil;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.FrameBuffer.FrameBufferTarget;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.util.TempVars;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * Shadows of the scene's spot and point lights, for as many of them as a frame can afford ({@link LocalShadowBudget}).
 * Each light that has one gets a place in a single depth atlas: a spot light one map, a point light six lower ones
 * (a cube laid flat), all drawn with the scene's casters by the fork's own pre-shadow pass. One full-screen pass then
 * reads them all, rather than the fork's one pass for each light, and darkens the picture where a light that reaches the
 * pixel finds a caster in the way. Lights past the budget keep their light and have no shadow.
 *
 * <p>A light whose own state and whose casters (position, shape, and that nothing about them is animated) are as they were
 * keeps its maps from the last frame instead of drawing them again.
 *
 * <p>Only lights on the scene's root node are looked at, and only those with a radius (a spot's range): a light that
 * reaches everywhere has no map to draw. shortcut: a light further down the tree is not seen, hand the filter a lights
 * source when a game keeps its lights on moving nodes.
 */
public final class LocalShadowFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(LocalShadowFilter.class.getName());

    /** Slots a frame can be asked for; more is more depth atlas and more uniforms than a frame pays for. */
    public static final int MAX_SLOTS = 8;
    private static final int SPOT_TEXELS = 1024;
    private static final int POINT_TEXELS = 512;
    /** One light's cell of the atlas: the six faces of a cube, three across and two down. */
    private static final int CELL_WIDTH = 3 * POINT_TEXELS;
    private static final int CELL_HEIGHT = 2 * POINT_TEXELS;
    private static final float NEAR = 0.1f;
    /** A cube's faces see a little past their edge, so a filter at the seam has something to read. */
    private static final float CUBE_FOV = 92f;
    private static final float MOST_SPOT_FOV = 160f;
    private static final float SPOT_MARGIN = 1.05f;

    private static final LightFilter NO_LIGHTS = new NullLightFilter();

    private final AssetManager assets;
    private final int slots;
    private final LocalShadowBudget budget;
    private final Camera[] faces;
    private final long[] drawn;
    private final Object[] drawnFor;
    private final long[] lampSignatures;
    private final int columns;
    private final int atlasWidth;
    private final int atlasHeight;
    private final GeometryList casters = new GeometryList(new OpaqueComparator());
    private final RenderState drawing = new RenderState();
    private final HashMap<Object, Float> worth = new HashMap<>();
    private final Vector3f[] axes = {Vector3f.UNIT_X, Vector3f.UNIT_X.negate(), Vector3f.UNIT_Y, Vector3f.UNIT_Y.negate(),
        Vector3f.UNIT_Z, Vector3f.UNIT_Z.negate()};
    private final Matrix4f bias = new Matrix4f(0.5f, 0f, 0f, 0.5f, 0f, 0.5f, 0f, 0.5f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
    private final ColorRGBA fill = new ColorRGBA(0.5f, 0.5f, 0.5f, 1f);
    private long frame;
    private int redrawn;
    private int reused;
    private ViewPort viewPort;
    private RenderManager renderManager;
    private Material preShadow;
    private Texture2D atlas;
    private FrameBuffer atlasBuffer;

    /**
     * @param assets  where the shaders are
     * @param budget  how many lights have a shadow at once, 1 to {@link #MAX_SLOTS}
     */
    public LocalShadowFilter(AssetManager assets, int budget) {
        super("Local Shadows");
        slots = Math.max(1, Math.min(budget, MAX_SLOTS));
        this.budget = new LocalShadowBudget(slots, LocalShadowBudget.FADE);
        columns = slots > 1 ? 2 : 1;
        atlasWidth = columns * CELL_WIDTH;
        atlasHeight = (slots + columns - 1) / columns * CELL_HEIGHT;
        faces = new Camera[slots * 6];
        drawn = new long[slots * 6];
        drawnFor = new Object[slots];
        lampSignatures = new long[slots];
        this.assets = assets;
        for (int i = 0; i < faces.length; i++) {
            faces[i] = new Camera(atlasWidth, atlasHeight);
        }
        drawing.setFaceCullMode(RenderState.FaceCullMode.Back);
        drawing.setPolyOffset(5f, 3f);
        drawing.setColorWrite(false);
        drawing.setDepthWrite(true);
        drawing.setDepthTest(true);
        material = new Material(assets, "Common/MatDefs/Duke/LocalShadows.j3md");
        material.setInt("Slots", slots);
        material.setVector2("AtlasSize", new Vector2f(atlasWidth, atlasHeight));
    }

    /** Whether this GPU can draw these shadows: what it cannot is drawn without them. */
    public static boolean supported(EnumSet<Caps> caps) {
        return caps.contains(Caps.GLSL150) && caps.contains(Caps.FrameBuffer) && caps.contains(Caps.DepthTexture);
    }

    /**
     * What everything but the local lights adds to a surface, as a colour in the units of the lights' own: the ambient
     * and about half the sun. A shadow takes only its light's share of each channel on a pixel, which that decides: where
     * the lamp is all the light, its shadow is black; in a sunny yard it hardly shows. shortcut: one colour for the whole
     * picture, where the sun's own shadow at each pixel would tell; take the sun's term in once this filter runs inside
     * the lighting.
     */
    public void fill(ColorRGBA colour) {
        fill.set(Math.max(colour.r, 1e-3f), Math.max(colour.g, 1e-3f), Math.max(colour.b, 1e-3f), 1f);
    }

    /** Of the shadow maps wanted last frame, how many were drawn anew. */
    public int mapsDrawn() {
        return redrawn;
    }

    /** Of the shadow maps wanted last frame, how many were kept from the frame before. */
    public int mapsKept() {
        return reused;
    }

    @Override
    protected Material getMaterial() {
        return material;
    }

    @Override
    protected boolean isRequiresDepthTexture() {
        return true;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager rm, ViewPort vp, int w, int h) {
        renderManager = rm;
        viewPort = vp;
        atlas = new Texture2D(atlasWidth, atlasHeight, Format.Depth);
        atlas.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
        atlas.setMagFilter(Texture.MagFilter.Nearest);
        atlasBuffer = new FrameBuffer(atlasWidth, atlasHeight, 1);
        atlasBuffer.setName("LocalShadows");
        atlasBuffer.setDepthTarget(FrameBufferTarget.newTarget(atlas));
        material.setTexture("Atlas", atlas);
        Arrays.fill(drawn, 0L);
        Arrays.fill(drawnFor, null);
    }

    @Override
    protected void cleanUpFilter(Renderer r) {
        if (atlasBuffer != null) {
            r.deleteFrameBuffer(atlasBuffer);
        }
    }

    @Override
    protected void preFrame(float tpf) {
        var view = viewPort.getCamera();
        worth.clear();
        var vars = TempVars.get();
        for (var scene : viewPort.getScenes()) {
            for (var light : scene.getLocalLightList()) {
                float reach = reachOf(light);
                float brightness = brightnessOf(light);
                if (reach > 0f && brightness > 1e-3f && light.isEnabled() && light.intersectsFrustum(view, vars)) {
                    worth.put(light, LocalShadowBudget.worth(brightness, reach, view.getLocation().distance(positionOf(light))));
                }
            }
        }
        vars.release();
        budget.update(worth, tpf);

        var position = new Vector4f[slots];
        var aim = new Vector4f[slots];
        var look = new Vector4f[slots];
        var colour = new Vector4f[slots];
        var cone = new Vector4f[slots];
        var tile = new Vector4f[slots];
        var matrices = new Matrix4f[slots * 6];
        for (int s = 0; s < slots; s++) {
            position[s] = new Vector4f(0f, 0f, 0f, 1f);
            aim[s] = new Vector4f(0f, 0f, 0f, -2f);
            look[s] = new Vector4f(0f, 0f, 1f, NEAR);
            colour[s] = new Vector4f();
            cone[s] = new Vector4f();
            tile[s] = new Vector4f();
            for (int f = 0; f < 6; f++) {
                matrices[s * 6 + f] = new Matrix4f();
            }
            var slot = budget.slots()[s];
            if (slot.light == null) {
                drawnFor[s] = null;
                continue;
            }
            place(s, (Light) slot.light, slot.weight, position[s], aim[s], look[s], colour[s], cone[s], tile[s], matrices);
        }
        material.setParam("LightPos", VarType.Vector4Array, position);
        material.setParam("LightAim", VarType.Vector4Array, aim);
        material.setParam("LightLook", VarType.Vector4Array, look);
        material.setParam("LightColour", VarType.Vector4Array, colour);
        material.setParam("LightCone", VarType.Vector4Array, cone);
        material.setParam("LightTile", VarType.Vector4Array, tile);
        material.setParam("FaceMatrix", VarType.Matrix4Array, matrices);
        material.setVector3("Fill", new Vector3f(fill.r, fill.g, fill.b));
        material.setMatrix4("ViewProjectionMatrixInverse", view.getViewProjectionMatrix().invert());
        frame++;
    }

    /** Aims this slot's cameras at its light and writes what the shader needs to read them. */
    private void place(int s, Light light, float weight, Vector4f position, Vector4f aim, Vector4f look, Vector4f colour, Vector4f cone,
            Vector4f tile, Matrix4f[] matrices) {
        var at = positionOf(light);
        float reach = reachOf(light);
        boolean spot = light instanceof SpotLight;
        int texels = spot ? SPOT_TEXELS : POINT_TEXELS;
        float fov = CUBE_FOV;
        position.set(at.x, at.y, at.z, reach);
        look.set(0f, weight, 0f, NEAR);
        var c = light.getColor();
        colour.set(c.r, c.g, c.b, 0f);
        if (light instanceof SpotLight lamp) {
            float outer = lamp.getSpotOuterAngle();
            fov = Math.min(MOST_SPOT_FOV, 2f * outer * SPOT_MARGIN * FastMath.RAD_TO_DEG);
            var direction = lamp.getDirection().normalize();
            aim.set(direction.x, direction.y, direction.z, FastMath.cos(outer * SPOT_MARGIN));
            cone.set(FastMath.cos(lamp.getSpotInnerAngle()), 1f, 0f, 0f);
        }
        look.z = 2f * FastMath.tan(fov * 0.5f * FastMath.DEG_TO_RAD) / texels;
        // The cell this slot has in the atlas, and a face's share of it as texture coordinates.
        float cellX = (s % columns) * CELL_WIDTH;
        float cellY = (s / columns) * CELL_HEIGHT;
        tile.set(cellX / atlasWidth, cellY / atlasHeight, (float) texels / atlasWidth, (float) texels / atlasHeight);

        // What of the light itself the maps depend on; a new light in the slot starts with maps it has not drawn.
        long lamp = 17L;
        for (float value : new float[] {at.x, at.y, at.z, reach, aim.x, aim.y, aim.z, fov}) {
            lamp = lamp * 31L + Float.floatToIntBits(value);
        }
        if (drawnFor[s] != light) {
            drawnFor[s] = light;
            Arrays.fill(drawn, s * 6, s * 6 + 6, 0L);
        }
        for (int f = 0; f < (spot ? 1 : 6); f++) {
            var camera = faces[s * 6 + f];
            float x = cellX + (spot ? 0 : (f % 3) * POINT_TEXELS);
            float y = cellY + (spot ? 0 : (f / 3) * POINT_TEXELS);
            camera.setViewPort(x / atlasWidth, (x + texels) / atlasWidth, y / atlasHeight, (y + texels) / atlasHeight);
            camera.setFrustumPerspective(fov, 1f, NEAR, reach);
            camera.setLocation(at);
            var toward = spot ? ((SpotLight) light).getDirection() : axes[f];
            camera.lookAtDirection(toward, Math.abs(toward.y) > 0.99f ? Vector3f.UNIT_Z : Vector3f.UNIT_Y);
            camera.update();
            camera.updateViewProjection();
            matrices[s * 6 + f].set(bias).multLocal(camera.getViewProjectionMatrix());
        }
        lampSignatures[s] = lamp;
    }

    @Override
    protected void postQueue(RenderQueue queue) {
        var renderer = renderManager.getRenderer();
        boolean any = false;
        redrawn = 0;
        reused = 0;
        LightFilter lights = null;
        for (int s = 0; s < slots; s++) {
            var slot = budget.slots()[s];
            if (slot.light == null) {
                continue;
            }
            if (!any) {
                any = true;
                renderManager.setForcedMaterial(preShadow());
                renderManager.setForcedTechnique("PreShadow");
                renderer.setFrameBuffer(atlasBuffer);
                renderManager.setForcedRenderState(drawing);
                lights = renderManager.getLightFilter();
                renderManager.setLightFilter(NO_LIGHTS);
            }
            for (int f = 0; f < (slot.light instanceof SpotLight ? 1 : 6); f++) {
                drawFace(s * 6 + f, lampSignatures[s]);
            }
        }
        if (any) {
            renderManager.setLightFilter(lights);
            renderManager.setForcedRenderState(null);
            renderer.setFrameBuffer(viewPort.getOutputFrameBuffer());
            renderManager.setForcedMaterial(null);
            renderManager.setForcedTechnique(null);
            renderManager.setCamera(viewPort.getCamera(), false);
        }
    }

    private Material preShadow() {
        if (preShadow == null) {
            preShadow = new Material(assets, "Common/MatDefs/Shadow/PreShadow.j3md");
        }
        return preShadow;
    }

    private void drawFace(int index, long lamp) {
        var camera = faces[index];
        casters.clear();
        for (var scene : viewPort.getScenes()) {
            ShadowUtil.getGeometriesInCamFrustum(scene, camera, RenderQueue.ShadowMode.Cast, casters);
        }
        long signature = lamp * 31L + sign(casters);
        if (signature == drawn[index]) {
            reused++;
            return;
        }
        drawn[index] = signature;
        redrawn++;
        renderManager.setCamera(camera, false);
        renderManager.getRenderer().clearBuffers(false, true, false);
        viewPort.getQueue().renderShadowQueue(casters, renderManager, camera, true);
    }

    /**
     * What these casters look like, to tell a frame's from the last one's. Anything drawn from other than its transform
     * (bones, morphs, instances) is never the same. shortcut: a vertex shader that moves things by itself (wind, waves)
     * is not seen; put such a thing's shadow mode on Receive, or make it return a new signature here.
     */
    private long sign(GeometryList list) {
        long sum = list.size();
        for (int i = 0; i < list.size(); i++) {
            Geometry g = list.get(i);
            if (g instanceof InstancedGeometry || g.getMesh().isAnimated() || g.getMesh().hasMorphTargets()) {
                return ++frame + (1L << 40);
            }
            var t = g.getWorldTransform();
            var p = t.getTranslation();
            var r = t.getRotation();
            var sc = t.getScale();
            long h = System.identityHashCode(g);
            h = h * 31L + Float.floatToIntBits(p.x);
            h = h * 31L + Float.floatToIntBits(p.y);
            h = h * 31L + Float.floatToIntBits(p.z);
            h = h * 31L + Float.floatToIntBits(r.getX());
            h = h * 31L + Float.floatToIntBits(r.getY());
            h = h * 31L + Float.floatToIntBits(r.getZ());
            h = h * 31L + Float.floatToIntBits(r.getW());
            h = h * 31L + Float.floatToIntBits(sc.x) + Float.floatToIntBits(sc.y) + Float.floatToIntBits(sc.z);
            sum += h * 0x9E3779B97F4A7C15L;
        }
        return sum;
    }

    private static float reachOf(Light light) {
        if (light instanceof PointLight point) {
            return point.getRadius();
        }
        if (light instanceof SpotLight spot) {
            return spot.getSpotRange();
        }
        return 0f;
    }

    private static Vector3f positionOf(Light light) {
        return light instanceof SpotLight spot ? spot.getPosition() : ((PointLight) light).getPosition();
    }

    private static float brightnessOf(Light light) {
        var c = light.getColor();
        return 0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b;
    }
}
