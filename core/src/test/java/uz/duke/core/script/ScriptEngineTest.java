package uz.duke.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

class ScriptEngineTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private ThingTemplate unit;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        unit = ThingTemplate.named("Unit").module(new ActiveBody.Data(100f)).build();
        thingFactory.addTemplate(unit);
        logic = new TestLogic(thingFactory);
        logic.init();
    }

    @Test
    void oneShotTriggerFiresExactlyOnce() {
        var fires = new AtomicInteger();
        logic.addTrigger(Trigger.once("threeUnits",
                l -> l.getObjectCount() >= 3,
                l -> fires.incrementAndGet()));

        logic.update();
        assertEquals(0, fires.get()); // no objects yet

        logic.createObject(unit);
        logic.createObject(unit);
        logic.createObject(unit);
        logic.update(); // condition now true -> fires
        logic.update(); // already fired and removed -> no second fire
        assertEquals(1, fires.get());
    }

    @Test
    void repeatingTriggerFiresEveryQualifyingFrame() {
        var fires = new AtomicInteger();
        logic.createObject(unit);
        logic.addTrigger(Trigger.repeating("always",
                l -> l.getObjectCount() > 0,
                l -> fires.incrementAndGet()));

        logic.update();
        logic.update();
        logic.update();
        assertEquals(3, fires.get());
    }

    @Test
    void victoryStyleConditionTriggersOnElimination() {
        var won = new java.util.concurrent.atomic.AtomicBoolean();
        var doomed = logic.createObject(unit);
        logic.addTrigger(Trigger.once("victory",
                l -> l.getObjectCount() == 0,
                l -> won.set(true)));

        logic.update();
        assertEquals(false, won.get());

        logic.destroyObject(doomed);
        logic.update(); // doomed reaped at start of frame -> count 0 -> victory
        assertTrue(won.get());
    }

    @Test
    void triggersAddedByActionsWaitUntilNextFrame() {
        var second = new AtomicInteger();
        logic.addTrigger(Trigger.once("adder",
                l -> true,
                l -> l.addTrigger(Trigger.repeating("added", x -> true, x -> second.incrementAndGet()))));

        logic.update(); // adder fires, registers "added" (which must NOT fire this frame)
        assertEquals(0, second.get());
        logic.update(); // now "added" fires
        assertTrue(second.get() >= 1);
    }
}
