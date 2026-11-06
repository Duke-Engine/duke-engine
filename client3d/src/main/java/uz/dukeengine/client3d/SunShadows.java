package uz.dukeengine.client3d;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.post.FilterPostProcessor;
import com.jme3.renderer.Caps;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Spatial;
import com.jme3.shadow.AbstractShadowFilter;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.shadow.EdgeFilteringMode;
import com.jme3.texture.Image;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import uz.dukeengine.render.HeightFogFilter;
import uz.dukeengine.render.PictureSettings;
import uz.dukeengine.render.SunShadowFilter;

/**
 * Shadows cast from the sun onto the ground and every drawn model, the caster's own included — the reference's volume
 * shadows ({@code W3DVolumetricShadowManager}): one light, the sun; what they fall on multiplied by one colour, once
 * however many of them overlap ({@code renderStencilShadows}); each look's cast from no lower a sun than its least
 * height allows ({@code ShadowSizeX}). Drawn by shadow maps: one set for each least height a look names, from the sun
 * raised to it, each set drawing its own casters alone. Nothing of it reaches the simulation.
 */
final class SunShadows {

    private static final Logger LOG = Logger.getLogger(SunShadows.class.getName());
    /** The reference's shadow colour where a map names none ({@code W3DShadow.cpp}: {@code 7FA0A0A0}). */
    static final ColorRGBA DEFAULT_COLOUR = new ColorRGBA(0xA0 / 255f, 0xA0 / 255f, 0xA0 / 255f, 1f);
    private static final String LEAST = "shadow.least";
    private static final int MAP_SIZE = 2048;
    private static final int SPLITS = 3;

    private final AssetManager assets;
    private final ViewPort view;
    /** Each least height's shadows, 0 for the sun as it stands; made as the first caster of it is drawn. */
    private final Map<Float, AbstractShadowFilter<?>> groups = new TreeMap<>();
    /** What each of them is cast from. */
    private final Map<Float, DirectionalLight> lights = new TreeMap<>();
    /** What a physically based picture asks of its shadows; null, they are drawn as they always were. */
    private PictureSettings picture;
    private FilterPostProcessor post;
    /** What is drawn over the finished picture, always after every set of shadows. */
    private List<Filter> finishing = List.of();
    /** The fog whose sun shafts the sun's maps cut, if the picture has one. */
    private HeightFogFilter fog;
    private Vector3f sun;
    private ColorRGBA colour = DEFAULT_COLOUR;
    private boolean on = true;
    /** How far from the camera shadows are drawn, fading out over the last of it; the camera's whole reach where 0. */
    private float reach;

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

    /** Shadows drawn only so far from the camera: the shadow maps' pixels all spent there, and nothing beyond it drawn twice. */
    void reach(float units) {
        reach = units;
        groups.values().forEach(this::reachOf);
    }

    private void reachOf(AbstractShadowFilter<?> group) {
        if (reach > 0f) {
            group.setShadowZExtend(reach);
            group.setShadowZFadeLength(reach * 0.2f);
        }
    }

    /** The sun moved — a new map's hour: every set of shadows cast from it, raised as each is. */
    void sun(Vector3f direction) {
        this.sun = direction.normalize();
        lights.forEach((least, light) -> light.setDirection(raised(sun, least)));
        groups.values().forEach(group -> {
            if (group instanceof SunShadowFilter soft) {
                soft.sun(sun);
            }
        });
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

    /**
     * Shadows made from now on drawn as {@code picture} asks, by {@link SunShadowFilter}; left as they were where this GPU
     * cannot draw them.
     */
    void drawnAs(PictureSettings picture, EnumSet<Caps> caps) {
        if (SunShadowFilter.supported(caps)) {
            this.picture = picture;
        } else {
            LOG.warning("this GPU has no GLSL 1.50: the sun's shadows stay hard edged, with no contact shadows");
        }
    }

    /** The shadows cast from a sun no lower than {@code least} degrees, made the first time a caster asks. */
    AbstractShadowFilter<?> groupFor(float least) {
        var made = groups.get(least);
        if (made != null) {
            return made;
        }
        AbstractShadowFilter<?> group;
        if (picture == null) {
            var drawn = new Group(assets, least, raised(sun, least));
            lights.put(least, drawn.light);
            group = drawn;
        } else {
            var light = new DirectionalLight(raised(sun, least));
            lights.put(least, light);
            var soft = new SunShadowFilter(assets, light, picture, groups.isEmpty(), geometry -> least == leastOf(geometry));
            soft.sun(sun);
            group = soft;
        }
        group.getShadowMaterial().setColor("ShadowColor", colour);
        group.setEnabled(on);
        reachOf(group);
        groups.put(least, group);
        cutShafts();
        finishing.forEach(post()::removeFilter);
        post().addFilter(group);
        finishing.forEach(post()::addFilter);
        return group;
    }

    FilterPostProcessor post() {
        if (post == null) {
            post = new FilterPostProcessor(assets);
            view.addProcessor(post);
        }
        return post;
    }

    /** {@code filters} over the finished picture, which is in HDR from now on: see {@link PhysicalLook}. */
    void finishWith(List<Filter> filters) {
        finishing = filters;
        fog = filters.stream().filter(HeightFogFilter.class::isInstance).map(HeightFogFilter.class::cast).findFirst().orElse(null);
        cutShafts();
        post().setFrameBufferFormat(Image.Format.RGB16F);
        filters.forEach(post()::addFilter);
    }

    /** The fog's shafts are cut by the maps of the lowest least height's group, where the sun stands as it is. */
    private void cutShafts() {
        if (fog != null) {
            groups.values().stream().filter(SunShadowFilter.class::isInstance).map(SunShadowFilter.class::cast).findFirst()
                    .ifPresent(fog::cascadesFrom);
        }
    }

    private static float leastOf(Geometry geometry) {
        Float its = geometry.getUserData(LEAST);
        return its == null ? 0f : its;
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
                if (leastOf(geometry) == least) {
                    shadowMapOccluders.add(geometry);
                }
            }
            return shadowMapOccluders;
        }
    }
}
