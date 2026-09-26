package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import org.junit.jupiter.api.Test;

/** The sun's shadows, as the reference's volumes cast them ({@code W3DVolumetricShadowManager}). */
class SunShadowsTest {

    private static final DesktopAssetManager ASSETS = new DesktopAssetManager(true);

    /** A sun {@code degrees} high, shining along +x. */
    private static Vector3f sunAt(float degrees) {
        float up = degrees * FastMath.DEG_TO_RAD;
        return new Vector3f(FastMath.cos(up), -FastMath.sin(up), 0f);
    }

    /** How far past a thing {@code height} high its shadow reaches on flat ground, cast along {@code direction}. */
    private static float reach(Vector3f direction, float height) {
        return height * FastMath.sqrt(direction.x * direction.x + direction.z * direction.z) / -direction.y;
    }

    private static Node box(boolean withGlass) {
        var body = new Node("body");
        var hull = new Geometry("hull", new Box(5f, 5f, 5f));
        hull.setMaterial(new Material(ASSETS, "Common/MatDefs/Misc/Unshaded.j3md"));
        body.attachChild(hull);
        if (withGlass) {
            var glass = new Geometry("glass", new Box(1f, 1f, 1f));
            glass.setMaterial(new Material(ASSETS, "Common/MatDefs/Misc/Unshaded.j3md"));
            glass.getMaterial().getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
            body.attachChild(glass);
        }
        return body;
    }

    @Test
    void aTenHighBoxUnderASun45HighCastsTenPastItInTheMapsColour() {
        var sun = sunAt(45f);
        assertEquals(10f, reach(SunShadows.raised(sun, 0f), 10f), 1e-4f, "10 past it, away from the sun");
        var shadows = new SunShadows(ASSETS, new ViewPort("world", new Camera(800, 600)), sun);
        shadows.cast(box(false), true, 0f);
        var colour = (ColorRGBA) shadows.groupFor(0f).getShadowMaterial().getParam("ShadowColor").getValue();
        assertEquals(0.627f, colour.r, 0.001f, "what lies under it drawn at 0.63 of its lit value, under A0A0A0");
        assertEquals(colour.r, colour.b);
    }

    @Test
    void aLeastSunHeightOf89UnderASun20HighKeepsTheShadowInItsFootprint() {
        var raised = SunShadows.raised(sunAt(20f), 89f);
        assertTrue(reach(raised, 10f) < 5f, "within its footprint: " + reach(raised, 10f));
        assertTrue(raised.x > 0f, "on the sun's own bearing");
        assertEquals(reach(sunAt(60f), 10f), reach(SunShadows.raised(sunAt(60f), 45f), 10f), 1e-4f,
                "a sun above it: as it stands");
    }

    @Test
    void aCasterCastsFromItsOpaquePiecesAndEverythingReceives() {
        var view = new ViewPort("world", new Camera(800, 600));
        var shadows = new SunShadows(ASSETS, view, sunAt(45f));
        var caster = box(true);
        var standing = box(false);
        shadows.cast(standing, false, 0f);
        assertTrue(view.getProcessors().isEmpty(), "nothing cast: nothing drawn for it");
        shadows.cast(caster, true, 0f);

        assertEquals(RenderQueue.ShadowMode.CastAndReceive, caster.getChild("hull").getShadowMode());
        assertEquals(RenderQueue.ShadowMode.Receive, caster.getChild("glass").getShadowMode(),
                "a see-through piece casts none");
        assertEquals(RenderQueue.ShadowMode.Receive, standing.getChild("hull").getShadowMode(),
                "one that does not cast is darkened where the shadow falls on it");
        assertEquals(1, view.getProcessors().size());
    }

    @Test
    void shadowsTurnedOffAreNoneAnywhereAndTurnedOnAreBack() {
        var shadows = new SunShadows(ASSETS, new ViewPort("world", new Camera(800, 600)), sunAt(45f));
        shadows.cast(box(false), true, 0f);
        shadows.cast(box(false), true, 45f);
        shadows.on(false);
        assertFalse(shadows.groupFor(0f).isEnabled());
        assertFalse(shadows.groupFor(45f).isEnabled());
        shadows.on(true);
        assertTrue(shadows.groupFor(0f).isEnabled() && shadows.groupFor(45f).isEnabled());
    }
}
