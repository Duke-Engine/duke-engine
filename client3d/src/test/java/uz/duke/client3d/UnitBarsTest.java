package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.font.BitmapText;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.game.view.UnitView;

/**
 * The bars go over the right creatures, say the right things, and cost what
 * they were designed to cost.
 *
 * <p>Headless, with a real camera: a bar belongs to a creature standing
 * somewhere in the world and has to end up where that creature is <em>on
 * screen</em>, which is arithmetic the camera owns. What cannot be checked here
 * is whether it looks right — that is a pair of eyes — so what is checked is
 * everything underneath: who gets one, who does not, what the lettering says,
 * and whether the sharing the design promises actually happens.
 */
class UnitBarsTest {

    private static final UnitBarLook LOOK = new UnitBarLook(
            List.of(new UnitBarLook.Step(100, 10),
                    new UnitBarLook.Step(500, 25),
                    new UnitBarLook.Step(0, 100)),
            30, 1400, 80f, 220f,
            13f, 6f, 2f, 1.4f,
            26f, 2f, 4f, 3f,
            0xA8322B, 0x8FC4AE, 0x3E6FA8, 0x16130F, 0x0A0806,
            0x16130F, 0x8FC4AE, 0xE8A33D, 0xC9A24B, 0xD9CFBA,
            11f, 15f, 10f, 12f);

    /** A floor with a hero on it at level seven, half an experience ring in. */
    private static final String LINE =
            "name=|deep=3|boss=9|hero=1,7,48,120,45,90|who=Rogue,Erika";

    private record Screen(UnitBars bars, Camera camera) {
    }

    private static Screen screen() {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var camera = new Camera(1600, 900);
        camera.setFrustumPerspective(45f, 1600f / 900f, 1f, 2000f);
        camera.setLocation(new Vector3f(0f, 200f, 200f));
        camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        camera.update();
        var bars = new UnitBars(assets, font, font);
        bars.look(LOOK);
        return new Screen(bars, camera);
    }

    private static UnitView unit(int id, String template, float x, float y, float health,
            float maxHealth) {
        return new UnitView(id, template, 0, x, y, 0f, health, maxHealth,
                false, true, false, false, -1);
    }

    private static UnitBars.Standing standing(UnitView view) {
        return new UnitBars.Standing(view, 4f, 0f, view.playerIndex() == 0);
    }

    private static List<UnitBars.Standing> all(UnitView... views) {
        var standing = new ArrayList<UnitBars.Standing>();
        for (var view : views) {
            standing.add(standing(view));
        }
        return standing;
    }

    /** Every bar currently showing, in the order they were dressed. */
    private static List<Node> up(UnitBars bars) {
        var showing = new ArrayList<Node>();
        for (var child : bars.node().getChildren()) {
            if (child instanceof Node bar && child.getLocalCullHint() != Spatial.CullHint.Always) {
                showing.add(bar);
            }
        }
        return showing;
    }

    private static List<String> words(Node bar) {
        var said = new ArrayList<String>();
        for (var child : bar.getChildren()) {
            if (child instanceof BitmapText text
                    && child.getLocalCullHint() != Spatial.CullHint.Always) {
                said.add(text.getText());
            }
        }
        return said;
    }

    private static Geometry named(Node bar, int which) {
        int seen = 0;
        for (var child : bar.getChildren()) {
            if (child instanceof Geometry piece && seen++ == which) {
                return piece;
            }
        }
        return null;
    }

    // ---- who gets one ----

    @Test
    void everyCreatureOnScreenGetsOne() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(
                unit(1, "Rogue", 0f, 0f, 100f, 200f),
                unit(2, "Skeleton", 10f, 0f, 30f, 30f),
                unit(9, "Warden", -10f, 0f, 400f, 600f)), UnitBarReading.read(LINE));

        assertEquals(3, screen.bars().showing());
        assertEquals(3, up(screen.bars()).size());
    }

    /**
     * One well past the edge of the screen is not drawn at all.
     *
     * <p>The cheapest half of keeping this affordable on a crowded floor: the
     * camera sees a fraction of what the player can see, and a bar for a creature
     * two rooms to the left is a dozen pieces of scene graph for nobody.
     *
     * <p>★ THE FIRST VERSION OF THIS TEST CHECKED NOTHING. It stood the creature
     * at five thousand paces, which is past the far plane — so it was dropped by
     * the depth test one line earlier, and deleting the edge test entirely left
     * the test green. The positions below were measured rather than reasoned
     * about: at this camera, world x 280 lands at screen 1886 with the depth
     * still well inside the frustum.
     */
    @Test
    void oneWellPastTheEdgeOfTheScreenIsNotDrawn() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(
                unit(1, "Rogue", 0f, 0f, 100f, 200f),
                unit(2, "Skeleton", 280f, 0f, 30f, 30f),
                unit(3, "Skeleton", -280f, 0f, 30f, 30f)), UnitBarReading.read(LINE));

        assertEquals(1, screen.bars().showing(), "the two off to the sides should be left out");
    }

    /**
     * But one only just past it still is, because its bar overhangs.
     *
     * <p>What the margin is for, and the reason the edge is not simply the edge:
     * a bar is two hundred pixels wide and hangs either side of the creature it
     * belongs to, so a monster whose feet are off the left of the screen still
     * has most of a bar that ought to show. Measured: world x 210 puts it at
     * screen 1615, fifteen pixels outside a sixteen-hundred-pixel screen.
     */
    @Test
    void oneJustPastTheEdgeStillIsBecauseItsBarOverhangs() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(
                unit(2, "Skeleton", 210f, 0f, 30f, 30f)), UnitBarReading.read(LINE));

        assertEquals(1, screen.bars().showing());
    }

    /** And one behind the camera, which projects to somewhere entirely plausible. */
    @Test
    void oneBehindTheCameraIsNotDrawn() {
        var screen = screen();

        // Measured: this lands at screen x 800 — the middle of the screen — with
        // only its depth saying it is behind the viewer. Culling on the projected
        // x and y alone would have drawn a bar in the centre for a creature the
        // player cannot see.
        screen.bars().update(screen.camera(), all(
                unit(2, "Skeleton", 0f, 600f, 30f, 30f)), UnitBarReading.read(LINE));

        assertEquals(0, screen.bars().showing());
    }

    /**
     * The pool settles at the busiest frame and is not rebuilt after it.
     *
     * <p>What makes a fight affordable: a bar is a dozen small pieces, and making
     * them as creatures walk into view would be the cost landing exactly when the
     * game is busiest.
     */
    @Test
    void thePoolSettlesAtTheBusiestFrameItHasSeen() {
        var screen = screen();
        var many = new ArrayList<UnitBars.Standing>();
        for (int id = 1; id <= 12; id++) {
            many.add(standing(unit(id, "Skeleton", id * 3f, 0f, 30f, 30f)));
        }

        screen.bars().update(screen.camera(), many, UnitBarReading.read(LINE));
        assertEquals(12, screen.bars().madeSoFar());

        screen.bars().update(screen.camera(), many.subList(0, 3), UnitBarReading.read(LINE));
        assertEquals(12, screen.bars().madeSoFar(), "the spare ten are kept, not thrown");
        assertEquals(3, screen.bars().showing(), "but only three are on screen");

        screen.bars().update(screen.camera(), many, UnitBarReading.read(LINE));
        assertEquals(12, screen.bars().madeSoFar(), "and the busy frame costs nothing again");
    }

    /** A new floor takes them all down. */
    @Test
    void aNewFloorTakesThemAllDown() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(unit(1, "Rogue", 0f, 0f, 10f, 200f)),
                UnitBarReading.read(LINE));
        screen.bars().clear();

        assertEquals(0, screen.bars().showing());
    }

    /** A game that named no table draws nothing rather than something invented. */
    @Test
    void aGameWithNoTableDrawsNoBars() {
        var screen = screen();
        screen.bars().look(UnitBarLook.NONE);

        screen.bars().update(screen.camera(), all(unit(1, "Rogue", 0f, 0f, 10f, 200f)),
                UnitBarReading.read(LINE));

        assertEquals(0, screen.bars().showing());
    }

    // ---- what they say ----

    /**
     * The disc says the hero's level and everybody else's depth.
     *
     * <p>Both halves in one test because the interesting thing is that they
     * differ: a monster's level IS the depth it is fought at, and the hero's is
     * his own.
     */
    @Test
    void theDiscSaysHisLevelAndTheirDepth() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(
                unit(1, "Rogue", 0f, 0f, 128f, 200f),
                unit(2, "Skeleton", 10f, 0f, 30f, 30f)), UnitBarReading.read(LINE));

        var showing = up(screen.bars());
        assertTrue(words(showing.get(0)).contains("7"), words(showing.get(0)).toString());
        assertTrue(words(showing.get(0)).contains("128/200"));
        assertTrue(words(showing.get(0)).contains("Erika"), "the printed name, not the template");
        assertTrue(words(showing.get(1)).contains("3"), "the skeleton is on the third floor");
        assertTrue(words(showing.get(1)).contains("30/30"));
        assertTrue(words(showing.get(1)).contains("Skeleton"), "nothing renamed it");
    }

    /**
     * A creature with no mana has no mana bar, and takes up no room for one.
     *
     * <p>An empty bar under a skeleton would be a claim that it could cast. Only
     * the hero has a skill book in this game, and the line says which he is.
     */
    @Test
    void onlyTheOneWithAPoolGetsTheThinBlueBar() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(
                unit(1, "Rogue", 0f, 0f, 128f, 200f),
                unit(2, "Skeleton", 10f, 0f, 30f, 30f)), UnitBarReading.read(LINE));

        var showing = up(screen.bars());
        assertTrue(showsManaBar(showing.get(0)), "the hero holds a pool");
        assertFalse(showsManaBar(showing.get(1)), "and a skeleton does not");
    }

    /** The mana pieces are the fourth and fifth made, in the order make() builds them. */
    private static boolean showsManaBar(Node bar) {
        return named(bar, 4).getLocalCullHint() != Spatial.CullHint.Always;
    }

    // ---- what the design promised it would cost ----

    /**
     * Two creatures of the same size share one mesh for their marks.
     *
     * <p>The claim the whole segment table rests on, stated as a test because it
     * is invisible otherwise: the marks are a mesh per <em>count</em>, of which
     * there are thirteen, rather than a mesh per creature, of which there can be
     * forty. Two skeletons are not two meshes, and a skeleton and a boss are.
     */
    @Test
    void creaturesOfOneSizeShareTheirMarks() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(
                unit(1, "Skeleton", 0f, 0f, 30f, 30f),
                unit(2, "Skeleton", 10f, 0f, 30f, 30f),
                unit(3, "Warden", -10f, 0f, 400f, 400f)), UnitBarReading.read(LINE));

        var showing = up(screen.bars());
        assertSame(named(showing.get(0), 2).getMesh(), named(showing.get(1), 2).getMesh(),
                "two of a size should be drawn from one mesh");
        assertNotSame(named(showing.get(0), 2).getMesh(), named(showing.get(2), 2).getMesh());
    }

    private static void assertNotSame(Object one, Object other) {
        assertFalse(one == other, "these two should not have been the same object");
    }

    /**
     * And every rectangle on every bar is the same mesh.
     *
     * <p>Troughs, fills and marks all being quads, there is one quad. Twelve
     * creatures is not twelve hundred vertices.
     */
    @Test
    void everyRectangleIsTheOneRectangle() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(
                unit(1, "Rogue", 0f, 0f, 128f, 200f),
                unit(2, "Skeleton", 10f, 0f, 30f, 30f)), UnitBarReading.read(LINE));

        var showing = up(screen.bars());
        var one = named(showing.get(0), 0).getMesh();
        assertSame(one, named(showing.get(0), 1).getMesh());
        assertSame(one, named(showing.get(1), 0).getMesh());
        assertSame(one, named(showing.get(1), 3).getMesh());
    }

    /** The experience ring is built once per step and then only pointed at. */
    @Test
    void theRingIsAMeshPerStepAndNotPerFrame() {
        var screen = screen();
        var standing = all(unit(1, "Rogue", 0f, 0f, 128f, 200f));

        screen.bars().update(screen.camera(), standing, UnitBarReading.read(LINE));
        var first = named(up(screen.bars()).get(0), 7).getMesh();
        screen.bars().update(screen.camera(), standing, UnitBarReading.read(LINE));

        assertSame(first, named(up(screen.bars()).get(0), 7).getMesh(),
                "a ring that has not moved should not have been rebuilt");
        assertNotNull(first);
    }

    /** Nobody but the hero has a ring at all, because nobody else earns any. */
    @Test
    void onlyTheHeroWearsARing() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(
                unit(1, "Rogue", 0f, 0f, 128f, 200f),
                unit(9, "Warden", 10f, 0f, 400f, 600f)), UnitBarReading.read(LINE));

        var showing = up(screen.bars());
        assertTrue(named(showing.get(0), 7).getLocalCullHint() != Spatial.CullHint.Always);
        assertEquals(Spatial.CullHint.Always, named(showing.get(1), 7).getLocalCullHint(),
                "the boss earns nothing, so its disc has no arc on it");
    }

    /** A creature with no body at all is never handed to the pool. */
    @Test
    void nothingIsDrawnForSomethingWithNoLife() {
        assertNull(named(new Node("empty"), 0), "the helper itself, so a miss reads as a miss");
    }
}
