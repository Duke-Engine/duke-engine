package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A reactor overcharged while the game runs: its side's surplus changes that frame, and goes with the reactor. */
class PowerBonusTest {

    /** Overcharges its reactor from its own update, the frame it is told to. */
    static final class Overcharge extends UpdateModule {
        record Data() implements ModuleData {
        }

        boolean on;

        Overcharge(GameObject owner) {
            super(owner);
        }

        @Override
        public void update() {
            getOwner().findModule(PowerModule.class).setBonus(on ? 5 : 0);
        }
    }

    @Test
    void aBonusSetInsideAnUpdateCountsThatFrameAndLeavesWithTheReactor() {
        var factory = new ThingFactory(RtsModules.withDefaults()
                .register(Overcharge.Data.class, (owner, data) -> new Overcharge(owner)));
        var reactorType = ThingTemplate.named("Reactor").module(new ActiveBody.Data(800f))
                .module(new PowerModule.Data(10, 0)).module(new Overcharge.Data()).build();
        var factoryType = ThingTemplate.named("WarFactory").module(new ActiveBody.Data(2000f))
                .module(new PowerModule.Data(0, 12)).build();
        factory.addTemplate(reactorType);
        factory.addTemplate(factoryType);
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        int china = logic.getPlayerList().addPlayer("China").getIndex();
        var reactor = logic.spawn(reactorType, new Coord3D(0f, 0f, 0f), china);
        logic.spawn(factoryType, new Coord3D(100f, 0f, 0f), china);
        logic.update();
        assertEquals(-2, PowerGrid.surplus(logic, china), "10 against 12");

        reactor.findModule(Overcharge.class).on = true;
        logic.update();
        assertEquals(3, PowerGrid.surplus(logic, china), "overcharged: 15 against 12, that frame");

        logic.destroyObject(reactor);
        logic.update();
        assertEquals(-12, PowerGrid.surplus(logic, china), "and gone with it");
    }
}
