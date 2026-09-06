package uz.duke.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;

class MapLoaderTest {

    @Test
    void parsesDimensionsAndBlockedCells() {
        var grid = MapLoader.fromText("""
                .....
                ..#..
                .....
                """);
        assertEquals(5, grid.getWidth());
        assertEquals(3, grid.getHeight());
        assertTrue(grid.isBlocked(2, 1));
        assertFalse(grid.isBlocked(0, 0));
    }

    @Test
    void verticalWallForcesPathfinderToDetour() {
        // Wall in column 4 for the middle rows; top and bottom rows are open.
        var grid = MapLoader.fromText("""
                ..........
                ....#.....
                ....#.....
                ....#.....
                ..........
                """, 10f);

        // From cell (1,1) to cell (8,1): must detour around the wall.
        var path = Pathfinder.findPath(grid, new Coord3D(15f, 15f, 0f), new Coord3D(85f, 15f, 0f));
        assertFalse(path.isEmpty());
        for (var wp : path.getWaypoints()) {
            assertFalse(grid.isBlocked(grid.toCellX(wp), grid.toCellY(wp)),
                    "waypoint landed in a blocked cell: " + wp);
        }
    }

    @Test
    void shortLinesArePaddedClear() {
        var grid = MapLoader.fromText("""
                ####
                .
                """);
        assertEquals(4, grid.getWidth());
        assertFalse(grid.isBlocked(3, 1)); // padded cell is clear
    }
}
