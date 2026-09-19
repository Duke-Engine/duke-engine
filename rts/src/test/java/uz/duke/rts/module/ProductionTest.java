package uz.duke.rts.module;

import uz.duke.rts.RtsTemplate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.rts.RtsSimulation;
import uz.duke.rts.message.GameMessage;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.core.thing.ThingTemplateLoader;

class ProductionTest {

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
    private ThingTemplate soldier;
    private GameObject barracks;
    private int usa;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        soldier = RtsTemplate.named("Soldier")
                .module(new ActiveBody.Data(50f))
                .buildCost(100)
                .buildTimeFrames(3)
                .build();
        thingFactory.addTemplate(soldier);

        var barracksTemplate = RtsTemplate.named("Barracks")
                .module(new ActiveBody.Data(500f))
                .module(new ProductionUpdate.Data())
                .build();
        thingFactory.addTemplate(barracksTemplate);

        logic = new TestLogic(thingFactory);
        logic.init();
        usa = logic.getPlayerList().addPlayer("USA").getIndex();
        logic.getRtsPlayer(usa).deposit(250);

        barracks = logic.createObject(barracksTemplate);
        barracks.setPlayerIndex(usa);
        barracks.setPosition(new Coord3D(0f, 0f, 0f));
    }

    private ProductionUpdate production() {
        return barracks.findModule(ProductionUpdate.class);
    }

    @Test
    void queueingChargesThePlayer() {
        assertTrue(production().queue(soldier));
        assertEquals(150, logic.getRtsPlayer(usa).getMoney());
        assertTrue(production().queue(soldier));
        assertEquals(50, logic.getRtsPlayer(usa).getMoney());
    }

    @Test
    void cannotQueueWhatPlayerCannotAfford() {
        production().queue(soldier); // 250 -> 150
        production().queue(soldier); // 150 -> 50
        assertFalse(production().queue(soldier)); // 50 < 100
        assertEquals(50, logic.getRtsPlayer(usa).getMoney());
        assertEquals(2, production().getQueueSize());
    }

    @Test
    void unitSpawnsAfterBuildTime() {
        production().queue(soldier);
        assertEquals(1, logic.getObjectCount()); // only the barracks so far

        logic.update();
        logic.update();
        assertEquals(1, logic.getObjectCount()); // still building (3 frames)

        logic.update(); // build completes on the 3rd frame
        assertEquals(2, logic.getObjectCount());

        var produced = logic.getObjects().stream()
                .filter(o -> o.getTemplate() == soldier)
                .findFirst()
                .orElseThrow();
        assertEquals(usa, produced.getPlayerIndex());
        assertEquals(50f, produced.getBody().getMaxHealth(), 1e-6f);
    }

    @Test
    void queueDrainsInOrderOverTime() {
        production().queue(soldier);
        production().queue(soldier);
        for (int i = 0; i < 6; i++) {
            logic.update();
        }
        // Both soldiers built (3 frames each, sequential) -> barracks + 2 soldiers.
        assertEquals(3, logic.getObjectCount());
        assertFalse(production().isProducing());
    }

    @Test
    void buildCostAndTimeLoadFromIni() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        RtsTemplate.register(new ThingTemplateLoader(thingFactory)).load("""
                Object
                  Name = Tank
                  BuildCost = 200
                  BuildTime = 2.0
                  ActiveBody
                    MaxHealth = 100
                  End
                End
                """, "units.duke");
        var tank = (RtsTemplate) thingFactory.findTemplate("Tank");
        assertEquals(200, tank.buildCost());
        assertEquals(60, tank.buildTimeFrames()); // 2.0s * 30 fps
    }
}
