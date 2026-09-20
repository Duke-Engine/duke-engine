package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * What a body does with a world that has more than one floor in it.
 *
 * <p>Three things, and each of them is invisible until it is wrong. It walks up
 * the stair rather than through the wall beside it. It stands at the height of
 * whatever it is standing on, every step, rather than at the height it set off
 * from. And it is not in the way of something on another floor — two bodies a
 * step apart in plan are not near each other at all when one of them is a storey
 * up, and a locomotor that thinks otherwise refuses to walk past a stair.
 */
class ClimbingTest {

    /** Two floors, joined at one cell — the same shape {@code LevelsTest} uses. */
    private static final String MAP = """
            ############
            #0000011111#
            #0000011111#
            #0000/11111#
            #0000011111#
            ############
            """;

    private static final float LEVEL_HEIGHT = 6f;

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private static TestLogic world(String map) {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        thingFactory.addTemplate(ThingTemplate.named("Walker")
                .geometry(new Geometry.Cylinder(4f, 10f))
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(30f))
                .build());

        var logic = new TestLogic(thingFactory);
        logic.init();
        var grid = MapLoader.fromText(map);
        MapLoader.levels(grid, map);
        grid.setLevelHeight(LEVEL_HEIGHT);
        logic.setPathGrid(grid);
        return logic;
    }

    private static Coord3D at(int cx, int cy) {
        return new Coord3D((cx + 0.5f) * PathGrid.DEFAULT_CELL_SIZE,
                (cy + 0.5f) * PathGrid.DEFAULT_CELL_SIZE, 0f);
    }

    private static GameObject walker(TestLogic logic, int cx, int cy) {
        var object = logic.createObject(logic.findTemplate("Walker"));
        object.setPosition(at(cx, cy));
        return object;
    }

    /** It goes round to the stair and ends up on the upper floor. */
    @Test
    void aWalkerClimbsByTheStair() {
        var logic = world(MAP);
        var walker = walker(logic, 1, 1);
        walker.findModule(MoveUpdate.class).moveTo(at(10, 1));

        for (int frame = 0; frame < 900 && walker.findModule(MoveUpdate.class).isMoving(); frame++) {
            logic.update();
        }

        assertFalse(walker.findModule(MoveUpdate.class).isMoving(), "it should have arrived");
        assertTrue(walker.getPosition().distance(at(10, 1)) < 12f,
                "it ended at " + walker.getPosition() + " rather than on the far floor");
    }

    /**
     * Having arrived upstairs, it stands still.
     *
     * <p>The symptom was a hero shivering on the top step for two seconds and then
     * stopping for no visible reason. The cause: how far there was left to go was
     * measured in three dimensions while the walking happened in two, so a
     * destination a storey higher stayed a storey away however close he got. He
     * never arrived; he was eventually given up on by the check that catches a
     * mover going round in circles, which is what the two seconds were.
     *
     * <p>So this measures the shivering itself — how far it travels after it
     * should already have stopped.
     */
    @Test
    void itStopsDeadOnceItIsThere() {
        var logic = world(MAP);
        var walker = walker(logic, 1, 1);
        var target = at(10, 1);
        walker.findModule(MoveUpdate.class).moveTo(target);

        for (int frame = 0; frame < 900 && walker.findModule(MoveUpdate.class).isMoving(); frame++) {
            logic.update();
        }
        var restedAt = walker.getPosition();
        float wandered = 0f;
        for (int frame = 0; frame < 60; frame++) {
            logic.update();
            wandered = Math.max(wandered, restedAt.distance(walker.getPosition()));
        }

        // Arriving is the one thing that puts a mover exactly on its destination —
        // the last leg ends by setting the position rather than stepping toward
        // it. Anything else stops it somewhere nearby instead, which is what
        // giving up looks like and is indistinguishable from arrival by distance
        // alone.
        assertEquals(target.x(), restedAt.x(), 0.001f,
                "it stopped near the destination rather than on it, which is what "
                        + "being given up on looks like");
        assertEquals(target.y(), restedAt.y(), 0.001f);
        assertEquals(0f, wandered, 0.001f, "and then stood still rather than shuffling");
    }

    /** And while it walks, it stands on whatever is under it — not on where it began. */
    @Test
    void itStandsAtTheHeightOfTheGroundBeneathIt() {
        var logic = world(MAP);
        var grid = logic.getPathGrid();
        var walker = walker(logic, 1, 1);
        walker.findModule(MoveUpdate.class).moveTo(at(10, 1));

        boolean everClimbed = false;
        for (int frame = 0; frame < 900 && walker.findModule(MoveUpdate.class).isMoving(); frame++) {
            logic.update();
            var position = walker.getPosition();
            assertEquals(grid.groundHeight(position), position.z(), 0f,
                    "at frame " + frame + " it was standing at " + position.z()
                            + " over ground at " + grid.groundHeight(position));
            everClimbed |= position.z() > 0f;
        }

        assertTrue(everClimbed, "it never got up to the second floor at all");
        assertEquals(LEVEL_HEIGHT, walker.getPosition().z(), 0f, "and it finished up there");
    }

    /**
     * Take the stair out and the far floor is unreachable — the walker does not
     * quietly step up the wall instead.
     */
    @Test
    void withoutAStairItNeverLeavesItsOwnFloor() {
        var logic = world(MAP);
        logic.getPathGrid().setRamp(5, 3, false);
        var walker = walker(logic, 1, 1);
        walker.findModule(MoveUpdate.class).moveTo(at(10, 1));

        for (int frame = 0; frame < 600; frame++) {
            logic.update();
            assertEquals(0f, walker.getPosition().z(), 0f,
                    "it climbed a wall at frame " + frame);
            assertEquals(0, logic.getPathGrid().level(
                    logic.getPathGrid().toCellX(walker.getPosition()),
                    logic.getPathGrid().toCellY(walker.getPosition())),
                    "it ended up on the upper floor with no way to have got there");
        }
    }

    /**
     * Nothing on another floor is in the way — and the only thing that changes
     * the answer is the floor.
     *
     * <p>The two bodies here really do share ground: standing either side of the
     * boundary, four units apart with a radius of four each, they overlap on the
     * map. On one floor that is a body in the way. Across two floors it is a man
     * upstairs.
     */
    @Test
    void onlyTheFloorDecidesWhetherABodyIsInTheWay() {
        var logic = world(MAP);
        var grid = logic.getPathGrid();

        var upstairs = walker(logic, 6, 1);
        upstairs.setPosition(new Coord3D(61f, 15f, LEVEL_HEIGHT)); // just over the boundary
        var downstairs = walker(logic, 2, 1);
        var edge = new Coord3D(59f, 15f, 0f); // just under it, still on the lower floor

        assertTrue(Footprint.of(upstairs)
                        .overlaps(Footprint.of(downstairs, edge)),
                "the two do share ground — this test is about the floor, not the distance");
        assertNull(logic.findBlocker(downstairs, edge),
                "a body a storey up is not standing in this one's way");

        grid.setLevel(6, 1, 0); // the same two bodies, now on one floor
        assertNotNull(logic.findBlocker(downstairs, edge),
                "and on one floor they are in each other's way, as they always were");
    }

    /** A world with no height in it behaves as though none of this existed. */
    @Test
    void aFlatWorldIsUntouched() {
        var flat = """
                ########
                #......#
                #......#
                ########
                """;
        var logic = world(flat);
        var walker = walker(logic, 1, 1);
        var other = walker(logic, 2, 1);
        walker.findModule(MoveUpdate.class).moveTo(at(6, 2));

        for (int frame = 0; frame < 300 && walker.findModule(MoveUpdate.class).isMoving(); frame++) {
            logic.update();
            assertEquals(0f, walker.getPosition().z(), 0f, "flat ground is at zero");
        }

        assertTrue(walker.getPosition().distance(at(6, 2)) < 6f, "it should have arrived");
        assertNotNull(logic.findBlocker(other, walker.getPosition()),
                "bodies still block each other on flat ground");
    }
}
