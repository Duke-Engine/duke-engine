package uz.duke.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.pathfind.MapLoader;
import uz.duke.core.thing.Geometry;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

/**
 * A world with no height in it runs exactly as it always did.
 *
 * <p>The engine is learning about levels: cells that stand higher than their
 * neighbours, and the links between them. Almost every game built here is flat
 * and will stay flat — the RTS demos, the sandbox, Generals, anything Studio
 * exports — and none of them will ever set a level. The promise made to them is
 * that they cannot tell the difference, and a promise that big is worth a test
 * that cannot be argued with.
 *
 * <p>So this is a golden master. It builds a world that exercises the parts
 * height touches — pathfinding round a wall, bodies bumping into each other,
 * swerving, arriving — runs it for ten seconds of simulation, and checks the
 * number the whole world hashes to against one written down here. That hash
 * includes every position, and a position includes its z; if height leaks into a
 * flat world anywhere, by so much as a rounding, this fails.
 *
 * <p>The value was taken from the engine as it was before levels existed. It is
 * not a number anyone chose, and it is not allowed to be updated to make a test
 * pass: if it changes, a flat world moved differently, which is exactly the
 * thing being guarded.
 */
class FlatWorldChecksumTest {

    /**
     * What this world hashes to after {@link #FRAMES} frames, on the engine as it
     * stood before levels were added.
     */
    private static final long GOLDEN = 1351068428557216477L;

    private static final int FRAMES = 300; // ten seconds at 30 Hz

    /** A wall down the middle with a gap at the bottom — so routes have to detour. */
    private static final String MAP = """
            ....................
            ....................
            .........#..........
            .........#..........
            .........#..........
            .........#..........
            .........#..........
            .........#..........
            ....................
            ....................
            """;

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private static TestLogic world() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        thingFactory.addTemplate(ThingTemplate.named("Runner")
                .geometry(new Geometry.Cylinder(4f, 10f))
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("MoveUpdate", new MoveUpdate.Data(40f))
                .build());
        thingFactory.addTemplate(ThingTemplate.named("Rock")
                .geometry(new Geometry.Box(8f, 8f, 10f))
                .module("ActiveBody", new ActiveBody.Data(500f))
                .build());

        var logic = new TestLogic(thingFactory);
        logic.init();
        logic.setPathGrid(MapLoader.fromText(MAP));
        return logic;
    }

    /** Everything is created after {@code init()}, which resets the world. */
    private static void populate(TestLogic logic) {
        var runner = logic.getThingFactory().findTemplate("Runner");
        var rock = logic.getThingFactory().findTemplate("Rock");

        // Two runners sent across the wall from either side, so both detour
        // through the gap and meet in it — pathfinding, collision and swerving
        // in one scenario.
        var west = logic.createObject(runner);
        west.setPosition(new Coord3D(25f, 25f, 0f));
        west.findModule(MoveUpdate.class).moveTo(new Coord3D(155f, 25f, 0f));

        var east = logic.createObject(runner);
        east.setPosition(new Coord3D(155f, 55f, 0f));
        east.findModule(MoveUpdate.class).moveTo(new Coord3D(25f, 55f, 0f));

        // A third one told to walk into something solid: it arrives against the
        // rock and stops there rather than circling it forever.
        var pusher = logic.createObject(runner);
        pusher.setPosition(new Coord3D(25f, 85f, 0f));
        pusher.findModule(MoveUpdate.class).moveTo(new Coord3D(75f, 85f, 0f));

        var boulder = logic.createObject(rock);
        boulder.setPosition(new Coord3D(75f, 85f, 0f));

        // And one that never moves at all, so the hash covers standing still too.
        logic.createObject(rock).setPosition(new Coord3D(125f, 85f, 0f));
    }

    @Test
    void aFlatWorldHashesToWhatItAlwaysDid() {
        var logic = world();
        populate(logic);

        for (int frame = 0; frame < FRAMES; frame++) {
            logic.update();
        }

        assertEquals(GOLDEN, logic.checksum(),
                "a flat world moved differently than it used to — height has leaked into "
                        + "a game that never asked for it");
    }

    /** And it is the same world twice, which is what makes the number mean anything. */
    @Test
    void andItIsTheSameWorldEveryTime() {
        var first = world();
        populate(first);
        var second = world();
        populate(second);

        for (int frame = 0; frame < FRAMES; frame++) {
            first.update();
            second.update();
            assertEquals(first.checksum(), second.checksum(),
                    "two runs of the same world diverged at frame " + frame);
        }
    }

    /** Nothing in a flat world ever stands at a height. */
    @Test
    void nothingEverLeavesTheGround() {
        var logic = world();
        populate(logic);

        for (int frame = 0; frame < FRAMES; frame++) {
            logic.update();
            for (var object : logic.getObjects()) {
                assertEquals(0f, object.getPosition().z(), 0f,
                        object.getTemplate().name() + " left the ground at frame " + frame);
            }
        }
        assertTrue(logic.getObjects().size() >= 5, "the scenario should still be populated");
    }
}
