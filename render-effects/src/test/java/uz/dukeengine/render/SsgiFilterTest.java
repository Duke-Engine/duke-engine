package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** Which pixel of a 2x2 block the half-resolution rays start from: every one of them in time, and not the same at every pixel. */
class SsgiFilterTest {

    @Test
    void aHalfPixelVisitsEachOfItsBlocksFourPixelsInFourFrames() {
        for (int x = 0; x < 6; x++) {
            for (int y = 0; y < 6; y++) {
                var seen = new HashSet<Integer>();
                for (int frame = 20; frame < 24; frame++) {
                    var at = SsgiFilter.source(x, y, frame);
                    assertTrue(at[0] / 2 == x && at[1] / 2 == y, "inside the block of " + x + "," + y + ": " + at[0] + "," + at[1]);
                    seen.add(at[1] % 2 * 2 + at[0] % 2);
                }
                assertEquals(4, seen.size(), "all four of " + x + "," + y);
            }
        }
    }

    @Test
    void neighboursStartFromDifferentCornersOfTheirBlocks() {
        var here = SsgiFilter.source(4, 4, 0);
        var next = SsgiFilter.source(5, 4, 0);
        var above = SsgiFilter.source(4, 5, 0);
        assertNotEquals(here[0] % 2 * 2 + here[1] % 2, next[0] % 2 * 2 + next[1] % 2);
        assertNotEquals(here[0] % 2 * 2 + here[1] % 2, above[0] % 2 * 2 + above[1] % 2);
    }

    @Test
    void theFrameTurnsBackToItsStartAtTheSizeOfTheCycleAndNotMore() {
        // The shader's frame counter wraps at 64, a multiple of 4: the pattern is the same from the wrap on.
        assertEquals(SsgiFilter.source(3, 2, 1)[0], SsgiFilter.source(3, 2, 65)[0]);
        assertEquals(SsgiFilter.source(3, 2, 1)[1], SsgiFilter.source(3, 2, 65)[1]);
    }
}
