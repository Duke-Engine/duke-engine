package uz.dukeengine.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A named place, read as a map writes it: a rectangle or a circle in cells. */
class MapZoneTest {

    @Test
    void aRectangleAndACircleAsWritten() {
        var court = MapZone.of("Saroy 10 20 4 3");
        assertTrue(court.holds(105f, 205f, 10f));
        assertFalse(court.holds(145f, 205f, 10f), "past its fourth column");
        assertEquals(120f, court.middleX(10f));
        assertEquals(12, court.cells().size());
        assertEquals("Saroy 10 20 4 3", court.written());

        var well = MapZone.of("Quduq 5 5 2");
        assertTrue(well.holds(55f, 75f, 10f), "two cells from its middle");
        assertFalse(well.holds(55f, 85f, 10f));
        assertEquals(5, well.cells().getFirst()[0], "its middle first");
        assertEquals("Quduq 5 5 2", well.written());
    }
}
