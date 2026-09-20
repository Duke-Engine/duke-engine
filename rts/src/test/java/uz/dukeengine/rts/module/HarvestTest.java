package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

class HarvestTest {

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
    private ThingFactory thingFactory;
    private ThingTemplate harvester;
    private ThingTemplate supplyPile;
    private int usa;

    @BeforeEach
    void setUp() {
        thingFactory = new ThingFactory(RtsModules.withDefaults());
        harvester = ThingTemplate.named("Harvester")
                .module(new ActiveBody.Data(100f))
                .module(new HarvestUpdate.Data(100, 10, 0f)) // 100 per 10 frames
                .build();
        supplyPile = ThingTemplate.named("Supplies")
                .module(new SupplyModule.Data(1000))
                .build();
        thingFactory.addTemplate(harvester);
        thingFactory.addTemplate(supplyPile);

        logic = new TestLogic(thingFactory);
        logic.init();
        usa = logic.getPlayerList().addPlayer("USA").getIndex();
    }

    private GameObject spawn(ThingTemplate template) {
        var o = logic.createObject(template);
        o.setPlayerIndex(usa);
        o.setPosition(Coord3D.ZERO);
        return o;
    }

    @Test
    void harvesterDepositsMoneyEachTrip() {
        spawn(harvester);
        spawn(supplyPile);
        assertEquals(0, logic.getRtsPlayer(usa).getMoney());

        for (int i = 0; i < 10; i++) {
            logic.update();
        }
        assertEquals(100, logic.getRtsPlayer(usa).getMoney()); // one trip

        for (int i = 0; i < 20; i++) {
            logic.update();
        }
        assertEquals(300, logic.getRtsPlayer(usa).getMoney()); // three trips total
    }

    @Test
    void harvestingStopsWhenPileExhausted() {
        var smallPile = ThingTemplate.named("SmallSupplies")
                .module(new SupplyModule.Data(250)) // 100,100,50 then empty
                .build();
        thingFactory.addTemplate(smallPile);

        spawn(harvester);
        var pile = spawn(smallPile);

        for (int i = 0; i < 100; i++) {
            logic.update();
        }
        assertEquals(250, logic.getRtsPlayer(usa).getMoney()); // never exceeds the pile
        assertEquals(0, pile.findModule(SupplyModule.class).getRemaining());
    }

    @Test
    void idleWithoutSupplies() {
        spawn(harvester); // no pile present
        for (int i = 0; i < 50; i++) {
            logic.update();
        }
        assertEquals(0, logic.getRtsPlayer(usa).getMoney());
    }

    @Test
    void supplyTakeNeverGoesNegative() {
        var owner = new GameObject(new uz.dukeengine.core.thing.ObjectId(1),
                ThingTemplate.named("X").build());
        var supply = new SupplyModule(owner, new SupplyModule.Data(250));
        assertEquals(250, supply.take(1000)); // capped at remaining
        assertEquals(0, supply.getRemaining());
        assertEquals(0, supply.take(50)); // nothing left
        assertTrue(supply.getRemaining() == 0);
    }
}
