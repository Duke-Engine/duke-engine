package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** That the chain of levels is a sound one at any window size, the odd and the tiny too. */
class MipBloomFilterTest {

    @Test
    void fullHdGetsSixLevelsFromHalfThePicture() {
        var levels = MipBloomFilter.levels(1920, 1080);
        assertEquals(6, levels.size());
        assertEquals(960, levels.get(0)[0]);
        assertEquals(540, levels.get(0)[1]);
    }

    @Test
    void everyLevelIsAPixelAtLeastAndNeverLargerThanTheOneBefore() {
        for (var size : new int[][] {{1, 1}, {2, 2}, {3, 5}, {7, 4000}, {853, 479}, {1366, 767}, {16, 16}}) {
            var levels = MipBloomFilter.levels(size[0], size[1]);
            assertTrue(levels.size() >= 1 && levels.size() <= 6, size[0] + "x" + size[1]);
            int[] before = size;
            for (var level : levels) {
                assertTrue(level[0] >= 1 && level[1] >= 1);
                assertTrue(level[0] <= before[0] && level[1] <= before[1]);
                before = level;
            }
        }
    }

    @Test
    void aTinyWindowGetsOneLevel() {
        assertEquals(1, MipBloomFilter.levels(1, 1).size());
        assertEquals(1, MipBloomFilter.levels(6, 6).size());
    }
}
