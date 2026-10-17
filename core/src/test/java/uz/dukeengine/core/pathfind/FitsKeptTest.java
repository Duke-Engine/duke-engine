package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** What a body fits at is kept cell by cell, and forgotten where the ground changes (E97). */
class FitsKeptTest {

    private static boolean straight(PathGrid grid) {
        var path = Pathfinder.findPathOrNearest(grid, new Coord3D(15f, 105f, 0f), new Coord3D(185f, 105f, 0f), 3f);
        return path.reachesGoal() && path.getWaypoints().stream().allMatch(at -> Math.abs(at.y() - 105f) < 1f);
    }

    @Test
    void aRouteSeesACellBlockedAndATrunkLaidAfterItWasAsked() {
        var grid = new PathGrid(20, 20, 10f);
        assertTrue(straight(grid), "open ground: straight across");

        grid.setBlocked(10, 10, true);
        assertFalse(straight(grid), "a cell blocked on its line: round it");
        grid.setBlocked(10, 10, false);
        assertTrue(straight(grid), "opened again: straight");

        grid.setSceneryFootprints(List.of(new SceneryFootprint(105f, 105f, 2f, 0)));
        assertFalse(straight(grid), "a trunk on its line: round it");
        grid.setSceneryFootprints(List.of());
        assertTrue(straight(grid));
    }
}
