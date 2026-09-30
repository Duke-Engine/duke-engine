package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.KeepsDead;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.combat.module.Shot;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponUpdate;

/** A hulk kept in the world while its death plays out is no target and takes no blast. */
class KeptDeadTargetTest {

    static final class Hulk extends Module implements KeepsDead {
        record Data() implements ModuleData {
        }

        Hulk(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean keepsDead() {
            return true;
        }
    }

    @Test
    void anAdjacentEnemyNeverAcquiresItAndABlastLeavesItAsItIs() {
        var factory = new ThingFactory(RtsModules.withDefaults()
                .register(Hulk.Data.class, (owner, data) -> new Hulk(owner)));
        var gunType = ThingTemplate.named("Gun").module(new ActiveBody.Data(400f))
                .module(new WeaponUpdate.Data(20f, 100f, 10, DamageType.NORMAL, 0f)).build();
        var tankType = ThingTemplate.named("Tank").module(new ActiveBody.Data(400f)).module(new Hulk.Data()).build();
        factory.addTemplate(gunType);
        factory.addTemplate(tankType);
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        var red = logic.getPlayerList().addPlayer("Red");
        var blue = logic.getPlayerList().addPlayer("Blue");
        red.setRelationshipTo(blue, Relationship.ENEMIES);
        blue.setRelationshipTo(red, Relationship.ENEMIES);
        var gun = logic.spawn(gunType, new Coord3D(0f, 0f, 0f), red.getIndex()).findModule(WeaponUpdate.class);
        var tank = logic.spawn(tankType, new Coord3D(10f, 0f, 0f), blue.getIndex());
        tank.getBody().damage(1000f);

        var attacked = new ArrayList<Boolean>();
        for (int frame = 0; frame < 60; frame++) {
            logic.update();
            attacked.add(gun.isAttacking());
        }
        var bomb = new Weapon("Bomb", 100f, 50f, 30, 30, DamageType.EXPLOSION, 50f, true, List.of(), 0, 0, true,
                DeathType.NORMAL);
        WeaponUpdate.land(logic, new Shot(gun.getOwner().getId(), red.getIndex(), bomb, 0, 100f), null,
                new Coord3D(10f, 0f, 0f), null);

        assertFalse(attacked.contains(true), "never acquired while it lay there");
        assertEquals(0f, tank.getBody().getHealth(), "a blast took nothing more from it");
        assertEquals(tank, logic.findObject(tank.getId()), "and it is still in the world");
    }
}
