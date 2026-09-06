package uz.duke.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectStatus;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

class StatusUpdateTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private TestLogic logic;
    private ThingTemplate template;

    @BeforeEach
    void setUp() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        // Speed 30/s == 1 unit/frame at 30Hz.
        template = ThingTemplate.named("Unit")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("AIUpdate", new AIUpdate.Data(30f))
                .module("StatusUpdate", new StatusUpdate.Data())
                .build();
        thingFactory.addTemplate(template);
        logic = new TestLogic(thingFactory);
        logic.init();
    }

    private GameObject spawnMover() {
        var unit = logic.createObject(template);
        unit.findModule(AIUpdate.class).moveTo(new Coord3D(100f, 0f, 0f));
        return unit;
    }

    @Test
    void disabledUnitStopsThenResumes() {
        var unit = spawnMover();
        for (int i = 0; i < 5; i++) {
            logic.update();
        }
        assertEquals(5f, unit.getPosition().x(), 1e-4f);

        unit.findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 3);
        assertTrue(unit.hasStatus(ObjectStatus.DISABLED));

        for (int i = 0; i < 3; i++) {
            logic.update();
        }
        assertEquals(5f, unit.getPosition().x(), 1e-4f); // frozen for 3 frames
        assertFalse(unit.hasStatus(ObjectStatus.DISABLED)); // status expired

        logic.update();
        assertEquals(6f, unit.getPosition().x(), 1e-4f); // moving again
    }

    @Test
    void slowedUnitMovesAtHalfSpeed() {
        var unit = spawnMover();
        unit.findModule(StatusUpdate.class).apply(ObjectStatus.SLOWED, 10);

        for (int i = 0; i < 10; i++) {
            logic.update();
        }
        // 10 frames at half of 1 unit/frame == 5 units.
        assertEquals(5f, unit.getPosition().x(), 1e-4f);
    }

    @Test
    void disabledUnitHoldsFire() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        var armed = ThingTemplate.named("Armed")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("WeaponUpdate", new WeaponUpdate.Data(25f, 10f, 2))
                .module("StatusUpdate", new StatusUpdate.Data())
                .build();
        thingFactory.addTemplate(armed);
        var combatLogic = new TestLogic(thingFactory);
        combatLogic.init();
        int me = combatLogic.getPlayerList().addPlayer("Me").getIndex();
        int foe = combatLogic.getPlayerList().addPlayer("Foe").getIndex();
        combatLogic.getPlayerList().getPlayer(me).setRelationshipTo(
                combatLogic.getPlayerList().getPlayer(foe), uz.duke.core.player.Relationship.ENEMIES);

        var shooter = combatLogic.createObject(armed);
        shooter.setPlayerIndex(me);
        shooter.setPosition(Coord3D.ZERO);
        var victim = combatLogic.createObject(armed);
        victim.setPlayerIndex(foe);
        victim.setPosition(new Coord3D(5f, 0f, 0f));

        shooter.findModule(StatusUpdate.class).apply(ObjectStatus.DISABLED, 100);

        for (int i = 0; i < 10; i++) {
            combatLogic.update();
        }
        assertEquals(100f, victim.getBody().getHealth(), 1e-4f); // shooter was disabled
    }
}
