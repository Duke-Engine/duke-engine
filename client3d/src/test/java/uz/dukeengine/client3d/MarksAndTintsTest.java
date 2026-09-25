package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.view.UnitView;

/** A mark by a thing's health bar and a colour added to it, each while it holds its words. */
class MarksAndTintsTest {

    private static final List<String> CROSS = List.of("c0", "c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8");
    private static final List<String> CROSS_B = List.of("b0", "b1");

    private static final Visuals.UnitVisual RANGER = Visuals.create().unit("Ranger", look -> look
            .mark(Set.of("ENTHUSIASTIC"), CROSS, 100, true, 0.25f, 0.5f)
            .mark(Set.of("ENTHUSIASTIC", "SUBLIMINAL"), CROSS_B, 100, true, 0.25f, 0.5f)
            .tint(Set.of("FRENZY_TWO"), 0f, -0.7f, -0.7f, 30)).of("Ranger");

    @Test
    void theStripPlaysBackAndForthAFrameEachTenthOfASecond() {
        var mark = RANGER.marks.getFirst();

        assertEquals("c0", mark.frameAt(0f));
        assertEquals("c8", mark.frameAt(0.85f), "out to the last");
        assertEquals("c7", mark.frameAt(0.95f), "and back");
        assertEquals("c1", mark.frameAt(1.55f));
        assertEquals("c0", mark.frameAt(1.65f), "and round again");
    }

    @Test
    void theMarkComesWithItsWordAndSubliminalTakesItsPlace() {
        assertEquals(List.of(), RANGER.marksFor(Set.of()), "no word, no mark");
        assertEquals(CROSS, RANGER.marks.get(RANGER.marksFor(Set.of("ENTHUSIASTIC")).getFirst()).frames());
        assertEquals(List.of(1), RANGER.marksFor(Set.of("ENTHUSIASTIC", "SUBLIMINAL")),
                "the Subliminal one alone with both held");
    }

    @Test
    void aMarkIsDrawnJustBelowTheBarAQuarterAlongIt() {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var camera = new Camera(1600, 900);
        camera.setFrustumPerspective(45f, 1600f / 900f, 1f, 2000f);
        camera.setLocation(new Vector3f(0f, 200f, 200f));
        camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        camera.update();
        var bars = new UnitBars(assets, font, font);
        bars.look(new UnitBarLook(
                List.of(new UnitBarLook.Step(100, 10), new UnitBarLook.Step(500, 25), new UnitBarLook.Step(0, 100)),
                30, 1400, 80f, 220f, 13f, 6f, 2f, 1.4f, 26f, 2f, 4f, 3f,
                0xA8322B, 0x8FC4AE, 0x3E6FA8, 0x16130F, 0x0A0806, 0x16130F, 0x8FC4AE, 0xE8A33D, 0xD9CFBA,
                11f, 15f, 10f, 12f));
        var view = new UnitView(1, "Ranger", 0, 0f, 0f, 0f, 50f, 100f, false, true, false, false, -1);

        bars.update(camera, List.of(new UnitBars.Standing(view, 4f, 0f, true,
                new UnitBars.Badge("Common/Textures/dot.png", 0.25f, 1f))), UnitBarReading.read(""));
        var bar = (Node) bars.node().getChild(0);
        var badge = named(bar, "badge");
        var trough = named(bar, "trough");
        assertTrue(badge.getCullHint() != Spatial.CullHint.Always, "shown with its words");
        float middle = badge.getLocalTranslation().x + badge.getLocalScale().x / 2f;
        assertEquals(trough.getLocalTranslation().x + trough.getLocalScale().x * 0.25f, middle, 1e-3f,
                "a quarter along the bar");
        assertTrue(badge.getLocalTranslation().y + badge.getLocalScale().y < trough.getLocalTranslation().y,
                "below it");

        bars.update(camera, List.of(new UnitBars.Standing(view, 4f, 0f, true)), UnitBarReading.read(""));
        assertEquals(Spatial.CullHint.Always, named(bar, "badge").getCullHint(), "and gone with them");
    }

    @Test
    void aFrenziedSoldierReachesHisTintWithinThirtyFramesAndLosesItThirtyAfter() {
        var tints = new WordTints();
        var root = new Node("soldier");
        for (int frame = 0; frame < 15; frame++) {
            tints.see(4, root, RANGER, Set.of("FRENZY_TWO"), 1f);
        }
        assertEquals(-0.35f, tints.tintOf(4).g, 1e-4f, "halfway there at fifteen");
        for (int frame = 15; frame < 30; frame++) {
            tints.see(4, root, RANGER, Set.of("FRENZY_TWO"), 1f);
        }
        var full = tints.tintOf(4);
        assertEquals(0f, full.r, 1e-4f);
        assertEquals(-0.7f, full.g, 1e-4f);
        assertEquals(-0.7f, full.b, 1e-4f, "all of it by thirty");
        assertEquals(1, root.getLocalLightList().size(), "laid over the soldier alone");

        for (int frame = 0; frame < 30; frame++) {
            tints.see(4, root, RANGER, Set.of(), 1f);
        }
        assertNull(tints.tintOf(4), "his own colour again thirty frames after the word went");
        assertEquals(0, root.getLocalLightList().size());
    }

    private static Geometry named(Node bar, String name) {
        for (var child : bar.getChildren()) {
            if (child instanceof Geometry piece && name.equals(piece.getName())) {
                return piece;
            }
        }
        return org.junit.jupiter.api.Assertions.fail("no piece called " + name);
    }
}
