package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** A route kept to the road where the grid costs open ground dearer, and straight across where it does not (E52). */
class RoadCostTest {

    private static final int WIDE = 30;
    private static final int DEEP = 20;

    /** A road out of (2, 10) up to row 2, along it, and down into (27, 10): a U round the straight way. */
    private static boolean road(int cx, int cy) {
        return cx == 2 && cy >= 2 && cy <= 10 || cy == 2 && cx >= 2 && cx <= 27 || cx == 27 && cy >= 2 && cy <= 10;
    }

    private static final Coord3D FROM = new Coord3D(25f, 105f, 0f);
    private static final Coord3D TO = new Coord3D(275f, 105f, 0f);

    @Test
    void aRouteKeepsToTheRoadWhereOpenGroundCostsMore() {
        var grid = new PathGrid(WIDE, DEEP);
        var costs = new int[WIDE * DEEP];
        for (int cy = 0; cy < DEEP; cy++) {
            for (int cx = 0; cx < WIDE; cx++) {
                costs[cy * WIDE + cx] = road(cx, cy) ? 100 : 300;
            }
        }
        grid.setStepCosts(costs);

        var path = Pathfinder.findPath(grid, FROM, TO);
        assertFalse(path.isEmpty());
        boolean upTheRoad = false;
        for (var at : path.getWaypoints()) {
            int cx = grid.toCellX(at);
            int cy = grid.toCellY(at);
            assertTrue(road(cx, cy), "on the road: " + at);
            upTheRoad |= cy <= 3;
        }
        assertTrue(upTheRoad, "round by the road, not across the field");
    }

    @Test
    void withNoCostsItGoesStraightAcrossAsEver() {
        var grid = new PathGrid(WIDE, DEEP);
        for (var at : Pathfinder.findPath(grid, FROM, TO).getWaypoints()) {
            assertTrue(grid.toCellY(at) >= 9, "the straight way: " + at);
        }
    }
}
