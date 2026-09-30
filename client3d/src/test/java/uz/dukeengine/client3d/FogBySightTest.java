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
    /**
     * Taken frame after frame, only the chunks the simulation wrote since the last view are read again — and the
     * ground comes out as it does read whole, cell for cell.
     */
    @Test
    void readOnlyWhereItChangedTheGroundIsWhatItIsReadWhole() {
        var factory = new uz.dukeengine.core.thing.ThingFactory(uz.dukeengine.core.module.ModuleFactory.withDefaults());
        factory.addTemplate(uz.dukeengine.core.thing.ThingTemplate.named("Scout").visionRange(60f).build());
        var world = new uz.dukeengine.core.GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        var grid = new PathGrid(160, 90);
        world.setPathGrid(grid);
        world.setSightCells(15f, 12);
        int me = world.getPlayerList().addPlayer("Me").getIndex();
        var scout = world.spawn(world.findTemplate("Scout"), new uz.dukeengine.core.math.Coord3D(20f, 20f, 0f), me);
        var fog = new Fog(false, 0f, 0.5f, 1f, 0, 100f, 64, 0x000000);
        var kept = new Discovery(grid, fog);
        for (int frame = 0; frame < 90; frame++) {
            scout.setPosition(new uz.dukeengine.core.math.Coord3D(20f + frame * 17f, 20f + frame * 9f, 0f));
            world.update();
            var view = world.getSightCells().view(me);
            kept.fromSight(view);
            var whole = new Discovery(grid, fog);
            whole.fromSight(view);
            for (int cy = 0; cy < grid.getHeight(); cy++) {
                for (int cx = 0; cx < grid.getWidth(); cx++) {
                    assertEquals(whole.stateAt(cx, cy), kept.stateAt(cx, cy), "frame " + frame + " " + cx + "," + cy);
                }
            }
        }
    }
}
