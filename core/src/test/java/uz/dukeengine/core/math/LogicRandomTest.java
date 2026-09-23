package uz.dukeengine.core.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** The simulation's random numbers: the same seed, the same numbers, and a state a save can carry. */
class LogicRandomTest {

    @Test
    void theSameSeedDrawsTheSameNumbers() {
        var one = new LogicRandom(7L);
        var other = new LogicRandom(7L);
        for (int draw = 0; draw < 100; draw++) {
            assertEquals(one.nextLong(), other.nextLong());
        }
        assertNotEquals(new LogicRandom(7L).nextLong(), new LogicRandom(8L).nextLong());
    }

    /** From the least to the most, both included, as a range written in data means. */
    @Test
    void aRangeIncludesBothEnds() {
        var random = new LogicRandom(1L);
        var seen = new TreeSet<Integer>();
        for (int draw = 0; draw < 500; draw++) {
            seen.add(random.nextInt(3, 5));
        }
        assertEquals(new TreeSet<>(java.util.List.of(3, 4, 5)), seen);
        int backwards = random.nextInt(5, 3);
        assertTrue(backwards >= 3 && backwards <= 5, "a range written backwards is read the right way round");
    }

    /** A range of one value decides nothing, so it draws nothing — the reference game does the same. */
    @Test
    void aRangeOfOneDrawsNothing() {
        var random = new LogicRandom(1L);
        long before = random.state();
        assertEquals(6, random.nextInt(6, 6));
        assertEquals(before, random.state());
    }

    /** A saved state carries on exactly where it stopped. */
    @Test
    void aRestoredStateCarriesOn() {
        var random = new LogicRandom(99L);
        random.nextLong();
        long saved = random.state();
        long next = random.nextLong();

        var loaded = new LogicRandom(0L);
        loaded.restore(saved);
        assertEquals(next, loaded.nextLong());
        float unit = new LogicRandom(3L).nextFloat();
        assertTrue(unit >= 0f && unit < 1f);
    }
}
