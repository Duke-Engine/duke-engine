package uz.duke.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

class ContainTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private ThingTemplate transport;
    private ThingTemplate infantry;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        transport = ThingTemplate.named("Transport")
                .module("ActiveBody", new ActiveBody.Data(200f))
                .module("ContainModule", new ContainModule.Data(2))
                .build();
        infantry = ThingTemplate.named("Infantry")
                .module("ActiveBody", new ActiveBody.Data(50f))
                .module("MoveUpdate", new MoveUpdate.Data(30f))
                .build();
        thingFactory.addTemplate(transport);
        thingFactory.addTemplate(infantry);
        logic = new TestLogic(thingFactory);
        logic.init();
    }

    @Test
    void loadsUpToCapacityThenRejects() {
        var apc = logic.createObject(transport);
        var contain = apc.findModule(ContainModule.class);
        var a = logic.createObject(infantry);
        var b = logic.createObject(infantry);
        var c = logic.createObject(infantry);

        assertTrue(contain.load(a));
        assertTrue(contain.load(b));
        assertFalse(contain.load(c)); // full (2 slots)
        assertEquals(2, contain.getPassengerCount());
        assertTrue(a.isContained());
        assertFalse(c.isContained());
    }

    @Test
    void containedUnitsDoNotMove() {
        var apc = logic.createObject(transport);
        var rider = logic.createObject(infantry);
        rider.setPosition(Coord3D.ZERO);
        rider.findModule(MoveUpdate.class).moveTo(new Coord3D(100f, 0f, 0f));
        apc.findModule(ContainModule.class).load(rider);

        for (int i = 0; i < 10; i++) {
            logic.update();
        }
        assertEquals(0f, rider.getPosition().x(), 1e-4f); // stayed put while contained
    }

    @Test
    void unloadReturnsPassengersToTheWorld() {
        var apc = logic.createObject(transport);
        apc.setPosition(new Coord3D(20f, 20f, 0f));
        var contain = apc.findModule(ContainModule.class);
        GameObject rider = logic.createObject(infantry);
        contain.load(rider);
        assertTrue(rider.isContained());

        contain.unloadAll();
        assertFalse(rider.isContained());
        assertEquals(0, contain.getPassengerCount());
        // Dropped next to the transport.
        assertTrue(rider.getPosition().distance(apc.getPosition()) < 10f);

        // And it can move again.
        rider.findModule(MoveUpdate.class).moveTo(new Coord3D(rider.getPosition().x() + 100f, rider.getPosition().y(), 0f));
        float xBefore = rider.getPosition().x();
        logic.update();
        assertTrue(rider.getPosition().x() > xBefore);
    }
}
