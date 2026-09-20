package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

class PathfinderTest {

    private static Coord3D at(float x, float y) {
        return new Coord3D(x, y, 0f);
    }

    @Test
    void findsStraightPathOnEmptyGrid() {
        var grid = new PathGrid(10, 10); // 100x100 world units
        var path = Pathfinder.findPath(grid, at(5, 5), at(95, 5));

        assertFalse(path.isEmpty());
        // Destination is the caller's exact target.
        assertEquals(at(95, 5), path.getDestination());
        // Path advances toward the goal (first waypoint is east of the start).
        assertTrue(path.get(0).x() > 5f);
    }

    @Test
    void samentCellReturnsDirectWaypoint() {
        var grid = new PathGrid(10, 10);
        var path = Pathfinder.findPath(grid, at(2, 2), at(7, 7)); // both in cell (0,0)
        assertEquals(1, path.size());
        assertEquals(at(7, 7), path.getDestination());
    }

    @Test
    void routesAroundAWall() {
        var grid = new PathGrid(10, 10);
        // Vertical wall at column x=5 from y=0..8, leaving a gap at the top (y=9).
        for (int cy = 0; cy <= 8; cy++) {
            grid.setBlocked(5, cy, true);
        }

        var path = Pathfinder.findPath(grid, at(15, 15), at(85, 15));
        assertFalse(path.isEmpty(), "a detour through the gap should exist");

        // No waypoint may sit in a blocked cell.
        for (var wp : path.getWaypoints()) {
            assertFalse(grid.isBlocked(grid.toCellX(wp), grid.toCellY(wp)),
                    "waypoint landed in a blocked cell: " + wp);
        }
        assertEquals(at(85, 15), path.getDestination());
    }

    @Test
    void noPathThroughFullWall() {
        var grid = new PathGrid(10, 10);
        for (int cy = 0; cy < 10; cy++) {
            grid.setBlocked(5, cy, true); // wall with no gap
        }
        var path = Pathfinder.findPath(grid, at(15, 15), at(85, 15));
        assertTrue(path.isEmpty());
    }

    @Test
    void blockedGoalYieldsNoPath() {
        var grid = new PathGrid(10, 10);
        grid.setBlocked(9, 0, true);
        var path = Pathfinder.findPath(grid, at(5, 5), at(95, 5));
        assertTrue(path.isEmpty());
    }

    @Test
    void searchIsDeterministic() {
        var grid = new PathGrid(20, 20);
        for (int cy = 2; cy <= 15; cy++) {
            grid.setBlocked(10, cy, true);
        }
        var a = Pathfinder.findPath(grid, at(15, 15), at(185, 15));
        var b = Pathfinder.findPath(grid, at(15, 15), at(185, 15));
        assertEquals(a.getWaypoints(), b.getWaypoints());
    }
}
