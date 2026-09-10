package uz.duke.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;

/**
 * Ground that is not all at one height, and the only way between one floor and
 * the next.
 *
 * <p>The rule is small enough to state in a line — same level, or one level apart
 * with a ramp at the step — and the whole of height rests on it holding in three
 * separate places: the search that finds a route, the straightening that throws
 * away most of it afterwards, and the step a body actually takes. The second is
 * the one that hides: a line drawn from one floor to another is perfectly clear
 * cell by cell, because every cell along it is open ground.
 */
class LevelsTest {

    /**
     * Two floors either side of a line, joined at exactly one cell.
     *
     * <p>The left half stands at 0, the right at 1, and the only way across is the
     * ramp at cell (5,3) — which is three rows down from where anything walking
     * between the two ends would like to go, so any route that ignores height has
     * a much shorter line available to it.
     */
    private static final String MAP = """
            ############
            #0000011111#
            #0000011111#
            #0000/11111#
            #0000011111#
            ############
            """;

    private static PathGrid grid() {
        var grid = MapLoader.fromText(MAP);
        MapLoader.levels(grid, MAP);
        return grid;
    }

    private static Coord3D at(int cx, int cy) {
        return new Coord3D((cx + 0.5f) * PathGrid.DEFAULT_CELL_SIZE,
                (cy + 0.5f) * PathGrid.DEFAULT_CELL_SIZE, 0f);
    }

    // ---- the rule itself ----

    @Test
    void theSameFloorIsAlwaysAStepAway() {
        var grid = grid();

        assertTrue(grid.canStep(1, 1, 2, 1), "two open cells at the same level");
        assertTrue(grid.canStep(1, 1, 2, 2), "diagonally, too");
        assertFalse(grid.canStep(1, 1, 0, 1), "but never into stone");
    }

    @Test
    void aChangeOfFloorNeedsARamp() {
        var grid = grid();

        assertFalse(grid.canStep(5, 1, 6, 1), "level 0 to level 1 with nothing joining them");
        assertTrue(grid.canStep(5, 3, 6, 3), "the ramp is what joins them");
        assertTrue(grid.canStep(6, 3, 5, 3), "and it is joined in both directions");
    }

    @Test
    void aRampJoinsOneFloorAndNotTwo() {
        var grid = grid();
        grid.setLevel(6, 3, 2); // the far side is now two floors up

        assertFalse(grid.canStep(5, 3, 6, 3),
                "a ramp climbs one floor; two is a cliff with a step at the bottom");
    }

    @Test
    void aFloorIsNotChangedDiagonally() {
        var grid = grid();
        grid.setRamp(5, 2, true); // a ramp beside the boundary, reachable diagonally

        assertFalse(grid.canStep(5, 2, 6, 3),
                "climbing across a corner puts a body halfway up a wall for a step");
        assertFalse(grid.canStep(5, 2, 6, 1), "in either diagonal direction");
    }

    /** A grid nobody tells about height behaves exactly as it did before height. */
    @Test
    void aFlatGridHasNoRulesToObey() {
        var flat = new PathGrid(10, 10);

        assertEquals(0, flat.level(4, 4));
        assertFalse(flat.isRamp(4, 4));
        assertEquals(0f, flat.groundHeight(4, 4), 0f);
        assertEquals(0f, flat.cellCenter(4, 4).z(), 0f, "a flat cell's centre is on the ground");
        assertTrue(flat.canStep(4, 4, 5, 5), "every open neighbour is a step away");
    }

    @Test
    void heightIsWhatTheMapSaysAndTheLevelIsWhole() {
        var grid = grid();
        grid.setLevelHeight(6f);

        assertEquals(0f, grid.groundHeight(1, 1), 0f);
        assertEquals(6f, grid.groundHeight(7, 1), 0f);
        assertEquals(6f, grid.cellCenter(7, 1).z(), 0f, "a raised cell's centre stands on it");
        assertEquals(0f, grid.groundHeight(5, 3), 0f, "a ramp stands at the foot of what it climbs");
    }

    // ---- reading one from text ----

    @Test
    void anUnknownCharacterInALevelMapIsAnError() {
        var grid = new PathGrid(4, 2);

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> MapLoader.levels(grid, """
                        0000
                        00?0
                        """));
        assertTrue(thrown.getMessage().contains("?"), "it should say what it could not read");
        assertTrue(thrown.getMessage().contains("2,1"), "and where: " + thrown.getMessage());
    }

    @Test
    void aMapWrittenBeforeHeightExistedReadsAsFlat() {
        var map = """
                ......
                ..##..
                ......
                """;
        var grid = MapLoader.fromText(map);
        MapLoader.levels(grid, map);

        for (int cy = 0; cy < 3; cy++) {
            for (int cx = 0; cx < 6; cx++) {
                assertEquals(0, grid.level(cx, cy), "cell " + cx + "," + cy);
            }
        }
    }

    // ---- routes ----

    @Test
    void aRouteBetweenFloorsGoesRoundToTheRamp() {
        var grid = grid();

        var path = Pathfinder.findPath(grid, at(1, 1), at(10, 1));

        assertFalse(path.isEmpty(), "there is a way across, and it should have been found");
        assertTrue(walksThrough(grid, path, at(1, 1), 5, 3),
                "the only way between the floors is the ramp, so the route must use it");
    }

    /**
     * The straightening is not allowed to undo what the search obeyed.
     *
     * <p>This is the test that would have caught it. A route round to the ramp and
     * back is mostly open floor, so straightening will happily replace it with a
     * line — and that line crosses the boundary where there is no stair, over
     * ground that is open the whole way.
     */
    @Test
    void theStraightenedRouteNeverCrossesWhereThereIsNoStair() {
        var grid = grid();

        var path = Pathfinder.findPath(grid, at(1, 1), at(10, 1));

        assertEveryStepIsLegal(grid, path, at(1, 1));
    }

    @Test
    void aBodyWideEnoughToNeedRoomStillUsesTheStair() {
        var grid = grid();

        var path = Pathfinder.findPath(grid, at(1, 1), at(10, 4), 4f);

        assertFalse(path.isEmpty());
        assertEveryStepIsLegal(grid, path, at(1, 1));
    }

    /** Take the stair away and the two floors are two worlds. */
    @Test
    void withoutARampThereIsNoWayUpAtAll() {
        var grid = grid();
        grid.setRamp(5, 3, false);

        var path = Pathfinder.findPath(grid, at(1, 1), at(10, 1));

        assertTrue(path.isEmpty(),
                "open ground on both sides is not a route; the floors are a wall apart");
    }

    /** And a route on one floor is not affected by the other one existing. */
    @Test
    void walkingAboutOnOneFloorIsUntouched() {
        var grid = grid();

        var path = Pathfinder.findPath(grid, at(1, 1), at(4, 4));

        assertFalse(path.isEmpty());
        assertEquals(1, path.getWaypoints().size(),
                "open floor should still straighten to a single leg");
    }

    // ---- helpers ----

    /**
     * Walk the finished route the way a body would and check every cell boundary
     * it crosses is one it was allowed to cross.
     */
    private static void assertEveryStepIsLegal(PathGrid grid, Path path, Coord3D from) {
        var at = from;
        for (var waypoint : path.getWaypoints()) {
            int lastX = grid.toCellX(at);
            int lastY = grid.toCellY(at);
            int steps = (int) Math.ceil(at.distance(waypoint) / (grid.getCellSize() * 0.2f));
            for (int i = 1; i <= steps; i++) {
                float t = (float) i / steps;
                var point = new Coord3D(at.x() + (waypoint.x() - at.x()) * t,
                        at.y() + (waypoint.y() - at.y()) * t, 0f);
                int cx = grid.toCellX(point);
                int cy = grid.toCellY(point);
                if (cx != lastX || cy != lastY) {
                    assertTrue(grid.canStep(lastX, lastY, cx, cy),
                            "the route crosses from " + lastX + "," + lastY + " (level "
                                    + grid.level(lastX, lastY) + ") to " + cx + "," + cy
                                    + " (level " + grid.level(cx, cy) + ") with no stair");
                    lastX = cx;
                    lastY = cy;
                }
            }
            at = waypoint;
        }
    }

    private static boolean walksThrough(PathGrid grid, Path path, Coord3D from, int cx, int cy) {
        var at = from;
        for (var waypoint : path.getWaypoints()) {
            int steps = (int) Math.ceil(at.distance(waypoint) / (grid.getCellSize() * 0.2f));
            for (int i = 0; i <= steps; i++) {
                float t = steps == 0 ? 1f : (float) i / steps;
                var point = new Coord3D(at.x() + (waypoint.x() - at.x()) * t,
                        at.y() + (waypoint.y() - at.y()) * t, 0f);
                if (grid.toCellX(point) == cx && grid.toCellY(point) == cy) {
                    return true;
                }
            }
            at = waypoint;
        }
        return false;
    }
}
