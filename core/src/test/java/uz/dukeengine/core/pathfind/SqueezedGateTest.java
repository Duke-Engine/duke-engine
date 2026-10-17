package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/**
 * A court whose gate is narrower than the body sent into it: routed by sectors laid for its width, it still squeezes in,
 * as it did before the sectors knew its width (E97's regression, the commander walking into the palace).
 */
class SqueezedGateTest {

    @Test
    void aBodyWiderThanTheGateStillSqueezesIntoTheCourt() {
        var grid = new PathGrid(64, 64, 5f);
        for (int i = 20; i <= 40; i++) {
            grid.setBlocked(i, 20, true);
            grid.setBlocked(i, 40, true);
            grid.setBlocked(20, i, true);
            grid.setBlocked(40, i, true);
        }
        grid.setBlocked(40, 30, false); // the gate: a cell wide, a body of 4 does not fit in it
        var sectors = Sectors.of(grid, 16, 4f);
        var flat = Sectors.of(grid, 16);
        var court = new Coord3D(150f, 150f, 0f);
        var path = Pathfinder.findPathOrNearest(grid, new Coord3D(280f, 150f, 0f), court, 4f, Zones.over(sectors),
                null, null, sectors, flat);
        assertTrue(path.reachesGoal(), "into the court: " + path.getWaypoints());
    }
}
