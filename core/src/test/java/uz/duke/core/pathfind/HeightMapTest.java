package uz.duke.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;

/** SAGE's height map in whole numbers: its triangles, its cliffs, and the ground a mover stands on. */
class HeightMapTest {

    /** One cell: corners p0 (0,0), p1 (1,0), p2 (1,1), p3 (0,1), as SAGE names them. */
    private static HeightMap cell(int p0, int p1, int p2, int p3) {
        return new HeightMap(2, 2, new int[] {p0, p1, p3, p2});
    }

    @Test
    void aSlopeIsTheSameWhicheverHalfOfTheCellItIsReadIn() {
        var plane = cell(0, 16, 32, 16);
        for (int fx = 0; fx < HeightMap.SUBCELL; fx += 17) {
            for (int fy = 0; fy < HeightMap.SUBCELL; fy += 13) {
                assertEquals(16 * fx + 16 * fy, plane.fixedAt(0, 0, fx, fy), "at " + fx + ", " + fy);
            }
        }
    }

    @Test
    void aSaddleIsSplitAsSageSplitsIt() {
        var saddle = cell(0, 10, 0, 10);
        assertEquals(0, saddle.fixedAt(0, 0, 128, 128), "the diagonal joins the two low corners");
        // SAGE, in floats: 10 + (50/256)(0-10) + (1-200/256)(0-10) = 5.859375 steps.
        assertEquals((int) (5.859375 * 256), saddle.fixedAt(0, 0, 200, 50));
        // And across the diagonal, the other triangle: 10 + (1-200/256)(0-10) + (50/256)(0-10).
        assertEquals((int) (5.859375 * 256), saddle.fixedAt(0, 0, 50, 200));
    }

    @Test
    void aCellIsACliffFromSixteenStepsBetweenItsCorners() {
        assertFalse(cell(0, 15, 15, 0).isCliff(0, 0), "9.375 is under SAGE's 9.8");
        assertTrue(cell(0, 16, 16, 0).isCliff(0, 0), "10 is over it");
        assertTrue(cell(3, 3, 3, 19).isCliff(0, 0), "one corner is enough");
    }

    @Test
    void aReliefIsReadAndWrittenAsRowsOfCorners() {
        var written = List.of("0 1 2", "-3 4 5");
        var relief = HeightMap.parse(written);
        assertEquals(3, relief.columns());
        assertEquals(-3, relief.at(0, 1));
        assertEquals(written, relief.written());
        assertEquals(5, relief.at(9, 9), "off the relief reads as its nearest corner");
        assertThrows(IllegalArgumentException.class, () -> HeightMap.parse(List.of("0 1", "0 1 2")));
        assertThrows(IllegalArgumentException.class, () -> HeightMap.parse(List.of("0 x", "0 1")));
    }

    @Test
    void theGroundRisesWithTheReliefAndACliffIsNotSteppedOnto() {
        var grid = new PathGrid(3, 1);
        assertEquals(0f, grid.groundHeight(new Coord3D(5f, 5f, 0f)));
        // Corners along x: 0 0 8 24, the same on both rows: a gentle rise, then a cliff.
        grid.setRelief(HeightMap.parse(List.of("0 0 8 24", "0 0 8 24")));
        assertEquals(0f, grid.groundHeight(0, 0), "flat where its corners are");
        assertEquals(4 * 10f / 16, grid.groundHeight(1, 0), "halfway up a cell rising 8 steps");
        assertEquals(2 * 10f / 16, grid.groundHeight(new Coord3D(12.5f, 2.5f, 0f)), "a quarter of the way up it");
        assertTrue(grid.canStep(0, 0, 1, 0));
        assertFalse(grid.canStep(1, 0, 2, 0), "16 steps across one cell is a cliff");
        assertTrue(grid.canStep(2, 0, 1, 0), "and whatever stands on one may still come down");
    }

    @Test
    void aReliefIsTheSizeOfTheCornersOfItsGrid() {
        var grid = new PathGrid(3, 1);
        assertThrows(IllegalArgumentException.class, () -> grid.setRelief(HeightMap.parse(List.of("0 0 0", "0 0 0"))));
    }
}
