package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

class AutoHealUpdateTest {

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
    private ThingTemplate template;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(RtsModules.withDefaults());
        // 30 health/sec at 30Hz == 1 health per frame.
        template = ThingTemplate.named("Regenerator")
                .module(new ActiveBody.Data(100f))
                .module(new AutoHealUpdate.Data(30f))
                .build();
        thingFactory.addTemplate(template);
        logic = new TestLogic(thingFactory);
        logic.init();
    }

    @Test
    void woundedUnitRegeneratesOverTime() {
        GameObject unit = logic.createObject(template);
        unit.getBody().damage(50f);
        assertEquals(50f, unit.getBody().getHealth(), 1e-4f);

        for (int i = 0; i < 30; i++) {
            logic.update();
        }
        assertEquals(80f, unit.getBody().getHealth(), 1e-4f); // +30 over one second
    }

    @Test
    void healingStopsAtMaxHealth() {
        GameObject unit = logic.createObject(template);
        unit.getBody().damage(10f);
        for (int i = 0; i < 60; i++) {
            logic.update(); // would add 60, but caps at max
        }
        assertEquals(100f, unit.getBody().getHealth(), 1e-4f);
    }

    @Test
    void deadUnitIsNotRevived() {
        GameObject unit = logic.createObject(template);
        unit.getBody().damage(999f);
        assertTrue(unit.isEffectivelyDead());

        for (int i = 0; i < 30; i++) {
            logic.update();
        }
        assertEquals(0f, unit.getBody().getHealth(), 1e-4f);
        assertTrue(unit.isEffectivelyDead());
    }
}
