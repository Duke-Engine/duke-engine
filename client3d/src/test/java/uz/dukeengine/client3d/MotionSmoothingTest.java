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
        assertTrue(last > 0.5f && last <= 2f, "a step behind the simulation, never ahead: " + last);
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

    @Test
    void aGoneThingStartsAgainWhereItReturns() {
        var smoothing = new MotionSmoothing();
        smoothing.at(1, 0, new Vector3f(), 0f);
        smoothing.forget(1);
        assertEquals(5f, smoothing.at(1, 9, new Vector3f(5f, 0f, 0f), 1f).x, 1e-5f);
    }
}
