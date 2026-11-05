package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.Image.Format;
import com.jme3.texture.Texture;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Fog that thins with height, over the linear HDR picture: the light the air takes from what is seen through it is worked
 * out along each pixel's ray in closed form ({@link #opticalDepth}), for the sky as well, at a distance so great that only
 * a ray near the horizon is fogged through: the horizon takes the colour of the fog and so is not washed out wrongly. What
 * the air gives back is the colour of the far air (the ambient, or the horizon of the {@link Atmosphere}) and the sun
 * scattered toward the eye by a Henyey-Greenstein lobe.
 *
 * <p>With {@link PictureSettings#shafts} and the cascades of a {@link SunShadowFilter} ({@link #cascadesFrom}) the sun's
 * share is marched instead: 32 jittered steps per pixel of a half size picture, each asking the cascade it falls in whether
 * the sun reaches it, then a blur over the 4x4 of the jitter and an upsample, both weighed by depth so that no light bleeds
 * over an edge. With no cascades, or when they are not drawn this frame, the fog is the plain one.
 *
 * <p>shortcut: the sun's light is not dimmed on its way through the fog to a point, only the view is; a game that wants a
 * red sunset through thick fog gives the sun's colour already reddened. The cascades are the first group's of
 * {@link SunShadowFilter}s, so a caster of another look does not cut a shaft; one tap per step, no cascade blend.
 */
public final class HeightFogFilter extends Filter {

    private static final Logger LOG = Logger.getLogger(HeightFogFilter.class.getName());
    /** Seen from so far that only a ray along the ground is fogged through. */
    private static final float SKY_DISTANCE = 20_000f;
    /** Past this much of the view's way through the air there is hardly any sun left to see: 3 optical depths. */
    private static final float DEPTHS_SEEN = 3f;
    private static final float MOST_REACH = 300f;
    /** The sun below this much over the horizon gives no light to the fog, up to full at the second. */
    private static final float SUN_FROM = -0.02f;
    private static final float SUN_TO = 0.08f;
    private static final String[] CASCADE = {"ShadowMap0", "ShadowMap1", "ShadowMap2", "ShadowMap3",
        "LightViewProjectionMatrix0", "LightViewProjectionMatrix1", "LightViewProjectionMatrix2",
        "LightViewProjectionMatrix3", "Splits", "CascadeTexels", "CascadeDepths", "ViewDepth"};

    private final float density;
    private final float falloff;
    private final boolean shafts;
    private final boolean atmosphere;
    private final Matrix4f inverse = new Matrix4f();
    private final Vector3f toSun = new Vector3f(0f, 1f, 0f);
    private final Vector3f sunColour = new Vector3f();
    private final Vector3f airColour = new Vector3f(0.5f, 0.5f, 0.5f);
    private ColorRGBA lightSeen;
    private ColorRGBA ambientSeen;
    private float sunShare = 1f;
    private SunShadowFilter source;
    private List<Pass> marching = List.of();
    private Pass march;
    private Material shaftMaterial;
    private Material blurMaterial;
    private Texture upsampled;
    private boolean marchingNow;
    private ViewPort view;

    /** The fog and, if asked for, the shafts, that {@code picture} describes; its density is above 0. */
    public HeightFogFilter(PictureSettings picture) {
        super("HeightFog");
        this.density = picture.fogDensity();
        this.falloff = picture.fogFalloff();
        this.shafts = picture.shafts();
        this.atmosphere = picture.atmosphere();
    }

    /**
     * What the light of the air is optically: the density along a ray, integrated. The density at height {@code y} is
     * {@code density * e^(-falloff * y)}; a ray of {@code distance} from {@code eyeY} rising by {@code dirY} per unit has
     * {@code density * e^(-falloff * eyeY) * distance * (1 - e^-x) / x} of it, {@code x = falloff * dirY * distance}.
     * The same as {@code opticalDepth} in HeightFog.frag: this is the one the test pins down.
     */
    static double opticalDepth(double density, double falloff, double eyeY, double dirY, double distance) {
        double atEye = density * Math.exp(Math.min(-falloff * eyeY, 80.0));
        return atEye * distance * stretch(Math.max(falloff * dirY * distance, -30.0));
    }

    /** {@code (1 - e^-x) / x}, whose limit at 0 is 1: the series where the quotient would lose its digits. */
    static double stretch(double x) {
        return Math.abs(x) < 0.01 ? 1.0 - x * 0.5 + x * x / 6.0 - x * x * x / 24.0 : (1.0 - Math.exp(-x)) / x;
    }

    /**
     * The shadow maps whose cascades cut the shafts, or none (null). They are read as the source has drawn them this frame:
     * the source must come earlier in the same picture chain.
     */
    public void cascadesFrom(SunShadowFilter shadows) {
        this.source = shadows;
    }

    /**
     * Every frame the sun is asked about: toward the sun, the light it gives (the colour above the air under an
     * {@link PictureSettings#atmosphere}, which tints it for the hour; else the colour the game lights the ground with) and
     * the ambient, which colours the air when there is no atmosphere. A sun below the horizon gives the fog no light.
     */
    public void sun(Vector3f toward, ColorRGBA light, ColorRGBA ambient) {
        var direction = toward.normalize();
        if (direction.distanceSquared(toSun) < 1e-8f && light.equals(lightSeen) && ambient.equals(ambientSeen)) {
            return;
        }
        lightSeen = light.clone();
        ambientSeen = ambient.clone();
        float share = FastMath.clamp((direction.y - SUN_FROM) / (SUN_TO - SUN_FROM), 0f, 1f);
        share = share * share * (3f - 2f * share);
        sunColour.set(light.r, light.g, light.b).multLocal(share);
        var air = ambient;
        if (atmosphere) {
            var through = Atmosphere.transmittance(direction);
            sunColour.multLocal(through.r, through.g, through.b);
            air = horizon(new Atmosphere(direction, light));
        }
        toSun.set(direction);
        airColour.set(air.r, air.g, air.b);
        sunShare = share;
    }

    /** The sky just over the horizon, mean of four ways round: the colour the far air takes. */
    static ColorRGBA horizon(SkyProbe.Sky sky) {
        var sum = new ColorRGBA(0f, 0f, 0f, 1f);
        for (var around : new float[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            var seen = sky.at(new Vector3f(around[0], 0.07f, around[1]));
            sum.r += seen.r * 0.25f;
            sum.g += seen.g * 0.25f;
            sum.b += seen.b * 0.25f;
        }
        return sum;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        view = vp;
        // A resize makes the passes and the material again.
        marchingNow = false;
        marching = List.of();
        march = null;
        postRenderPasses = new ArrayList<>();
        material = new Material(manager, "Common/MatDefs/Duke/HeightFog.j3md");
        if (!renderManager.getRenderer().getCaps().contains(Caps.GLSL150)) {
            LOG.warning("this GPU cannot draw fog (GLSL 1.50): the picture goes without it");
            setEnabled(false);
            return;
        }
        material.setFloat("Density", density);
        material.setFloat("Falloff", falloff);
        material.setFloat("SkyDistance", SKY_DISTANCE);
        if (!shafts || !renderManager.getRenderer().getCaps().contains(Caps.FloatColorBuffer)) {
            return;
        }
        int lowW = Math.max(1, w / 2);
        int lowH = Math.max(1, h / 2);
        shaftMaterial = new Material(manager, "Common/MatDefs/Duke/Shafts.j3md");
        shaftMaterial.setFloat("Density", density);
        shaftMaterial.setFloat("Falloff", falloff);
        shaftMaterial.setFloat("Reach", Math.min(DEPTHS_SEEN / density, MOST_REACH));
        shaftMaterial.setFloat("SkyDistance", SKY_DISTANCE);
        blurMaterial = new Material(manager, "Common/MatDefs/Duke/ShaftsBlur.j3md");
        march = new Pass("shafts") {
            @Override
            public boolean requiresDepthAsTexture() {
                return true;
            }

            @Override
            public void beforeRender() {
                for (var name : CASCADE) {
                    var param = source.getShadowMaterial().getParam(name);
                    if (param != null) {
                        shaftMaterial.setParam(name, param.getVarType(), param.getValue());
                    }
                }
            }
        };
        march.init(renderManager.getRenderer(), lowW, lowH, Format.RG32F, Format.Depth, 1, shaftMaterial);
        var blur = new Pass("shafts blur");
        blur.init(renderManager.getRenderer(), lowW, lowH, Format.RG32F, Format.Depth, 1, blurMaterial);
        for (var pass : List.of(march, blur)) {
            pass.getRenderedTexture().setMinFilter(Texture.MinFilter.NearestNoMipMaps);
            pass.getRenderedTexture().setMagFilter(Texture.MagFilter.Nearest);
            postRenderPasses.add(pass);
        }
        blurMaterial.setTexture("Shafts", march.getRenderedTexture());
        upsampled = blur.getRenderedTexture();
    }

    /** Whether the source has maps to read this frame, the sun gives light and shafts are asked for. */
    private boolean canMarch() {
        if (march == null || source == null || !source.isEnabled() || sunShare < 1e-3f) {
            return false;
        }
        var map = source.getShadowMaterial().getParam("ShadowMap0");
        return map != null && map.getValue() != null;
    }

    @Override
    protected void postQueue(RenderQueue queue) {
        if (view == null || material == null) {
            return;
        }
        view.getCamera().getViewProjectionMatrix().invert(inverse);
        material.setMatrix4("ViewProjectionMatrixInverse", inverse);
        material.setVector3("ToSun", toSun);
        material.setVector3("Sun", sunColour);
        material.setVector3("Air", airColour);
        if (march != null) {
            shaftMaterial.setMatrix4("ViewProjectionMatrixInverse", inverse);
            shaftMaterial.setInt("Frame", TaaFilter.turn(processor));
        }
        // The source's postQueue has run before this one: if it has no maps for this frame, it has taken them off.
        boolean now = canMarch();
        marching = now ? postRenderPasses : List.of();
        if (now != marchingNow) {
            marchingNow = now;
            if (now) {
                material.setTexture("Shafts", upsampled);
            } else {
                material.clearParam("Shafts");
            }
        }
    }

    @Override
    protected List<Pass> getPostRenderPasses() {
        return marching;
    }

    @Override
    protected boolean isRequiresDepthTexture() {
        return true;
    }

    @Override
    protected Material getMaterial() {
        return material;
    }
}
