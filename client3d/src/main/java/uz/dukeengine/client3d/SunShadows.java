package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.shadow.AbstractShadowFilter;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Shadows cast from the sun onto the ground and every drawn model, the caster's own included — the reference's volume
 * shadows ({@code W3DVolumetricShadowManager}): one light, the sun; what they fall on multiplied by one colour, once
 * however many of them overlap ({@code renderStencilShadows}); each look's cast from no lower a sun than its least
 * height allows ({@code ShadowSizeX}). Drawn by shadow maps: one set for each least height a look names, from the sun
 * raised to it, each set drawing its own casters alone. Nothing of it reaches the simulation.
 */
final class SunShadows {

    /** The reference's shadow colour where a map names none ({@code W3DShadow.cpp}: {@code 7FA0A0A0}). */
    static final ColorRGBA DEFAULT_COLOUR = new ColorRGBA(0xA0 / 255f, 0xA0 / 255f, 0xA0 / 255f, 1f);
    private static final String LEAST = "shadow.least";
    private static final int MAP_SIZE = 2048;
    private static final int SPLITS = 3;

    private final AssetManager assets;
    private final ViewPort view;
    /** Each least height's shadows, 0 for the sun as it stands; made as the first caster of it is drawn. */
    private final Map<Float, Group> groups = new TreeMap<>();
    private FilterPostProcessor post;
    private Vector3f sun;
    private ColorRGBA colour = DEFAULT_COLOUR;
    private boolean on = true;

    SunShadows(AssetManager assets, ViewPort view, Vector3f sun) {
        this.assets = assets;
        this.view = view;
        this.sun = sun.normalize();
    }

    /**
     * The sun's direction raised to at least {@code leastDegrees} above the ground, on its own bearing — the
     * reference's least height a volume is cast from: with the sun lower, cast as from that height.
     */
    static Vector3f raised(Vector3f sun, float leastDegrees) {
        var down = sun.normalize();
        float least = FastMath.clamp(leastDegrees, 0f, 90f) * FastMath.DEG_TO_RAD;
        float height = FastMath.asin(FastMath.clamp(-down.y, -1f, 1f));
        var across = new Vector3f(down.x, 0f, down.z);
        if (height >= least || across.lengthSquared() < 1e-12f) {
            return down;
        }
        across.normalizeLocal().multLocal(FastMath.cos(least));
        return new Vector3f(across.x, -FastMath.sin(least), across.z);
    }

    /** The sun moved — a new map's hour: every set of shadows cast from it, raised as each is. */
    void sun(Vector3f direction) {
        this.sun = direction.normalize();
        groups.forEach((least, group) -> group.light.setDirection(raised(sun, least)));
    }

    /** What shadows multiply what they fall on by: the map's colour. */
    void colour(ColorRGBA colour) {
        this.colour = colour == null ? DEFAULT_COLOUR : colour.clone();
        groups.values().forEach(group -> group.getShadowMaterial().setColor("ShadowColor", this.colour));
    }

    /** Every shadow drawn, or none: the player's option. */
    void on(boolean on) {
        this.on = on;
        groups.values().forEach(group -> group.setEnabled(on));
    }

    boolean isOn() {
        return on;
    }

    /**
     * A thing's body as it is drawn this frame: casting from its opaque pieces — a see-through piece casts none —
     * from no lower a sun than {@code leastDegrees}, where {@code casts}; only receiving otherwise.
     */
    void cast(Spatial body, boolean casts, float leastDegrees) {
        if (body == null) {
            return;
        }
        float least = casts ? Math.max(0f, leastDegrees) : 0f;
        body.depthFirstTraversal(spatial -> {
            if (spatial instanceof Geometry piece) {
                boolean opaque = piece.getQueueBucket() != RenderQueue.Bucket.Transparent
                        && (piece.getMaterial() == null
                        || piece.getMaterial().getAdditionalRenderState().getBlendMode() == RenderState.BlendMode.Off);
                piece.setShadowMode(casts && opaque ? RenderQueue.ShadowMode.CastAndReceive
                        : RenderQueue.ShadowMode.Receive);
                piece.setUserData(LEAST, least);
            }
        });
        if (casts) {
            groupFor(least);
        }
    }

    /** The shadows cast from a sun no lower than {@code least} degrees, made the first time a caster asks. */
    Group groupFor(float least) {
        var group = groups.get(least);
        if (group != null) {
            return group;
        }
        group = new Group(assets, least, raised(sun, least));
        group.getShadowMaterial().setColor("ShadowColor", colour);
        group.setEnabled(on);
        groups.put(least, group);
        if (post == null) {
            post = new FilterPostProcessor(assets);
            view.addProcessor(post);
        }
        post.addFilter(group);
        return group;
    }

    /** One least height's shadows: the sun raised to it, its casters alone, multiplied by the colour. */
    static final class Group extends AbstractShadowFilter<Caster> {
        final DirectionalLight light;

        Group(AssetManager assets, float least, Vector3f direction) {
            super(assets, MAP_SIZE, new Caster(assets, least));
            light = new DirectionalLight(direction);
            shadowRenderer.setLight(light);
            material = new Material(assets, "MatDefs/duke/SunShadow.j3md");
            shadowRenderer.wear(material);
            setShadowIntensity(1f);
            setEdgeFilteringMode(EdgeFilteringMode.PCF4);
        }
    }

    /** A sun's shadow maps drawn from its least height's casters alone. */
    static final class Caster extends DirectionalLightShadowRenderer {
        private final float least;

        Caster(AssetManager assets, float least) {
            super(assets, MAP_SIZE, SPLITS);
            this.least = least;
        }

        void wear(Material material) {
            setPostShadowMaterial(material);
        }

        @Override
        protected GeometryList getOccludersToRender(int shadowMapIndex, GeometryList shadowMapOccluders) {
            super.getOccludersToRender(shadowMapIndex, shadowMapOccluders);
            var all = new ArrayList<Geometry>(shadowMapOccluders.size());
            for (var geometry : shadowMapOccluders) {
                all.add(geometry);
            }
            shadowMapOccluders.clear();
            for (var geometry : all) {
                Float its = geometry.getUserData(LEAST);
                if ((its == null ? 0f : its) == least) {
                    shadowMapOccluders.add(geometry);
                }
            }
            return shadowMapOccluders;
        }
    }
}
