package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** How far the eye moves in a frame: the same over any frame rate, and faster the shorter it takes. */
class AcesToneMapTest {

    @Test
    void twoHalfFramesGoAsFarAsOneWholeFrame() {
        float half = AcesToneMap.rate(1f / 120f, 0.5f);
        assertEquals(AcesToneMap.rate(1f / 60f, 0.5f), 1f - (1f - half) * (1f - half), 1e-6f);
    }

    @Test
    void coversMostOfTheWayInTheTimeItTakes() {
        assertEquals(0.632f, AcesToneMap.rate(2f, 2f), 1e-3f);
        assertEquals(0f, AcesToneMap.rate(0f, 2f));
    }

    @Test
    void theShorterTimeMovesFurtherInAFrame() {
        assertTrue(AcesToneMap.rate(0.016f, 0.5f) > AcesToneMap.rate(0.016f, 2f));
    }
}
