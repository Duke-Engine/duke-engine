package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.pathfind.ObstacleRules;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.thing.RtsKinds;

/**
 * What is in the way on the ground, as the reference classifies a footprint: structures, not trees; nothing standing
 * more than a cell over the ground; a fence along its line; and a still thing's footprint laid again when it turns.
 */
class InTheWayTest {

    private CombatTest.CombatLogic world;
    private PathGrid grid;

    private void world(ObstacleRules rules) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Tree").geometry(new Geometry.Cylinder(8f, 30f))
                .module(new ActiveBody.Data(100f)).build());
        factory.addTemplate(RtsTemplate.named("Barracks").kindOf(RtsKinds.STRUCTURE)
                .geometry(new Geometry.Box(15f, 15f, 20f)).module(new ActiveBody.Data(1000f)).build());
        factory.addTemplate(RtsTemplate.named("Bridge").kindOf(RtsKinds.STRUCTURE)
                .geometry(new Geometry.Box(200f, 20f, 5f)).module(new ActiveBody.Data(1000f)).build());
        factory.addTemplate(RtsTemplate.named("Wall").kindOf(RtsKinds.STRUCTURE)
                .geometry(new Geometry.Box(40f, 3f, 10f)).module(new ActiveBody.Data(1000f)).build());
        factory.addTemplate(RtsTemplate.named("Fence").kindOf(RtsKinds.STRUCTURE).fence(40f, 20f)
                .geometry(new Geometry.Box(20f, 20f, 5f)).module(new ActiveBody.Data(100f)).build());
        world = new CombatTest.CombatLogic(factory);
        world.init();
        grid = new PathGrid(80, 80);
        world.setPathGrid(grid);
        world.setObstacleRules(rules);
        world.getPlayerList().addPlayer("Ours");
    }

    private GameObject put(String template, float x, float y, float z) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, y, z), 0);
    }

    private boolean closed(float x, float y) {
        return grid.isBlocked(grid.toCellX(new Coord3D(x, y, 0f)), grid.toCellY(new Coord3D(x, y, 0f)));
    }

    @Test
    void aTreeLeavesItsCellOpenAndAStructureBesideItClosesItsOwn() {
        world(new ObstacleRules(Set.of(RtsKinds.STRUCTURE), Set.of(), 10f));
        put("Tree", 105f, 105f, 0f);
        put("Barracks", 205f, 105f, 0f);
        world.update();

        assertFalse(closed(105f, 105f), "a tree is no structure: its cell is open");
        assertTrue(closed(205f, 105f), "the barracks closes its own");
    }

    @Test
    void aBox400LongStanding80OverAValleyLeavesTheValleyOpen() {
        world(new ObstacleRules(Set.of(RtsKinds.STRUCTURE), Set.of(), 10f));
        put("Bridge", 400f, 400f, 80f);
        put("Barracks", 205f, 105f, 5f);
        world.update();

        assertFalse(closed(400f, 400f), "the valley under the bridge is open");
        assertFalse(closed(250f, 400f));
        assertTrue(closed(205f, 105f), "a building 5 up is still in the way");
    }

    @Test
    void aBoxTurned45DegreesAfterItIsPlacedClosesTheTurnedCellsFromTheNextRouteOn() {
        world(ObstacleRules.EVERYTHING);
        var wall = put("Wall", 400f, 400f, 0f);
        world.update();
        assertTrue(closed(430f, 400f), "along its length");
        assertFalse(closed(425f, 425f));

        wall.setOrientation((float) Math.PI / 4f);
        world.findPath(new Coord3D(100f, 100f, 0f), new Coord3D(700f, 700f, 0f)); // the next route
        assertTrue(closed(425f, 425f), "turned, the diagonal is closed");
        assertFalse(closed(430f, 400f), "and its old cells open");
    }

    @Test
    void aFenceIsInTheWayAlongItsLineAlone() {
        world(new ObstacleRules(Set.of(RtsKinds.STRUCTURE), Set.of(), 10f));
        put("Fence", 405f, 405f, 0f);
        world.update();

        assertTrue(closed(390f, 405f), "on its line, from 20 behind its middle");
        assertTrue(closed(420f, 405f), "to 20 ahead");
        assertFalse(closed(405f, 420f), "not over the rest of its shape");
    }
}
