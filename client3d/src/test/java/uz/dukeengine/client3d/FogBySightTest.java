package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.SightCells;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The ground drawn by the simulation's cells of what the local player has seen — the reference's shroud picture, a
 * texel a cell: black where never seen, at the fog's remembered light where seen, whole where in sight, and black past
 * the map's edges.
 */
class FogBySightTest {

    @Test
    void neverSeenIsBlackSeenIsHalfLitInSightIsWholeAndPastTheEdgeIsBlack() {
        var fog = new Fog(false, 0f, 0.5f, 1f, 0, 100f, 64, 0x000000);
        var discovery = new Discovery(new PathGrid(12, 4), fog); // 120 by 40: three cells of 40
        var states = new byte[] {
                (byte) SightCells.Sight.NEVER_SEEN.ordinal(),
                (byte) SightCells.Sight.SEEN.ordinal(),
                (byte) SightCells.Sight.IN_SIGHT.ordinal()};
        discovery.fromSight(new SightCells.View(40f, 3, 1, states));
        discovery.soften(1f);

        assertEquals(0f, discovery.lightAt(1, 1), 1e-4f, "never seen: black");
        assertEquals(0.5f, discovery.lightAt(5, 1), 1e-4f, "seen: half lit");
        assertEquals(1f, discovery.lightAt(9, 1), 1e-4f, "in sight: whole");
        assertEquals(0f, discovery.lightAtPoint(130f, 20f), 1e-4f, "past the map's edge: black, not the edge repeated");
    }
}
