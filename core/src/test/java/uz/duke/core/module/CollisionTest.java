package uz.duke.core.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.Footprint;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.Geometry;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

/** Objects have a physical size, so they cannot occupy the same ground. */
class CollisionTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private static final ThingTemplate WALKER = ThingTemplate.named("Walker")
            .geometry(new Geometry.Cylinder(3f, 8f))
            .module("ActiveBody", new ActiveBody.Data(100f))
            .module("MoveUpdate", new MoveUpdate.Data(60f))
            .build();

    private static final ThingTemplate BUNKER = ThingTemplate.named("Bunker")
            .geometry(new Geometry.Box(20f, 20f, 10f))
            .module("ActiveBody", new ActiveBody.Data(500f))
            .build();

    /** A ghost: no geometry at all, the pre-collision default. */
    private static final ThingTemplate GHOST = ThingTemplate.named("Ghost")
            .module("ActiveBody", new ActiveBody.Data(100f))
            .module("MoveUpdate", new MoveUpdate.Data(60f))
            .build();

    private static TestLogic logicWith(ThingTemplate... templates) {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        for (var template : templates) {
            thingFactory.addTemplate(template);
        }
        var logic = new TestLogic(thingFactory);
        logic.init();
        return logic;
    }

    private static GameObject spawn(TestLogic logic, ThingTemplate template, float x, float y) {
        var object = logic.createObject(template);
        object.setPosition(new Coord3D(x, y, 0f));
        return object;
    }

    private static boolean overlapping(GameObject a, GameObject b) {
        return Footprint.of(a).overlaps(Footprint.of(b));
    }

    @Test
    void aUnitWalksAroundABuildingInsteadOfThroughIt() {
        var logic = logicWith(WALKER, BUNKER);
        var bunker = spawn(logic, BUNKER, 100f, 50f);
        var walker = spawn(logic, WALKER, 10f, 50f);

        var goal = new Coord3D(190f, 50f, 0f);
        walker.findModule(MoveUpdate.class).moveTo(goal); // straight through the bunker

        boolean everInsideTheBunker = false;
        for (int frame = 0; frame < 600; frame++) {
            logic.update();
            if (overlapping(walker, bunker)) {
                everInsideTheBunker = true;
            }
        }

        assertFalse(everInsideTheBunker, "the walker must never stand inside the bunker");
        assertTrue(walker.getPosition().distance(goal) < 5f,
                "the walker should steer around and still arrive, but stopped at "
                        + walker.getPosition());
    }

    @Test
    void unitsDoNotStackOnTopOfEachOther() {
        var logic = logicWith(WALKER);
        var parked = spawn(logic, WALKER, 100f, 50f);
        var walker = spawn(logic, WALKER, 10f, 50f);

        walker.findModule(MoveUpdate.class).moveTo(parked.getPosition()); // "go stand where he is"

        for (int frame = 0; frame < 300; frame++) {
            logic.update();
            assertFalse(overlapping(walker, parked),
                    "units must never share ground (frame " + frame + ")");
        }
        assertFalse(walker.findModule(MoveUpdate.class).isMoving(),
                "a unit that cannot reach its goal gives up rather than grinding forever");
    }

    @Test
    void aUnitSentAtSomethingStopsAgainstItRatherThanCirclingIt() {
        var logic = logicWith(WALKER);
        var parked = spawn(logic, WALKER, 100f, 50f);
        var walker = spawn(logic, WALKER, 10f, 50f);

        // Ordered to stand exactly where something already stands — the shape of
        // "click the enemy" in any game built on this.
        walker.findModule(MoveUpdate.class).moveTo(parked.getPosition());

        float travelled = 0f;
        var previous = walker.getPosition();
        for (int frame = 0; frame < 300; frame++) {
            logic.update();
            travelled += walker.getPosition().distance(previous);
            previous = walker.getPosition();
        }

        float direct = 90f - 6f; // the 90 between them, less the two bodies
        assertTrue(travelled < direct + 20f,
                "it should walk there and stop, not walk there and then around it: "
                        + travelled + " units for a " + direct + " unit trip");
        assertFalse(walker.findModule(MoveUpdate.class).isMoving());
        assertFalse(overlapping(walker, parked));
    }

    @Test
    void aUnitSpawnedInsideAnotherCanWalkFree() {
        var logic = logicWith(WALKER);
        var parked = spawn(logic, WALKER, 100f, 50f);
        var trapped = spawn(logic, WALKER, 100f, 50f); // exactly on top of it

        assertTrue(overlapping(trapped, parked), "they start overlapping");
        trapped.findModule(MoveUpdate.class).moveTo(new Coord3D(200f, 50f, 0f));

        for (int frame = 0; frame < 300; frame++) {
            logic.update();
        }

        assertFalse(overlapping(trapped, parked), "it must be able to escape, not lock in place");
        assertTrue(trapped.getPosition().x() > 190f, "and then carry on to its goal");
    }

    @Test
    void objectsWithoutGeometryStillPassThroughEachOther() {
        var logic = logicWith(GHOST);
        var parked = spawn(logic, GHOST, 100f, 50f);
        var ghost = spawn(logic, GHOST, 10f, 50f);

        var goal = new Coord3D(190f, 50f, 0f);
        ghost.findModule(MoveUpdate.class).moveTo(goal);

        for (int frame = 0; frame < 300 && ghost.findModule(MoveUpdate.class).isMoving(); frame++) {
            logic.update();
        }

        assertTrue(ghost.getPosition().distance(goal) < 1f,
                "data written before geometry existed must behave exactly as it did");
        assertTrue(parked.getPosition().distance(new Coord3D(100f, 50f, 0f)) < 1f);
    }
}
