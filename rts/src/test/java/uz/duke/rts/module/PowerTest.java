package uz.duke.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

class PowerTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private ThingFactory thingFactory;
    private ThingTemplate factoryTemplate;
    private ThingTemplate powerPlant;
    private ThingTemplate soldier;
    private int usa;

    @BeforeEach
    void setUp() {
        thingFactory = new ThingFactory(RtsModules.withDefaults());
        soldier = ThingTemplate.named("Soldier")
                .module("ActiveBody", new ActiveBody.Data(50f))
                .buildCost(100)
                .buildTimeFrames(3)
                .build();
        factoryTemplate = ThingTemplate.named("Factory")
                .module("ActiveBody", new ActiveBody.Data(400f))
                .module("ProductionUpdate", new ProductionUpdate.Data())
                .module("PowerModule", new PowerModule.Data(0, 8)) // consumes 8
                .build();
        powerPlant = ThingTemplate.named("PowerPlant")
                .module("ActiveBody", new ActiveBody.Data(300f))
                .module("PowerModule", new PowerModule.Data(10, 0)) // produces 10
                .build();
        thingFactory.addTemplate(soldier);
        thingFactory.addTemplate(factoryTemplate);
        thingFactory.addTemplate(powerPlant);

        logic = new TestLogic(thingFactory);
        logic.init();
        usa = logic.getPlayerList().addPlayer("USA").getIndex();
        logic.getPlayerList().getPlayer(usa).deposit(500);
    }

    private GameObject spawn(ThingTemplate template) {
        var o = logic.createObject(template);
        o.setPlayerIndex(usa);
        o.setPosition(Coord3D.ZERO);
        return o;
    }

    @Test
    void surplusReflectsProductionAndConsumption() {
        spawn(factoryTemplate);
        assertEquals(-8, PowerGrid.surplus(logic, usa));
        assertFalse(PowerGrid.isPowered(logic, usa));

        spawn(powerPlant);
        assertEquals(2, PowerGrid.surplus(logic, usa));
        assertTrue(PowerGrid.isPowered(logic, usa));
    }

    @Test
    void productionStallsWhenUnderpowered() {
        var factory = spawn(factoryTemplate); // consumes 8, nothing produces -> unpowered
        factory.findModule(ProductionUpdate.class).queue(soldier);

        for (int i = 0; i < 10; i++) {
            logic.update();
        }
        // No power -> nothing built; the factory still holds the job.
        assertEquals(1, logic.getObjectCount());
        assertTrue(factory.findModule(ProductionUpdate.class).isProducing());
    }

    @Test
    void productionResumesOncePowered() {
        var factory = spawn(factoryTemplate);
        factory.findModule(ProductionUpdate.class).queue(soldier);
        logic.update();
        logic.update(); // stalled, unpowered

        spawn(powerPlant); // now powered
        for (int i = 0; i < 3; i++) {
            logic.update();
        }
        assertEquals(3, logic.getObjectCount()); // factory + plant + built soldier
    }
}
