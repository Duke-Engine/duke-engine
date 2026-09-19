package uz.duke.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.module.UpdateModule;

class ThingSystemTest {

    /** A test update module that translates its owner by a fixed delta each frame. */
    static final class MoverUpdate extends UpdateModule {
        record Data(Coord3D delta) implements ModuleData {
        }

        private final Coord3D delta;

        MoverUpdate(GameObject owner, Data data) {
            super(owner);
            this.delta = data.delta();
        }

        @Override
        public void update() {
            var owner = getOwner();
            owner.setPosition(owner.getPosition().add(delta));
        }
    }

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private ThingTemplate tankTemplate;

    @BeforeEach
    void setUp() {
        var moduleFactory = ModuleFactory.withDefaults();
        moduleFactory.register(MoverUpdate.Data.class, MoverUpdate::new);
        var thingFactory = new ThingFactory(moduleFactory);

        tankTemplate = ThingTemplate.named("TestTank")
                .displayName("Test Tank")
                .kindOf(Kind.of("VEHICLE"), Kind.of("SELECTABLE"), Kind.of("CAN_ATTACK"))
                .module(new ActiveBody.Data(100f))
                .module(new MoverUpdate.Data(new Coord3D(1f, 0f, 0f)))
                .build();
        thingFactory.addTemplate(tankTemplate);

        logic = new TestLogic(thingFactory);
        logic.init();
    }

    @Test
    void createObjectAssignsSequentialIds() {
        var first = logic.createObject(tankTemplate);
        var second = logic.createObject(tankTemplate);
        assertEquals(1, first.getId().value());
        assertEquals(2, second.getId().value());
        assertEquals(2, logic.getObjectCount());
    }

    @Test
    void objectInheritsTemplateClassificationAndBody() {
        var tank = logic.createObject(tankTemplate);
        assertTrue(tank.isKindOf(Kind.of("VEHICLE")));
        assertFalse(tank.isKindOf(Kind.of("STRUCTURE")));
        assertSame(tankTemplate, tank.getTemplate());
        assertEquals(100f, tank.getBody().getMaxHealth(), 1e-6f);
        assertEquals(100f, tank.getBody().getHealth(), 1e-6f);
    }

    @Test
    void updateModulesRunEachFrame() {
        var tank = logic.createObject(tankTemplate);
        for (int i = 0; i < 5; i++) {
            logic.update();
        }
        assertEquals(5, logic.getFrame());
        assertEquals(5f, tank.getPosition().x(), 1e-6f);
    }

    @Test
    void damageReducesHealthAndKills() {
        var tank = logic.createObject(tankTemplate);
        tank.getBody().damage(30f);
        assertEquals(70f, tank.getBody().getHealth(), 1e-6f);
        assertFalse(tank.isEffectivelyDead());

        tank.getBody().damage(999f);
        assertEquals(0f, tank.getBody().getHealth(), 1e-6f);
        assertTrue(tank.isEffectivelyDead());
    }

    @Test
    void destroyedObjectsAreReaped() {
        var tank = logic.createObject(tankTemplate);
        assertEquals(1, logic.getObjectCount());

        logic.destroyObject(tank);
        logic.update(); // reap happens at the start of the frame
        assertEquals(0, logic.getObjectCount());
    }

    @Test
    void healClampsToMax() {
        var tank = logic.createObject(tankTemplate);
        tank.getBody().damage(50f);
        tank.getBody().heal(999f);
        assertEquals(100f, tank.getBody().getHealth(), 1e-6f);
    }
}
