package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.BodyModule;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.TargetRule;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.rts.RtsTemplate;

/**
 * A body asked what a blow is worth by the weapon that would deal it: a building that shrugs off a rifle's kind of
 * damage is still worth the shot of the one rifle allowed at garrisons (the reference's {@code
 * AllowAttackGarrisonedBldgs}), and a unit weighing its weapons hears the weapon's name.
 */
class WeaponWeighedByNameTest {

    /** A garrisoned building, immune to {@code SNIPER} but to the rifle named {@code Kell}. */
    record Garrison() implements ModuleData {
    }

    static final class GarrisonBody extends BodyModule {
        private float health = 1_000_000f;

        GarrisonBody(GameObject owner, Garrison data) {
            super(owner);
        }

        @Override
        public float getHealth() {
            return health;
        }

        @Override
        public float getMaxHealth() {
            return 1_000_000f;
        }

        @Override
        public void setHealth(float health) {
            this.health = health;
        }

        @Override
        public void damage(float amount, DamageType type) {
            health -= estimateDamage(amount, type);
        }

        @Override
        public void heal(float amount) {
            health += amount;
        }

        @Override
        public float estimateDamage(float amount, DamageType type) {
            return type.name().equals("SNIPER") ? 0f : amount;
        }

        @Override
        public float estimateDamage(float amount, DamageType type, String weapon) {
            return weapon.equals("Kell") ? 1f : estimateDamage(amount, type);
        }
    }

    private static Weapon rifle(String name) {
        return new Weapon(name, 50f, 60f, 5, 0, DamageType.of("SNIPER"), 0f, true, List.of(), 0, 0, true, null);
    }

    private static List<String> firedAtTheGarrison(Weapon... arms) {
        var factory = new ThingFactory(RtsModules.withDefaults().register(Garrison.class, GarrisonBody::new));
        var slots = java.util.Arrays.stream(arms).map(one -> new WeaponSlot(one.name())).toList();
        factory.addTemplate(RtsTemplate.named("Sniper").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), slots)))).build());
        factory.addTemplate(RtsTemplate.named("Bunker").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new Garrison()).build());
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        logic.setTargetRules(List.of(new TargetRule(List.of(), false, List.of())));
        logic.addWeapons(List.of(arms));
        var us = logic.getPlayerList().addPlayer("Us");
        var them = logic.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        var sniper = logic.createObject(factory.findTemplate("Sniper"));
        sniper.setPlayerIndex(us.getIndex());
        sniper.setPosition(Coord3D.ZERO);
        var bunker = logic.createObject(factory.findTemplate("Bunker"));
        bunker.setPlayerIndex(them.getIndex());
        bunker.setPosition(new Coord3D(20f, 0f, 0f));
        var fired = new ArrayList<String>();
        for (int frame = 0; frame < 3; frame++) {
            logic.update();
            for (var event : logic.drainEvents()) {
                if (event instanceof WeaponFired shot) {
                    fired.add(shot.weapon());
                }
            }
        }
        return fired;
    }

    @Test
    void theWeaponAllowedAtGarrisonsIsWorthItsShotWhereItsKindIsWorthNothing() {
        assertEquals(List.of("Pathfinder"), firedAtTheGarrison(rifle("Pathfinder"), rifle("Ranger")),
                "two rifles worth nothing: the first, as a unit falls back to when none is worth a shot");
        assertEquals(List.of("Kell"), firedAtTheGarrison(rifle("Pathfinder"), rifle("Kell")),
                "the one its body names is worth something, so it is chosen over the slot its armour stops");
    }
}
