package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.Pathfinder;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.core.thing.World;

/**
 * Somewhere that cannot be reached: a mover goes as near as it can and says so, and the spot beside a thing it
 * is sent to is one it can stand on and walk to.
 *
 * <p>Cells are 10 across. The "cliff" here is a block of solid cells north-west of a building — the shape of the
 * one a supply truck met on a real map, where the straight line from it toward its depot ran onto the cliff face.
 */
class NearestReachableTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private static TestLogic world(PathGrid grid) {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(ThingTemplate.named("Truck")
                .geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(30f))
                .build());
        factory.addTemplate(ThingTemplate.named("Depot")
                .geometry(new Geometry.Box(10f, 10f, 8f))
                .module(new ActiveBody.Data(500f))
                .build());
        var logic = new TestLogic(factory);
        logic.init();
        logic.setPathGrid(grid);
        return logic;
    }

    private static GameObject make(TestLogic logic, String template, float x, float y) {
        var thing = logic.createObject(logic.getThingFactory().findTemplate(template));
        thing.setPosition(new Coord3D(x, y, 0f));
        return thing;
    }

    /** A depot at (155, 155), and solid rock just north-west of it, where a truck from the north-west aims. */
    private static PathGrid cliffBesideTheDepot() {
        var grid = new PathGrid(30, 30);
        for (int cx = 12; cx <= 13; cx++) {
            for (int cy = 16; cy <= 18; cy++) {
                grid.setBlocked(cx, cy, true);
            }
        }
        return grid;
    }

    // ---- the pathfinder ----

    /** A goal on the far side of a wall with no way round: a route to the nearest cell, and it says so. */
    @Test
    void aGoalThatCannotBeReachedGivesARouteToTheNearestPlaceThatCan() {
        var grid = new PathGrid(10, 10);
        for (int cy = 0; cy < 10; cy++) {
            grid.setBlocked(5, cy, true);
        }
        var path = Pathfinder.findPathOrNearest(grid, new Coord3D(15f, 45f, 0f), new Coord3D(85f, 45f, 0f), 0f);

        assertFalse(path.reachesGoal());
        assertEquals(new Coord3D(45f, 45f, 0f), path.getDestination(), "against the wall, level with the goal");

        var open = Pathfinder.findPathOrNearest(new PathGrid(10, 10), new Coord3D(15f, 45f, 0f),
                new Coord3D(85f, 45f, 0f), 0f);
        assertTrue(open.reachesGoal(), "and one that can be reached says that");
        assertEquals(new Coord3D(85f, 45f, 0f), open.getDestination());
    }

    // ---- a mover ----

    /** Sent into a sealed pocket, it walks to the nearest cell outside it and stops there, short. */
    @Test
    void aMoveIntoAWalledOffPocketEndsAtTheNearestReachableCell() {
        var grid = new PathGrid(30, 30);
        for (int cx = 13; cx <= 17; cx++) {
            for (int cy = 13; cy <= 17; cy++) {
                boolean edge = cx == 13 || cx == 17 || cy == 13 || cy == 17;
                grid.setBlocked(cx, cy, edge);
            }
        }
        var logic = world(grid);
        var truck = make(logic, "Truck", 55f, 155f);
        var legs = truck.findModule(MoveUpdate.class);
        // Exactly there, as a move into something goes: a move to a place would take the nearest block it can reach.
        legs.moveExactlyTo(new Coord3D(155f, 155f, 0f));
        assertFalse(legs.isGoalReachable(), "known from the start");
        for (int frame = 0; frame < 300 && legs.isMoving(); frame++) {
            logic.update();
        }

        assertFalse(legs.isMoving());
        assertTrue(legs.stoppedShort(), "stopped short, and says so");
        assertEquals(125f, truck.getPosition().x(), 1e-3f, "at the wall of the pocket, on its own side");
        assertEquals(155f, truck.getPosition().y(), 1e-3f);
    }

    /** One that gets where it was sent has not stopped short. */
    @Test
    void aMoveThatArrivesHasNotStoppedShort() {
        var logic = world(new PathGrid(30, 30));
        var truck = make(logic, "Truck", 55f, 155f);
        var legs = truck.findModule(MoveUpdate.class);
        legs.moveTo(new Coord3D(105f, 155f, 0f));
        for (int frame = 0; frame < 300 && legs.isMoving(); frame++) {
            logic.update();
        }
        assertFalse(legs.stoppedShort());
        assertEquals(105f, truck.getPosition().x(), 1e-3f);
    }

    // ---- a spot beside a thing ----

    /**
     * The straight line from a truck in the north-west toward the depot runs onto the rock. The spot it is given
     * is beside the depot, on open ground, and one it can walk to — and it gets there.
     */
    @Test
    void aSpotBesideAThingAcrossACliffIsOneTheMoverCanStandOnAndReach() {
        var logic = world(cliffBesideTheDepot());
        var depot = make(logic, "Depot", 155f, 155f);
        var truck = make(logic, "Truck", 55f, 255f);
        logic.update(); // the depot is baked into the ground

        // Where the straight line puts it, a cell short of touching: on the rock.
        float gap0 = World.reachBetween(truck, depot) - logic.cellSize();
        float span = (float) Math.sqrt(100f * 100f + 100f * 100f);
        var straight = new Coord3D(55f + 100f / span * gap0, 255f - 100f / span * gap0, 0f);
        var grid = logic.getPathGrid();
        assertTrue(grid.isBlocked(grid.toCellX(straight), grid.toCellY(straight)), "the straight line runs onto it");

        var spot = logic.standingNextTo(truck, depot);
        assertFalse(grid.isBlocked(grid.toCellX(spot), grid.toCellY(spot)), "never a blocked cell");
        float gap = Footprint.of(truck, spot).separation(Footprint.of(depot));
        assertTrue(gap >= 0f && gap <= logic.cellSize(), "beside it: " + gap);

        var legs = truck.findModule(MoveUpdate.class);
        legs.moveTo(spot);
        assertTrue(legs.isGoalReachable(), "and it can be walked to");
        for (int frame = 0; frame < 600 && legs.isMoving(); frame++) {
            logic.update();
        }
        assertTrue(logic.isBeside(truck, depot), "which it does");
        assertFalse(legs.stoppedShort());
    }

    /** From anywhere round the depot, the spot is never a blocked cell. */
    @Test
    void aSpotBesideAThingIsNeverABlockedCell() {
        var logic = world(cliffBesideTheDepot());
        var depot = make(logic, "Depot", 155f, 155f);
        var truck = make(logic, "Truck", 55f, 255f);
        logic.update();
        var grid = logic.getPathGrid();
        for (int around = 0; around < 16; around++) {
            double angle = around * Math.PI / 8;
            truck.setPosition(new Coord3D(155f + (float) (90 * Math.cos(angle)), 155f + (float) (90 * Math.sin(angle)),
                    0f));
            var spot = logic.standingNextTo(truck, depot);
            assertFalse(grid.isBlocked(grid.toCellX(spot), grid.toCellY(spot)), "from direction " + around);
        }
    }

    /** Beside it already, it stays where it is. */
    @Test
    void aMoverBesideTheThingAlreadyStaysWhereItIs() {
        var logic = world(cliffBesideTheDepot());
        var depot = make(logic, "Depot", 155f, 155f);
        var truck = make(logic, "Truck", 170f, 155f);
        logic.update();

        assertTrue(logic.isBeside(truck, depot));
        assertEquals(truck.getPosition(), logic.standingNextTo(truck, depot));
    }
}
