package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TimingTest {

    @Test
    void aMarkIsCrossedOnceWhateverTheFrameLength() {
        for (float step : new float[] {0.001f, 0.016f, 0.1f, 0.35f}) {
            int crossed = 0;
            for (float t = 0f; t < 3f; t += step) {
                crossed += Timing.crossed(t, t + step, 1.2f) ? 1 : 0;
            }
            assertEquals(1, crossed, "frames of " + step);
        }
    }

    @Test
    void theStartCountsAStandingClockNeverAndBackwardsNot() {
        assertTrue(Timing.crossed(-1f, 0f, 0f), "a clock starting at 0 reaches a mark at 0");
        assertFalse(Timing.crossed(0.5f, 0.5f, 0.5f), "standing at it");
        assertFalse(Timing.crossed(0.8f, 0.2f, 0.5f), "wound back");
    }
}
