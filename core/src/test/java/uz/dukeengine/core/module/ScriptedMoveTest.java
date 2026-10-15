package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A thing moved by the story: over an arc, frame by frame, down where it was sent, its landing heard once. */
class ScriptedMoveTest {

    private static final class TestLogic extends uz.dukeengine.core.GameLogic {
        TestLogic(ThingFactory things) {
            super(things);
        }

        @Override
        protected void simulate() {
        }
    }

    private static final ThingTemplate WALKER = ThingTemplate.named("Walker")
            .geometry(new Geometry.Cylinder(4f, 9f))
            .module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(60f))
            .build();

    @Test
    void sixtyUnitsOverTwentyFramesAlongAnArcFifteenHighStandsThereOnTheTwentiethAndLandsOnce() {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(WALKER);
        var logic = new TestLogic(things);
        logic.init();
        logic.setPathGrid(new PathGrid(20, 20));
        var walker = logic.createObject(WALKER);
        walker.setPosition(new Coord3D(40f, 100f, 0f));
        var landed = new AtomicInteger();
        var move = ScriptedMove.along(walker, new Coord3D(100f, 100f, 0f), 20, 15f).onLanded(landed::incrementAndGet);
        walker.getLocomotor().moveTo(new Coord3D(40f, 180f, 0f)); // an order it does not take

        for (int frame = 0; frame < 10; frame++) {
            logic.update();
        }
        assertEquals(70f, walker.getPosition().x(), 1f, "half way across");
        assertEquals(15f, walker.getPosition().z(), 0.5f, "at the top of its arc");
        assertEquals(100f, walker.getPosition().y(), 1e-3f, "and not walked off by its legs");
        assertFalse(move.isDone());

        for (int frame = 0; frame < 10; frame++) {
            logic.update();
        }
        assertEquals(new Coord3D(100f, 100f, 0f), walker.getPosition(), "where it was sent, on the twentieth");
        assertTrue(move.isDone());
        assertEquals(1, landed.get());

        for (int frame = 0; frame < 5; frame++) {
            logic.update();
        }
        assertEquals(1, landed.get(), "heard once");
    }
}
