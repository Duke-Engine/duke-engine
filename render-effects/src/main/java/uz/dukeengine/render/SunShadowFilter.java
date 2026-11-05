package uz.dukeengine.render;

import com.jme3.asset.AssetManager;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Caps;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.shadow.AbstractShadowFilter;
import com.jme3.shadow.CompareMode;
import java.util.EnumSet;
import java.util.function.Predicate;

/**
 * The sun's shadows over a finished picture: {@link SunCascades}, stabilised and blended into each other; soft where a
 * blocker stands far from what it shades, as far as the sun's own size makes them ({@link PictureSettings#softShadows});
 * a line where round things turn from the sun that comes from their smooth normal rather than the map's texels; and, from
 * {@link SceneBuffers}, a short ray over the screen's depth for the contact the maps are too coarse to see
 * ({@link PictureSettings#contactShadows}). Without scene buffers the contact shadows go, the line comes off the faces.
 */
public final class SunShadowFilter extends AbstractShadowFilter<SunCascades> {

    private static final int MAP_SIZE = 2048;
    /** How wide the sun is as far as shadows go. The real half degree gives a house's shadow an edge of one pixel from an RTS camera. */
    private static final float SUN_DEGREES = 2f;
    /** shortcut: one shadow distance for every game's world size, make it a setting when a game needs another. */
    private static final float DISTANCE = 500f;
    private static final float FADE = 50f;

    private final boolean contact;

    /**
     * @param light    what the maps are drawn from
     * @param picture  which of the effects are asked for
     * @param once     whether this filter also draws what must fall on a pixel only once, when a scene has several
     * @param casts    which of the scene's casters are this light's
     */
    public SunShadowFilter(AssetManager assets, DirectionalLight light, PictureSettings picture, boolean once,
            Predicate<Geometry> casts) {
        super(assets, MAP_SIZE, new SunCascades(assets, MAP_SIZE, casts));
        contact = once && picture.contactShadows();
        shadowRenderer.setLight(light);
        material = new Material(assets, "Common/MatDefs/Duke/SunShadows.j3md");
        shadowRenderer.wear(material);
        // PCSS reads the depths themselves, a hardware comparison would hide them.
        setShadowCompareMode(CompareMode.Software);
        setShadowIntensity(1f);
        setShadowZExtend(DISTANCE);
        setShadowZFadeLength(FADE);
        material.setBoolean("Once", once);
        if (picture.softShadows()) {
            material.setFloat("SunTangent", FastMath.tan(SUN_DEGREES * 0.5f * FastMath.DEG_TO_RAD));
        }
    }

    /** Whether this GPU can draw these shadows: what it cannot is drawn as it was, by the fork's own filter. */
    public static boolean supported(EnumSet<Caps> caps) {
        return caps.contains(Caps.GLSL150);
    }

    /** The sun that lights the scene, where the light these are drawn from is another. */
    public void sun(Vector3f direction) {
        shadowRenderer.sun(direction);
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        super.initFilter(manager, renderManager, vp, w, h);
        SceneBuffers.on(manager, renderManager, vp, w, h).ifPresent(buffers -> {
            material.setTexture("Surface", buffers.normalRoughness());
            if (contact) {
                material.setTexture("SolidDepth", buffers.depth());
            }
        });
    }
}
