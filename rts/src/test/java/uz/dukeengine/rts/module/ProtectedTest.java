package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.combat.module.WeaponUpdate;

/** An ejected pilot nobody targets for sixty frames: not acquired, not ordered at, and then a target as any. */
class ProtectedTest {

    @Test
    void aPilotProtectedUntilFrameSixtyOneIsLeftAloneUntilThen() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        var tankType = ThingTemplate.named("Tank").module(new ActiveBody.Data(400f))
                .module(new WeaponUpdate.Data(20f, 100f, 30, DamageType.NORMAL, 0f)).build();
        var pilotType = ThingTemplate.named("Pilot").module(new ActiveBody.Data(100f)).build();
        factory.addTemplate(tankType);
        factory.addTemplate(pilotType);
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        var tank = logic.spawn(tankType, new Coord3D(0f, 0f, 0f), red.getIndex());
        var pilot = logic.spawn(pilotType, new Coord3D(10f, 0f, 0f), blue.getIndex());
        pilot.setTargetableFrom(61);
        var gun = tank.findModule(WeaponUpdate.class);

        while (logic.getFrame() < 61) {
            assertFalse(gun.attack(pilot.getId()), "an attack order on him is refused at frame " + logic.getFrame());
            logic.update();
            assertFalse(gun.isAttacking(), "not acquired at frame " + (logic.getFrame() - 1));
        }
        assertEquals(100f, pilot.getBody().getHealth(), "untouched");

        logic.update(); // frame 61
        assertTrue(gun.isAttacking(), "from frame 61 a target as any");
        assertTrue(gun.attack(pilot.getId()));
    }
}
