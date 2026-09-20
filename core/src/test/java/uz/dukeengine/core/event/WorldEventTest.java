package uz.dukeengine.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** The one-way channel the simulation uses to report moments a snapshot cannot hold. */
class WorldEventTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    /** Leaves a wreck behind, the way a real die module would. */
    static final class WreckOnDeath extends Module implements DieModule {
        private final ThingTemplate wreck;
        int corpsesVisibleWhenItRan = -1;

        WreckOnDeath(GameObject owner, ThingTemplate wreck) {
            super(owner);
            this.wreck = wreck;
        }

        @Override
        public void onDie() {
            var world = getOwner().getWorld();
            corpsesVisibleWhenItRan = world.findObject(getOwner().getId()) == null ? 0 : 1;
            world.spawn(wreck, getOwner().getPosition(), getOwner().getPlayerIndex());
        }
    }

    private static final ThingTemplate SOLDIER = ThingTemplate.named("Soldier")
            .module(new ActiveBody.Data(50f))
            .build();

    private static final ThingTemplate WRECK = ThingTemplate.named("Wreck").build();

    private static TestLogic newLogic() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        thingFactory.addTemplate(SOLDIER);
        thingFactory.addTemplate(WRECK);
        var logic = new TestLogic(thingFactory);
        logic.init();
        return logic;
    }

    @Test
    void aDeathIsAnnouncedWithEverythingNeededToDrawIt() {
        var logic = newLogic();
        var soldier = logic.spawn(SOLDIER, new Coord3D(120f, 60f, 0f), 2);
        var id = soldier.getId();

        soldier.getBody().damage(999f);
        logic.update();

        var events = logic.drainEvents();
        assertEquals(1, events.size());
        var died = (ObjectDied) events.get(0);
        assertEquals(id, died.object());
        assertEquals("Soldier", died.templateName(), "it is gone, so the event must say what it was");
        assertEquals(2, died.playerIndex());
        assertEquals(120f, died.position().x(), 1e-4f);
        assertSame(died.position(), died.where(), "fog filtering needs a place");
        assertNull(logic.findObject(id), "and by now the object really is gone");
    }

    @Test
    void aDieModuleRunsInAWorldThatNoLongerHoldsTheBody() {
        var logic = newLogic();
        var soldier = logic.spawn(SOLDIER, new Coord3D(50f, 50f, 0f), 1);
        var wreckMaker = new WreckOnDeath(soldier, WRECK);
        soldier.addModule(wreckMaker);

        soldier.getBody().damage(999f);
        logic.update();

        assertEquals(0, wreckMaker.corpsesVisibleWhenItRan,
                "wreckage must not be built around a corpse still standing in the world");
        assertEquals(1, logic.getObjects().size());
        assertEquals("Wreck", logic.getObjects().get(0).getTemplate().name());
    }

    @Test
    void removingAnObjectWithoutKillingItIsNotADeath() {
        var logic = newLogic();
        var soldier = logic.spawn(SOLDIER, Coord3D.ZERO, 1);

        soldier.markDestroyed(); // a script clearing the map, not a kill
        logic.update();

        assertTrue(logic.drainEvents().isEmpty(),
                "ObjectDied means destroyed, so it is safe to hang an explosion on");
        assertTrue(logic.getObjects().isEmpty());
    }

    @Test
    void drainingTakesEachMomentExactlyOnce() {
        var logic = newLogic();
        logic.spawn(SOLDIER, Coord3D.ZERO, 1).getBody().damage(999f);
        logic.update();

        assertEquals(1, logic.drainEvents().size());
        assertTrue(logic.drainEvents().isEmpty(), "a moment is reported once, not every frame");
    }

    @Test
    void anUndrainedQueueStaysBounded() {
        var logic = newLogic(); // nothing is draining: a headless run
        for (int i = 0; i < 5000; i++) {
            logic.post(new ObjectDied(i, new uz.dukeengine.core.thing.ObjectId(i), "Soldier", 1, Coord3D.ZERO));
        }

        var events = logic.drainEvents();
        assertTrue(events.size() <= 1024, "must not grow without bound, was " + events.size());
        assertEquals(4999, events.get(events.size() - 1).frame(),
                "the newest are kept; it is the oldest that are dropped");
    }
}
