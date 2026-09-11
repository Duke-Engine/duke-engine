package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * How the flash moves, checked by arithmetic rather than by watching for it.
 *
 * <p>Everything that makes this effect read as Warcraft's rather than as three
 * shapes sliding is a property of one function, and all of it is the kind of
 * thing that is wrong by a little and looks wrong by a lot: a fade that starts
 * too early, an ease that is really a straight line, a turn that goes the other
 * way. Each of them gets a test, because each of them is a sentence somebody
 * could quietly stop being true.
 */
class OrderMarkTest {

    private static final OrderMark LOOK = OrderMark.DEFAULT;

    @Test
    void itStartsWideAndEndsOnTheSpotThatWasClicked() {
        assertEquals(LOOK.startRadius(), LOOK.at(0f).radius(), 0.001f,
                "it should begin at arm's length from the click");
        assertEquals(LOOK.endRadius(), LOOK.at(LOOK.seconds()).radius(), 0.001f,
                "and finish gathered on it");
    }

    /**
     * Fast at first and slowing as it lands.
     *
     * <p>The one that separates this from a slide. Halfway through its life it
     * should be well past halfway home — a constant speed would put it exactly
     * halfway, which is the thing that reads as machinery.
     */
    @Test
    void itClosesFastAndArrivesSlowly() {
        float span = LOOK.startRadius() - LOOK.endRadius();
        float halfway = LOOK.at(LOOK.seconds() * 0.5f).radius();
        float covered = (LOOK.startRadius() - halfway) / span;

        assertTrue(covered > 0.6f,
                "halfway through the flight it should be most of the way in, but had covered "
                        + Math.round(covered * 100) + "%");
    }

    /** And a power of 1 really is the straight line, so the knob means what it says. */
    @Test
    void anEaseOfOneIsAConstantSpeed() {
        var straight = new OrderMark(10f, 0f, 1f, 1f, 1f, 0f, 1f, 1f, 0f, 1f, 5f, 2, 0, 0);

        assertEquals(5f, straight.at(0.5f).radius(), 0.001f,
                "with no easing, half the time is half the distance");
    }

    /**
     * It travels at full strength and goes out at the end.
     *
     * <p>Not a fade from the first frame: a mark that starts going out as it
     * appears is half gone before the player has read it.
     */
    @Test
    void itDoesNotBeginToFadeUntilLateInItsLife() {
        assertEquals(1f, LOOK.at(0f).alpha(), 0.001f, "full when it appears");
        assertEquals(1f, LOOK.at(LOOK.seconds() * LOOK.fadeFrom() * 0.99f).alpha(), 0.001f,
                "and still full right up to the point it starts going out");
        assertTrue(LOOK.at(LOOK.seconds() * 0.9f).alpha() < 0.5f, "well gone by the end");
        assertEquals(0f, LOOK.at(LOOK.seconds()).alpha(), 0.001f, "and out when it arrives");
    }

    /** The set turns as it closes — a little, and all in one direction. */
    @Test
    void itTurnsAsItCloses() {
        float quarter = LOOK.at(LOOK.seconds() * 0.25f).spinRadians();
        float whole = LOOK.at(LOOK.seconds()).spinRadians();

        assertTrue(quarter > 0f, "it should have begun turning");
        assertTrue(whole > quarter, "and kept turning the same way");
        assertEquals(Math.toRadians(LOOK.spinDegrees()), whole, 0.001f,
                "ending exactly where the file said");
    }

    /**
     * The attack ring goes hard on and hard off, twice, and ends dark.
     *
     * <p>A blink rather than a fade, and the thing worth pinning is that it is
     * really OFF in the middle: a "blink" that only dims is a thing nobody sees
     * across a room, and it is the gap that the eye catches rather than the light.
     */
    @Test
    void theAttackRingBlinksTwiceAndGoesOut() {
        float life = LOOK.seconds();
        assertEquals(1f, LOOK.blinkAt(0f), 0.001f, "lit the moment it is ordered");
        assertEquals(0f, LOOK.blinkAt(life * 0.30f), 0.001f, "out");
        assertEquals(1f, LOOK.blinkAt(life * 0.55f), 0.001f, "and back");
        assertEquals(0f, LOOK.blinkAt(life * 0.80f), 0.001f, "and out again");
        assertEquals(0f, LOOK.blinkAt(life), 0.001f, "and finished dark");
        assertEquals(0f, LOOK.blinkAt(-1f), 0.001f, "and dark before it exists");
    }

    /** However many blinks the file asks for, it is a whole number of them. */
    @Test
    void theFileDecidesHowManyTimesItBlinks() {
        var once = new OrderMark(7f, 1f, 1f, 1f, 1f, 0f, 1f, 1f, 0f, 1f, 5f, 1, 0, 0);

        assertEquals(1f, once.blinkAt(0.2f), 0.001f);
        assertEquals(0f, once.blinkAt(0.7f), 0.001f, "one blink is one on and one off");
    }

    /** Asked about a moment before or after its life, it answers sensibly. */
    @Test
    void itIsWellBehavedOutsideItsOwnLife() {
        assertEquals(LOOK.startRadius(), LOOK.at(-5f).radius(), 0.001f);
        assertEquals(LOOK.endRadius(), LOOK.at(100f).radius(), 0.001f);
        assertEquals(0f, LOOK.at(100f).alpha(), 0.001f);
        assertFalse(LOOK.spent(0f), "brand new");
        assertTrue(LOOK.spent(LOOK.seconds()), "and finished on time");
    }

    /**
     * A file that asks for something impossible gets something drawable.
     *
     * <p>These are typed by hand into a data file and re-tuned by eye, so a zero
     * or a minus sign is a matter of time. None of them should be able to divide
     * by zero or hand the renderer a shape with no area.
     */
    @Test
    void nonsenseInTheFileIsPulledBackToSomethingDrawable() {
        var silly = new OrderMark(-4f, -1f, 0f, 0f, -2f, 0f, 0.1f, 5f, 0f, -1f, -3f, 0, 0, 0);

        assertTrue(silly.seconds() > 0f, "a mark with no life would divide by zero");
        assertTrue(silly.size() > 0f && silly.width() > 0f, "and one with no area draws nothing");
        assertTrue(silly.easePower() >= 1f, "an ease under 1 speeds up as it lands");
        assertTrue(silly.fadeFrom() < 1f, "and fading from the very end never fades at all");
        assertEquals(1f, silly.at(0f).alpha(), 0.001f);
        assertEquals(0f, silly.at(silly.seconds()).alpha(), 0.001f);
    }
}
