package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.asset.DesktopAssetManager;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import org.junit.jupiter.api.Test;

/**
 * The numbers say the right thing, in the right colour, and stop.
 *
 * <p>Headless, with a real camera rather than a stand-in: the whole point of this
 * class is that a number belongs to a creature standing somewhere in the world
 * and has to be put where that creature is <em>on screen</em>, which is
 * arithmetic the camera owns.
 */
class FloatingNumbersTest {

    private static final float NOW = 100f;
    private static final HitNumbers LOOK = HitNumbers.DEFAULTS;

    private record Screen(FloatingNumbers numbers, Node gui, Camera camera) {
    }

    private static Screen screen() {
        var assets = new DesktopAssetManager(true);
        var font = assets.loadFont("Interface/Fonts/Default.fnt");
        var gui = new Node("gui");
        var camera = new Camera(1600, 900);
        camera.setFrustumPerspective(45f, 1600f / 900f, 1f, 2000f);
        camera.setLocation(new com.jme3.math.Vector3f(0f, 200f, 200f));
        camera.lookAt(com.jme3.math.Vector3f.ZERO, com.jme3.math.Vector3f.UNIT_Y);
        camera.update();
        return new Screen(new FloatingNumbers(font, gui, LOOK), gui, camera);
    }

    private static HealthWatch.Change hit(float amount, boolean healed, boolean his) {
        return new HealthWatch.Change(1, 0f, 0f, amount, healed, his);
    }

    private static BitmapText firstUp(Node gui) {
        for (var child : ((Node) gui.getChild(0)).getChildren()) {
            if (child instanceof BitmapText text
                    && text.getLocalCullHint() != Spatial.CullHint.Always) {
                return text;
            }
        }
        return null;
    }

    /** Damage is a bare number, healing carries its plus — the genre's own shorthand. */
    @Test
    void damageIsBareAndHealingIsSigned() {
        var screen = screen();

        screen.numbers().add(hit(34f, false, false), NOW, 14f);
        screen.numbers().update(NOW, screen.camera(), (x, y) -> 0f);
        assertEquals("34", firstUp(screen.gui()).getText());

        screen.numbers().clear();
        screen.numbers().add(hit(40f, true, true), NOW, 14f);
        screen.numbers().update(NOW, screen.camera(), (x, y) -> 0f);
        assertEquals("+40", firstUp(screen.gui()).getText());
    }

    /** Whole numbers, and never a zero: something landed, so something is shown. */
    @Test
    void itIsAlwaysAWholeNumberAndNeverNothing() {
        var screen = screen();

        screen.numbers().add(hit(0.4f, false, false), NOW, 14f);
        screen.numbers().update(NOW, screen.camera(), (x, y) -> 0f);

        assertEquals("1", firstUp(screen.gui()).getText(),
                "a blow the player was told about cannot read as nothing");
    }

    /**
     * What he is taking and what he is dealing are not the same colour.
     *
     * <p>The one distinction worth making on screen: damage to his own is the
     * number he has to act on, and damage to everything else is the number he is
     * pleased about.
     */
    @Test
    void takingAndDealingAndHealingAreThreeColours() {
        var dealing = screen();
        dealing.numbers().add(hit(20f, false, false), NOW, 14f);
        dealing.numbers().update(NOW, dealing.camera(), (x, y) -> 0f);
        var taking = screen();
        taking.numbers().add(hit(20f, false, true), NOW, 14f);
        taking.numbers().update(NOW, taking.camera(), (x, y) -> 0f);
        var healing = screen();
        healing.numbers().add(hit(20f, true, true), NOW, 14f);
        healing.numbers().update(NOW, healing.camera(), (x, y) -> 0f);

        var dealt = firstUp(dealing.gui()).getColor();
        var taken = firstUp(taking.gui()).getColor();
        var healed = firstUp(healing.gui()).getColor();
        assertNotEquals(dealt, taken, "what he is taking has to stand out from what he deals");
        assertNotEquals(taken, healed);
        assertTrue(taken.r > taken.g, "and what he is taking is red");
        assertTrue(healed.g > healed.r, "and healing is green");
    }

    /**
     * It punches out large, settles to its own size, and then it is gone.
     *
     * <p>Not a drift and not a fade, which is what it was. Something moving across
     * the screen has to be followed to be read and something translucent has to be
     * read off whatever is behind it -- both cost the player the fraction of a
     * second the number was drawn for.
     */
    @Test
    void itPunchesOutLargeAndSettles() {
        var screen = screen();
        screen.numbers().add(hit(34f, false, false), NOW, 14f);

        screen.numbers().update(NOW, screen.camera(), (x, y) -> 0f);
        float big = firstUp(screen.gui()).getLocalScale().x;
        float solid = firstUp(screen.gui()).getColor().a;

        screen.numbers().update(NOW + LOOK.seconds() * 0.9f, screen.camera(), (x, y) -> 0f);
        float settled = firstUp(screen.gui()).getLocalScale().x;

        assertEquals(LOOK.popScale(), big, 0.001f, "it should appear at its full size");
        assertTrue(settled < big * 0.6f, "and shrink hard, but went " + big + " to " + settled);
        assertEquals(1f, solid, 0.001f, "and never be see-through");
        assertEquals(1f, firstUp(screen.gui()).getColor().a, 0.001f);

        screen.numbers().update(NOW + LOOK.seconds() * 1.1f, screen.camera(), (x, y) -> 0f);
        assertEquals(null, firstUp(screen.gui()), "and then it is simply gone");
    }

    /**
     * Several on one creature burst out of it in different directions.
     *
     * <p>Two blows in one instant are two numbers a player can read; two numbers
     * in the same place are one number he cannot. The first goes straight up,
     * where his eye already is, and the rest open out either side of it.
     */
    @Test
    void severalOnOneCreatureFanOutFromIt() {
        var screen = screen();
        for (int blow = 0; blow < 3; blow++) {
            screen.numbers().add(hit(10f + blow, false, false), NOW, 14f);
        }

        screen.numbers().update(NOW + LOOK.seconds() * 0.9f, screen.camera(), (x, y) -> 0f);

        var all = ((Node) screen.gui().getChild(0)).getChildren();
        assertEquals(3, all.size(), "three blows are three numbers");
        var places = all.stream().map(Spatial::getLocalTranslation).toList();
        for (int one = 0; one < places.size(); one++) {
            for (int other = one + 1; other < places.size(); other++) {
                assertTrue(places.get(one).distance(places.get(other)) > 8f,
                        "two of them are on top of each other and read as one");
            }
        }
    }

    /** And numbers on different creatures do not fan: each starts straight up. */
    @Test
    void aNumberOnItsOwnCreatureGoesStraightUp() {
        var screen = screen();
        screen.numbers().add(new HealthWatch.Change(1, 0f, 0f, 12f, false, false), NOW, 14f);
        screen.numbers().add(new HealthWatch.Change(2, 0f, 0f, 12f, false, false), NOW, 14f);

        screen.numbers().update(NOW + LOOK.seconds() * 0.9f, screen.camera(), (x, y) -> 0f);

        var both = ((Node) screen.gui().getChild(0)).getChildren();
        assertEquals(both.get(0).getLocalTranslation().x, both.get(1).getLocalTranslation().x,
                0.5f, "they are on different creatures; neither has to get out of the way");
    }

    /** A creature that walks takes its number with it. */
    @Test
    void theNumberIsPutWhereItsCreatureIsOnScreen() {
        var screen = screen();
        screen.numbers().add(new HealthWatch.Change(1, -80f, 0f, 12f, false, false), NOW, 14f);
        screen.numbers().add(new HealthWatch.Change(2, 80f, 0f, 12f, false, false), NOW, 14f);

        screen.numbers().update(NOW, screen.camera(), (x, y) -> 0f);

        var both = ((Node) screen.gui().getChild(0)).getChildren();
        assertTrue(both.get(0).getLocalTranslation().x < both.get(1).getLocalTranslation().x,
                "one to the west of the other in the world should be to its left on screen");
    }

    /**
     * The pool stops growing.
     *
     * <p>A busy fight throws a dozen of these a second, and a fresh
     * {@code BitmapText} for each is a fresh mesh for each.
     */
    @Test
    void theSceneStopsGrowingOnceItHasSeenTheBusiestMoment() {
        var screen = screen();
        screen.numbers().add(hit(10f, false, false), NOW, 14f);
        screen.numbers().add(hit(10f, false, false), NOW, 14f);
        screen.numbers().update(NOW, screen.camera(), (x, y) -> 0f);
        assertEquals(2, screen.numbers().madeSoFar());

        for (int blow = 0; blow < 200; blow++) {
            float when = NOW + 10f + blow;
            screen.numbers().update(when, screen.camera(), (x, y) -> 0f);
            screen.numbers().add(hit(blow + 1, false, false), when, 14f);
        }

        assertEquals(2, screen.numbers().madeSoFar(),
                "the pool built something new for a blow it had a number for");
    }

    /** A new world takes them all down. */
    @Test
    void aNewWorldShowsNothing() {
        var screen = screen();
        screen.numbers().add(hit(34f, false, false), NOW, 14f);
        screen.numbers().update(NOW, screen.camera(), (x, y) -> 0f);
        assertEquals(1, screen.numbers().showing());

        screen.numbers().clear();

        assertEquals(0, screen.numbers().showing());
        assertEquals(null, firstUp(screen.gui()));
    }

    /** The colour really does come from the look rather than from anywhere else. */
    @Test
    void theColourIsTheOneTheFileNamed() {
        var screen = screen();
        screen.numbers().add(hit(20f, true, false), NOW, 14f);

        screen.numbers().update(NOW, screen.camera(), (x, y) -> 0f);

        var wanted = Glow.colour(LOOK.healColour(), LOOK.brightness(), 1f);
        assertEquals(wanted, firstUp(screen.gui()).getColor());
        assertEquals(new ColorRGBA(wanted.r, wanted.g, wanted.b, 1f),
                firstUp(screen.gui()).getColor());
    }
}
