package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;

/** An errand that put a word on its unit for its show takes it off again, however it ends. */
class GivenUpTest {

    /** A soldier raising a flag over what he captures: the word on him while he does. */
    private static final class RaisingFlag extends UpdateModule implements Errand {
        private int left;

        RaisingFlag(GameObject soldier, int frames) {
            super(soldier);
            this.left = frames;
        }

        @Override
        public void update() {
            if (left <= 0) {
                return;
            }
            getOwner().setCondition("RAISING_FLAG");
            if (--left == 0) {
                getOwner().clearCondition("RAISING_FLAG"); // raised: its own show is over
            }
        }

        @Override
        public void onRemoved() {
            getOwner().clearCondition("RAISING_FLAG");
        }
    }

    private static GameObject soldier(ProductionTest.TestLogic[] world) {
        var things = new ThingFactory(RtsModules.withDefaults());
        var template = RtsTemplate.named("Rebel").module(new ActiveBody.Data(100f)).build();
        things.addTemplate(template);
        var logic = new ProductionTest.TestLogic(things);
        logic.init();
        world[0] = logic;
        var soldier = logic.createObject(template);
        soldier.setPosition(new Coord3D(10f, 10f, 0f));
        return soldier;
    }

    @Test
    void anErrandGivenUpForANewOrderTakesItsWordOffBeforeTheNextFrame() {
        var world = new ProductionTest.TestLogic[1];
        var rebel = soldier(world);
        rebel.addModule(new RaisingFlag(rebel, 30));
        world[0].update();
        assertTrue(rebel.hasCondition("RAISING_FLAG"), "raising it");

        Errand.giveUpAll(rebel); // what every new order does to the errands a unit is on
        assertFalse(rebel.hasCondition("RAISING_FLAG"), "given up: the flag is down at once");
        world[0].update();
        assertFalse(rebel.hasCondition("RAISING_FLAG"), "and stays down the next frame");
    }

    @Test
    void anErrandThatFinishesOnItsOwnClearsItAsItAlwaysDid() {
        var world = new ProductionTest.TestLogic[1];
        var rebel = soldier(world);
        var flag = new RaisingFlag(rebel, 3);
        rebel.addModule(flag);
        for (int frame = 0; frame < 3; frame++) {
            world[0].update();
        }
        assertFalse(rebel.hasCondition("RAISING_FLAG"), "raised and done");
        assertTrue(rebel.getModules().contains(flag), "and not taken off: nothing was given up");
    }
}
