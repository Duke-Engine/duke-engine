package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

class ContainTest {

    static final class TestLogic extends RtsSimulation {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
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
                .module(new ActiveBody.Data(200f))
                .module(new ContainModule.Data(2))
                .build();
        infantry = ThingTemplate.named("Infantry")
                .module(new ActiveBody.Data(50f))
                .module(new MoveUpdate.Data(30f))
                .build();
        thingFactory.addTemplate(transport);
        thingFactory.addTemplate(infantry);
        logic = new TestLogic(thingFactory);
        logic.init();
    }

    /** A transport holding two soldiers taken out of the world takes them with it, the same frame, killing nobody. */
    @Test
    void aHolderTakenOutOfTheWorldTakesItsPassengersWithIt() {
        var apc = logic.createObject(transport);
        var a = logic.createObject(infantry);
        var b = logic.createObject(infantry);
        apc.findModule(ContainModule.class).load(a);
        apc.findModule(ContainModule.class).load(b);
        logic.update();
        logic.drainEvents();

        logic.destroyObject(apc);
        logic.update();

        assertFalse(logic.getObjects().contains(a) || logic.getObjects().contains(b), "gone with it, the same frame");
        assertTrue(logic.drainEvents().stream().noneMatch(uz.dukeengine.core.event.ObjectDied.class::isInstance),
                "and nobody died");
    }

    /** A transport holding two soldiers killed lets them out beside it, alive, the frame it dies: OpenContain::onDie. */
    @Test
    void aHolderThatDiesLetsItsPassengersOutBesideIt() {
        var apc = logic.createObject(transport);
        apc.setPosition(new Coord3D(20f, 20f, 0f));
        var a = logic.createObject(infantry);
        var b = logic.createObject(infantry);
        apc.findModule(ContainModule.class).load(a);
        apc.findModule(ContainModule.class).load(b);
        logic.update();

        apc.getBody().setHealth(0f);
        logic.update();

        assertFalse(logic.getObjects().contains(apc), "it died this frame");
        for (var soldier : java.util.List.of(a, b)) {
            assertTrue(logic.getObjects().contains(soldier) && !soldier.isContained(), "out, the same frame");
            assertEquals(50f, soldier.getBody().getHealth(), "alive and unhurt");
            assertTrue(soldier.getPosition().distance(apc.getPosition()) < 10f, "beside it");
        }
    }

    /** A hold naming a share deals it to each passenger as it dies, and lets them out: DamagePercentToUnits. */
    @Test
    void aHolderThatDiesDealsItsPassengersItsShare() {
        var bunker = logic.createObject(ThingTemplate.named("Bunker").module(new ActiveBody.Data(200f))
                .module(new ContainModule.Data(2, null, false, null, false, null, null, null, false, null, false, null,
                        0.5f)).build());
        var a = logic.createObject(infantry);
        bunker.findModule(ContainModule.class).load(a);

        bunker.getBody().setHealth(0f);
        logic.update();

        assertEquals(25f, a.getBody().getHealth(), "half its most health");
        assertFalse(a.isContained(), "and out");
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
    void passengersAreListedInTheOrderTheyGotIn() {
        var chinook = logic.createObject(ThingTemplate.named("Chinook")
                .module(new ActiveBody.Data(400f)).module(new ContainModule.Data(8)).build());
        var hold = chinook.findModule(ContainModule.class);
        var a = logic.createObject(infantry);
        var b = logic.createObject(infantry);
        var c = logic.createObject(infantry);
        var d = logic.createObject(infantry);

        hold.load(a);
        hold.load(b);
        hold.load(c);
        assertEquals(java.util.List.of(a.getId(), b.getId(), c.getId()), hold.getPassengers());

        hold.unload(b);
        assertEquals(java.util.List.of(a.getId(), c.getId()), hold.getPassengers(), "the others keep their places");

        hold.load(d);
        assertEquals(java.util.List.of(a.getId(), c.getId(), d.getId()), hold.getPassengers(), "and the new one last");
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
