package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

/** Things drawn between the simulation's thirty steps a second. */
class MotionSmoothingTest {

    private static final float STEP = 1f / 30f;

    @Test
    void aThingIsDrawnOnItsWayBetweenTwoStepsInsteadOfStandingThenJumping() {
        var smoothing = new MotionSmoothing();
        // Drawn at 120 a second; the simulation moves it a unit a step, along x.
        smoothing.at(7, 0, new Vector3f(0f, 0f, 0f), 0f);
        float last = 0f;
        for (int frame = 1; frame <= 8; frame++) {
            float now = frame / 120f;
            int step = frame / 4; // a step every four frames
            float x = smoothing.at(7, step, new Vector3f(step, 0f, 0f), now).x;
            assertTrue(x >= last, "never back: " + x + " after " + last);
            assertTrue(x - last < 0.5f, "never a jump of a whole step: " + (x - last));
            last = x;
        }
        // The step before the last has been reached; the last is not drawn yet but on its way.
        assertTrue(last > 0.4f && last <= 2f, "a step behind the simulation, never ahead: " + last);
    }

    @Test
    void aStepNeverDrawsAThingAheadOfWhereTheSimulationHasPutIt() {
        var smoothing = new MotionSmoothing();
        smoothing.at(1, 0, new Vector3f(), 0f);
        smoothing.at(1, 1, new Vector3f(1f, 0f, 0f), STEP);
        assertEquals(1f, smoothing.at(1, 1, new Vector3f(1f, 0f, 0f), 10f).x, 1e-5f, "long after, it is where it is put");
    }

    @Test
    void aJumpIsDrawnAtOnce() {
        var smoothing = new MotionSmoothing();
        smoothing.at(1, 0, new Vector3f(), 0f);
        var blinked = new Vector3f(100f, 0f, 0f);
        assertEquals(100f, smoothing.at(1, 1, blinked, STEP).x, 1e-5f, "a blink is not a run");
    }

    /**
     * Steps that arrive unevenly — Windows sleeps in 15.6 ms ticks, so a 30-a-second simulation hands its steps over
     * a sixtieth and then a twentieth apart — still draw a walk at an even pace on a 144 Hz screen: never standing
     * still a frame between two steps, never lurching to twice its pace.
     */
    @Test
    void stepsArrivingUnevenlyStillDrawAnEvenWalk() {
        var smoothing = new MotionSmoothing();
        float arrives = 0f;
        int step = 0;
        float last = smoothing.at(1, 0, new Vector3f(), 0f).x;
        float least = Float.MAX_VALUE;
        float most = 0f;
        for (int drawn = 1; drawn <= 144 * 3; drawn++) {
            float now = drawn / 144f;
            while (now >= arrives + (step % 2 == 0 ? 1f / 60f : 1f / 20f)) {
                arrives += step % 2 == 0 ? 1f / 60f : 1f / 20f;
                step++;
            }
            float x = smoothing.at(1, step, new Vector3f(step, 0f, 0f), now).x;
            if (drawn > 144) { // its first second to settle
                least = Math.min(least, x - last);
                most = Math.max(most, x - last);
            }
            last = x;
        }
        assertTrue(least > 0f, "never standing still between steps: " + least);
        assertTrue(most < least * 3f, "never lurching: " + least + " to " + most);
    }

    @Test
    void aFacingTurnsOnItsWayBetweenStepsTheShortWayRound() {
        var smoothing = new MotionSmoothing();
        smoothing.at(1, 0, new Vector3f(), 0f);
        assertEquals(3f, smoothing.facing(1, 3f), 1e-5f);
        smoothing.at(1, 1, new Vector3f(), STEP);
        smoothing.at(1, 2, new Vector3f(), 2 * STEP);
        smoothing.at(1, 2, new Vector3f(), 2.5f * STEP);
        float half = smoothing.facing(1, -3f); // from 3 to -3 the short way: across pi, not through 0
        assertTrue(Math.abs(half) > 3f, "the short way round: " + half);
        smoothing.at(1, 2, new Vector3f(), 10f);
        assertEquals(-3f, smoothing.facing(1, -3f), 1e-4f, "long after, it faces where it is turned");
    }

    @Test
    void aGoneThingStartsAgainWhereItReturns() {
        var smoothing = new MotionSmoothing();
        smoothing.at(1, 0, new Vector3f(), 0f);
        smoothing.forget(1);
        assertEquals(5f, smoothing.at(1, 9, new Vector3f(5f, 0f, 0f), 1f).x, 1e-5f);
    }
}
