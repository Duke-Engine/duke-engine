package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
import uz.dukeengine.game.view.UnitView;

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
            0x16130F, 0x8FC4AE, 0xE8A33D, 0xD9CFBA,
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
        return unit(id, template, x, y, health, maxHealth, 0);
    }

    /** The same, for somebody else's creature — whose bar is filled differently. */
    private static UnitView unit(int id, String template, float x, float y, float health,
            float maxHealth, int player) {
        return new UnitView(id, template, player, x, y, 0f, health, maxHealth,
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

    /**
     * One named piece of a bar.
     *
     * <p>By name rather than by the order it was made in. Counting was how this
     * was written first, and it broke silently the moment a keyline was added in
     * front of the trough: every index shifted by one, and the test that meant to
     * ask about the experience ring was asking about the mana bar.
     */
    private static Geometry named(Node bar, String name) {
        for (var child : bar.getChildren()) {
            if (child instanceof Geometry piece && name.equals(piece.getName())) {
                return piece;
            }
        }
        return org.junit.jupiter.api.Assertions.fail("a bar has no piece called " + name);
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

    private static boolean showsManaBar(Node bar) {
        return named(bar, "manaFill").getLocalCullHint() != Spatial.CullHint.Always;
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
        assertSame(named(showing.get(0), "ticks").getMesh(), named(showing.get(1), "ticks").getMesh(),
                "two of a size should be drawn from one mesh");
        assertNotSame(named(showing.get(0), "ticks").getMesh(), named(showing.get(2), "ticks").getMesh());
    }

    /**
     * Every flat rectangle on every bar is the same mesh, and every gauge is
     * one of three.
     *
     * <p>Two claims, because there are two kinds of rectangle now. The troughs,
     * the keylines and the marks are flat and share ONE square between every
     * creature on the floor. A gauge is not flat — it runs bright along its top
     * edge and falls away dark at the foot, which is what makes it read as a
     * thing rather than as a region of screen that happens to be red — and the
     * shading lives in the corners, so there is one square per fill COLOUR:
     * his, theirs, and mana. Forty creatures share three.
     */
    @Test
    void everyFlatRectangleIsOneMeshAndEveryGaugeIsOneOfThree() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(
                unit(1, "Rogue", 0f, 0f, 128f, 200f),
                unit(2, "Skeleton", 10f, 0f, 30f, 30f, 1),
                unit(9, "Warden", -10f, 0f, 400f, 600f, 1)), UnitBarReading.read(LINE));

        var showing = up(screen.bars());
        var flat = named(showing.get(0), "trough").getMesh();
        for (var bar : showing) {
            for (var name : new String[] {"edge", "trough", "manaEdge", "manaTrough"}) {
                assertSame(flat, named(bar, name).getMesh(),
                        name + " should be drawn from the one flat square");
            }
        }

        var hisGauge = named(showing.get(0), "fill").getMesh();
        assertNotSame(flat, hisGauge, "a gauge is shaded, so it is not the flat square");
        assertNotSame(hisGauge, named(showing.get(0), "manaFill").getMesh(),
                "mana is its own colour, so its gauge is its own square");
        assertSame(named(showing.get(1), "fill").getMesh(),
                named(showing.get(2), "fill").getMesh(),
                "two of somebody else's creatures share one gauge");
        assertNotSame(hisGauge, named(showing.get(1), "fill").getMesh(),
                "and his is not theirs");
    }

    /**
     * A gauge is lit from above: bright at its top edge, dark at its foot.
     *
     * <p>The whole difference between the design and what the game looked like.
     * A flat bar is not a duller version of this — it is a different kind of
     * picture, a region of screen that happens to be red rather than a thing
     * with a light on it. Checked in the mesh because that is where the light
     * lives: three rows of vertices, and the top row brighter than the bottom in
     * every channel.
     */
    @Test
    void aGaugeIsLitFromAbove() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(unit(1, "Rogue", 0f, 0f, 128f, 200f)),
                UnitBarReading.read(LINE));

        var mesh = named(up(screen.bars()).get(0), "fill").getMesh();
        var colours = mesh.getFloatBuffer(com.jme3.scene.VertexBuffer.Type.Color);
        assertNotNull(colours, "a gauge carries its light in its vertex colours");
        assertEquals(3 * 2 * 4, colours.limit(), "three stops, two corners apiece");
        for (int channel = 0; channel < 3; channel++) {
            float top = colours.get(channel);
            float foot = colours.get(2 * 2 * 4 + channel);
            assertTrue(top > foot,
                    "channel " + channel + ": the top of a gauge (" + top
                            + ") should be brighter than its foot (" + foot + ")");
        }
        // And the flat pieces carry none, or they would be shaded too.
        assertNull(named(up(screen.bars()).get(0), "trough").getMesh()
                .getFloatBuffer(com.jme3.scene.VertexBuffer.Type.Color));
    }

    /**
     * The level sits in the middle of its disc, and the disc in the middle of
     * the bars.
     *
     * <p>★ NEITHER DID. A BitmapText hangs downward from where it is put, so
     * asking for a middle and handing over a top drops it by half its height —
     * and the level number slid out through the bottom of the circle. The disc
     * itself was centred on the health bar rather than on the BLOCK of bars, so
     * on anything with mana it sat visibly high of the thing it belongs to.
     *
     * <p>Both are arithmetic that reads exactly like centring, which is why
     * this is measured rather than looked at.
     */
    @Test
    void theLevelSitsInItsDiscAndTheDiscInTheMiddleOfTheBars() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(
                unit(1, "Rogue", 0f, 0f, 128f, 200f),
                unit(2, "Skeleton", 10f, 0f, 30f, 30f, 1)), UnitBarReading.read(LINE));

        for (var bar : up(screen.bars())) {
            float disc = named(bar, "back").getLocalTranslation().y;
            var level = lettering(bar, "3", "7");
            float middle = level.getLocalTranslation().y - level.getLineHeight() / 2f;
            assertEquals(disc, middle, 1f, "the level should sit in the middle of its disc");

            // And the disc in the middle of whatever bars this creature wears.
            var health = named(bar, "edge");
            var mana = named(bar, "manaEdge");
            float top = health.getLocalTranslation().y + health.getLocalScale().y;
            float foot = mana.getLocalCullHint() == Spatial.CullHint.Always
                    ? health.getLocalTranslation().y
                    : mana.getLocalTranslation().y;
            assertEquals((top + foot) / 2f, disc, 1f,
                    "the disc should sit in the middle of the bars beside it");
        }
    }

    /** The showing line of lettering that says one of these words. */
    private static BitmapText lettering(Node bar, String... words) {
        for (var child : bar.getChildren()) {
            if (child instanceof BitmapText line
                    && child.getLocalCullHint() != Spatial.CullHint.Always
                    && List.of(words).contains(line.getText())) {
                return line;
            }
        }
        return org.junit.jupiter.api.Assertions.fail(
                "no lettering on this bar says any of " + List.of(words));
    }

    /** The experience ring is built once per step and then only pointed at. */
    @Test
    void theRingIsAMeshPerStepAndNotPerFrame() {
        var screen = screen();
        var standing = all(unit(1, "Rogue", 0f, 0f, 128f, 200f));

        screen.bars().update(screen.camera(), standing, UnitBarReading.read(LINE));
        var first = named(up(screen.bars()).get(0), "arc").getMesh();
        screen.bars().update(screen.camera(), standing, UnitBarReading.read(LINE));

        assertSame(first, named(up(screen.bars()).get(0), "arc").getMesh(),
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
        assertTrue(named(showing.get(0), "arc").getLocalCullHint() != Spatial.CullHint.Always);
        assertEquals(Spatial.CullHint.Always, named(showing.get(1), "arc").getLocalCullHint(),
                "the boss earns nothing, so its disc has no arc on it");
    }

    /**
     * Every piece a bar is made of is drawn where the depth buffer cannot reach
     * it.
     *
     * <p>★ THE FAULT THIS EXISTS FOR WAS INVISIBLE TO EVERY OTHER TEST. The
     * pieces are stacked by giving each its own z, which is how the interface
     * layer sorts them — and z is also what the depth buffer tests. With the test
     * left on, the first piece drawn wrote its depth and everything behind it in
     * the stack failed: the medallion, its rim and its ring never reached the
     * screen. The geometry was built, placed, and measurably the right size, so
     * nothing here or anywhere else had anything to say about it. It was found by
     * running the game and looking.
     *
     * <p>And when the depth test was taken off, the medallion was STILL missing.
     * A flat shape has a side it is seen from, decided by the order its corners
     * are given in: the square every bar is made of is wound one way and the fan
     * of wedges the disc is made of the other, so the disc, its rim and its ring
     * were being thrown away as back-facing. Two faults with one symptom, and
     * the first fix looked like it had failed.
     */
    @Test
    void nothingIsHiddenByTheDepthBufferOrByItsOwnWinding() {
        var screen = screen();

        screen.bars().update(screen.camera(), all(unit(1, "Rogue", 0f, 0f, 128f, 200f)),
                UnitBarReading.read(LINE));

        var bar = up(screen.bars()).get(0);
        for (var name : new String[] {"edge", "trough", "fill", "ticks", "manaEdge",
            "manaTrough", "manaFill", "back", "rim", "arc"}) {
            var state = named(bar, name).getMaterial().getAdditionalRenderState();
            assertFalse(state.isDepthTest(), name + " would be hidden by whatever it is over");
            assertFalse(state.isDepthWrite(), name + " would hide the piece in front of it");
            assertEquals(com.jme3.material.RenderState.FaceCullMode.Off, state.getFaceCullMode(),
                    name + " has a back, and a flat shape wound the wrong way round has"
                            + " nothing else to show");
        }
    }
}
