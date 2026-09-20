package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What the portrait plays, and how often it is worth redrawing.
 *
 * <p>The drawing itself needs a window; none of what decides it does. Which clip
 * a nearly-dead hero wears, whether a level he already had counts as going up
 * one, and how many frames a second twenty-four frames a second comes to are all
 * arithmetic — and every one of them is invisible from a chair until it is wrong
 * for a whole session.
 */
class PortraitMoodTest {

    /** A hero's block as the dungeon writes one, with a distinct clip per state. */
    private static final PortraitLook ARCHER = new PortraitLook(
            PortraitLook.Camera.DEFAULT,
            new PortraitLook.Clips("Ranged_Bow_Idle", "Ranged_Bow_Aiming_Idle",
                    "Idle_B", "Death_A", "Spawn_Ground"),
            30f, 1.5f);

    /** Every clip is a second long, except where a test says otherwise. */
    private static final PortraitMood.Lengths A_SECOND = clip -> 1f;

    private static PortraitMood mood() {
        return mood(ARCHER);
    }

    private static PortraitMood mood(PortraitLook look) {
        var mood = new PortraitMood(look, 24, A_SECOND);
        mood.nowShowing(7);
        return mood;
    }

    @Test
    void aQuietHeroStands() {
        var mood = mood();

        mood.sees(false, 1f, "1-daraja");

        assertEquals(PortraitLook.State.CALM, mood.state());
        assertEquals("Ranged_Bow_Idle", mood.clip());
        assertTrue(mood.loops(), "standing is a condition, not a moment");
        assertEquals(1f, mood.speed(), 0.001f);
    }

    @Test
    void heWhoHasSomethingToFightStandsReady() {
        var mood = mood();

        mood.sees(true, 1f, "1-daraja");

        assertEquals(PortraitLook.State.FIGHT, mood.state());
        assertEquals("Ranged_Bow_Aiming_Idle", mood.clip());
    }

    /**
     * The one priority worth stating. He is in a fight for most of the game and at
     * a tenth of his health for the few seconds when the player has to decide
     * whether to run — and that is the moment the frame is for.
     */
    @Test
    void beingNearlyDeadBeatsBeingInAFight() {
        var mood = mood();

        mood.sees(true, 0.2f, "1-daraja");

        assertEquals(PortraitLook.State.HURT, mood.state());
        assertEquals("Idle_B", mood.clip());
        // No tired clip in the kit: speed is what says "labouring" without one.
        assertEquals(1.5f, mood.speed(), 0.001f);
    }

    /** And the threshold is the file's, not a number in here. */
    @Test
    void howHurtCountsAsHurtIsTheFilesToSay() {
        var jumpy = mood(new PortraitLook(PortraitLook.Camera.DEFAULT, ARCHER.clips(), 80f, 1.5f));

        jumpy.sees(false, 0.6f, "1-daraja");

        assertEquals(PortraitLook.State.HURT, jumpy.state(),
                "60% is above 30 and below 80; the file said 80");
    }

    /**
     * Change what the file binds and the portrait plays something else.
     *
     * <p>The promise the whole arrangement is for: a clip is one word in a data
     * file, and no name of one is written in Java.
     */
    @Test
    void whateverTheFileBindsIsWhatIsPlayed() {
        var rebound = mood(new PortraitLook(PortraitLook.Camera.DEFAULT,
                new PortraitLook.Clips("Idle_A", "Melee_2H_Idle", "Idle_B", "Death_B", "Throw"),
                30f, 1.2f));

        rebound.sees(true, 1f, "1-daraja");

        assertEquals("Melee_2H_Idle", rebound.clip(),
                "the fighting clip should be the one the file named");
    }

    /**
     * Dying takes the frame and does not give it back.
     *
     * <p>A death takes the creature out of the snapshot, which takes it out of the
     * selection, which empties the card — all on the frame he falls. A portrait
     * that followed the card would never draw one frame of the death it was given
     * a clip for.
     */
    @Test
    void dyingTakesTheFrameAndKeepsIt() {
        var mood = mood();
        mood.sees(true, 0.4f, "3-daraja");

        mood.died();

        assertEquals(PortraitLook.State.DEAD, mood.state());
        assertEquals("Death_A", mood.clip());
        assertFalse(mood.loops(), "a death happens once");
        assertTrue(mood.holding(), "the frame is still his although the card has moved on");
        // And nothing the world says afterwards takes it off him.
        mood.sees(false, 1f, "9-daraja");
        assertEquals(PortraitLook.State.DEAD, mood.state());
    }

    /** Once he has fallen the frame keeps the pose and stops asking for frames. */
    @Test
    void aFallenHeroIsNotRedrawnForEver() {
        var mood = mood();
        mood.sees(false, 0.1f, "3-daraja");
        mood.died();

        float spent = 0f;
        for (int tick = 0; tick < 120; tick++) {
            spent += mood.advance(1f / 60f);
        }

        // A second of death clip, and then nothing at all for the second after it.
        assertTrue(spent > 0.9f && spent < 1.3f,
                "he should be drawn for his death and then left where he fell, not " + spent);
    }

    /** Going up a level is a flourish, and a flourish ends. */
    @Test
    void aNewLevelIsAFlourishThatEnds() {
        var mood = mood();
        mood.sees(false, 1f, "6-daraja");

        mood.sees(false, 1f, "7-daraja");

        assertEquals(PortraitLook.State.LEVEL_UP, mood.state());
        assertEquals("Spawn_Ground", mood.clip());
        assertFalse(mood.loops());

        for (int tick = 0; tick < 70; tick++) {
            mood.advance(1f / 60f);
        }
        assertEquals(PortraitLook.State.CALM, mood.state(),
                "a second of flourish, then back to whatever is true");
    }

    /**
     * The first level seen is not a level-up.
     *
     * <p>It is the player clicking on him. Without the guard, every click on the
     * hero is a fanfare — the same trap the sounds pay for, with the same guard.
     */
    @Test
    void theFirstLevelSeenIsNotALevelUp() {
        var mood = mood();

        mood.sees(false, 1f, "7-daraja");

        assertEquals(PortraitLook.State.CALM, mood.state());
    }

    /** And nor is clicking off him onto something else and back. */
    @Test
    void clickingOntoAnotherCreatureAndBackIsNotALevelUp() {
        var mood = mood();
        mood.sees(false, 1f, "7-daraja");

        mood.nowShowing(31); // a skeleton, which has no level at all
        mood.sees(false, 1f, "");
        mood.nowShowing(7);
        mood.sees(false, 1f, "7-daraja");

        assertEquals(PortraitLook.State.CALM, mood.state());
    }

    /**
     * The rate is a ceiling.
     *
     * <p>Twenty-four asked for, at sixty ticks a second, comes to twenty — a frame
     * every third tick — and never to more than was asked for. The number that
     * matters is the second one: the whole point of the rate is what it does not
     * spend.
     */
    @Test
    void theRateIsACeiling() {
        var mood = mood();
        mood.sees(false, 1f, "1-daraja");

        int drawn = 0;
        for (int tick = 0; tick < 60; tick++) {
            if (mood.advance(1f / 60f) > 0f) {
                drawn++;
            }
        }

        assertTrue(drawn <= 24, "asked for 24 a second and drew " + drawn);
        assertTrue(drawn >= 18, "asked for 24 a second and drew only " + drawn);
    }

    /** And the frames it does draw carry the time the skipped ones were owed. */
    @Test
    void aDrawnFrameCarriesTheTimeTheSkippedOnesWereOwed() {
        var mood = mood();
        mood.sees(false, 1f, "1-daraja");
        mood.advance(1f / 60f); // the first is always drawn, so the clock starts here

        float spent = 0f;
        for (int tick = 0; tick < 60; tick++) {
            spent += mood.advance(1f / 60f);
        }

        assertEquals(1f, spent, 0.05f,
                "a second of ticks has to be a second of animation, however few frames drew it");
    }

    /**
     * A long stall is not spent in one step.
     *
     * <p>A loading screen or an alt-tab is seconds in which nothing is drawn, and
     * the owed time goes on adding up. Spent at once it jumps the animation by the
     * whole stall, which looks like the model teleporting into another pose.
     */
    @Test
    void aStallIsNotSpentInOneJump() {
        var mood = mood();
        mood.sees(false, 1f, "1-daraja");

        assertTrue(mood.advance(9f) <= 0.25f, "nine seconds must not arrive as nine seconds");
    }
}
