package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/**
 * A route is a way to get there, not a list of cells to visit.
 *
 * <p>A grid search can only answer in cells, and walking their centres turns
 * open ground into a staircase. What comes back has to be the line the mover
 * would have taken if it could see.
 */
class PathSmoothingTest {

    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;

    private static Coord3D at(float x, float y) {
        return new Coord3D(x, y, 0f);
    }

    /** Distance from a point to the segment a→b, for asking how straight a route is. */
    private static float distanceToSegment(Coord3D point, Coord3D a, Coord3D b) {
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        float lengthSquared = dx * dx + dy * dy;
        float t = lengthSquared == 0 ? 0
                : Math.clamp(((point.x() - a.x()) * dx + (point.y() - a.y()) * dy) / lengthSquared, 0f, 1f);
        float ox = a.x() + dx * t - point.x();
        float oy = a.y() + dy * t - point.y();
        return (float) Math.sqrt(ox * ox + oy * oy);
    }

    @Test
    void openGroundIsWalkedInAStraightLine() {
        var grid = new PathGrid(40, 40);
        var from = at(55f, 55f);
        var to = at(345f, 215f);

        var path = Pathfinder.findPath(grid, from, to);

        assertEquals(1, path.size(),
                "nothing is in the way, so there is nothing to say but where to go");
        assertEquals(to, path.get(0));
    }

    @Test
    void aWallIsRoundedInAsFewTurnsAsItTakes() {
        var grid = new PathGrid(40, 40);
        for (int cy = 0; cy <= 30; cy++) {
            grid.setBlocked(20, cy, true); // a wall from the top, with a gap below it
        }
        var from = at(55f, 55f);
        var to = at(345f, 55f);

        var path = Pathfinder.findPath(grid, from, to);

        assertFalse(path.isEmpty(), "there is a way round");
        assertTrue(path.size() <= 4,
                "a wall with one gap is two or three turns, not a waypoint per cell; got "
                        + path.size());

        // Every leg has to be walkable in a straight line, or the straightening
        // has cut a corner it should not have.
        var previous = from;
        for (var waypoint : path.getWaypoints()) {
            assertTrue(isWalkableLine(grid, previous, waypoint, 0f),
                    "leg " + previous + " -> " + waypoint + " passes through stone");
            previous = waypoint;
        }
    }

    @Test
    void aWideBodyIsKeptOffTheWalls() {
        var grid = new PathGrid(40, 12);
        for (int cx = 0; cx < 40; cx++) {
            grid.setBlocked(cx, 0, true);
            grid.setBlocked(cx, 11, true);
        }
        var from = at(25f, 55f);
        var to = at(375f, 55f);

        var narrow = Pathfinder.findPath(grid, from, to, 0f);
        var wide = Pathfinder.findPath(grid, from, to, 12f); // wider than a cell

        assertFalse(wide.isEmpty(), "the corridor is nine cells across; a wide body fits");
        for (var waypoint : wide.getWaypoints()) {
            assertTrue(isWalkableLine(grid, from, waypoint, 12f)
                            || distanceToSegment(waypoint, from, to) < CELL,
                    "a wide route must not hug the wall at " + waypoint);
        }
        assertEquals(1, narrow.size(), "and a point still just walks the straight line");
    }

    @Test
    void aBodyTooWideForTheGapStillGetsARouteRatherThanNone() {
        var grid = new PathGrid(20, 20);
        for (int cy = 0; cy < 20; cy++) {
            grid.setBlocked(10, cy, true);
        }
        grid.setBlocked(10, 10, false); // a single-cell doorway

        var path = Pathfinder.findPath(grid, at(55f, 105f), at(155f, 105f), 20f);

        assertFalse(path.isEmpty(),
                "squeezing through is worse than refusing to move, but only just");
    }

    @Test
    void nowhereToGoIsStillNowhereToGo() {
        var grid = new PathGrid(20, 20);
        for (int cy = 0; cy < 20; cy++) {
            grid.setBlocked(10, cy, true); // a wall with no way through at all
        }

        assertTrue(Pathfinder.findPath(grid, at(55f, 105f), at(155f, 105f), 4f).isEmpty());
    }

    /** Whether a body of {@code clearance} could walk the straight line a→b. */
    private static boolean isWalkableLine(PathGrid grid, Coord3D a, Coord3D b, float clearance) {
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        int samples = Math.max(1, (int) Math.ceil(Math.sqrt(dx * dx + dy * dy) / (CELL * 0.2f)));
        for (int i = 0; i <= samples; i++) {
            float t = (float) i / samples;
            float x = a.x() + dx * t;
            float y = a.y() + dy * t;
            for (int cy = (int) Math.floor((y - clearance) / CELL);
                    cy <= (int) Math.floor((y + clearance) / CELL); cy++) {
                for (int cx = (int) Math.floor((x - clearance) / CELL);
                        cx <= (int) Math.floor((x + clearance) / CELL); cx++) {
                    if (grid.isBlocked(cx, cy)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
