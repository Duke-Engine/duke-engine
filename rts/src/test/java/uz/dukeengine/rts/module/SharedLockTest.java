package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * A weapon lock kept across a change of the set in use to a set that shares it — the reference's {@code
 * WeaponLockSharedAcrossSets}, read on the set arrived at ({@code WeaponSet::updateWeaponSet}); every other change
 * lets the lock go, as it always did.
 */
class SharedLockTest {

    private record Scene(CombatTest.CombatLogic logic, GameObject shooter, List<String> fired) {

        WeaponUpdate weapons() {
            return shooter.findModule(WeaponUpdate.class);
        }

        /** The weapons it fired over {@code frames} frames, each once. */
        List<String> run(int frames) {
            fired.clear();
            for (int frame = 0; frame < frames; frame++) {
                logic.update();
                for (var event : logic.drainEvents()) {
                    if (event instanceof WeaponFired shot) {
                        fired.add(shot.weapon());
                    }
                }
            }
            return fired.stream().distinct().toList();
        }
    }

    private static Weapon weapon(String name) {
        return new Weapon(name, 10f, 60f, 2, 0, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true, null);
    }

    /**
     * Three sets of two slots: the plain one, one for UPGRADED that shares the lock, one for UPGRADED and VETERAN
     * that does not.
     */
    private static Scene scene() {
        var sets = List.of(
                new WeaponSet(List.of(), List.of(new WeaponSlot("Gun"), new WeaponSlot("Cannon"))),
                new WeaponSet(List.of("UPGRADED"), List.of(new WeaponSlot("GunTwo"), new WeaponSlot("CannonTwo")),
                        true),
                new WeaponSet(List.of("UPGRADED", "VETERAN"),
                        List.of(new WeaponSlot("GunThree"), new WeaponSlot("CannonThree"))));
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Shooter").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(WeaponUpdate.Data.sets(sets)).build());
        factory.addTemplate(RtsTemplate.named("Wall").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(1_000_000f)).build());
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        logic.addWeapons(List.of(weapon("Gun"), weapon("Cannon"), weapon("GunTwo"), weapon("CannonTwo"),
                weapon("GunThree"), weapon("CannonThree")));
        var us = logic.getPlayerList().addPlayer("Us");
        var them = logic.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        var shooter = logic.spawn(factory.findTemplate("Shooter"), Coord3D.ZERO, us.getIndex());
        logic.spawn(factory.findTemplate("Wall"), new Coord3D(20f, 0f, 0f), them.getIndex());
        return new Scene(logic, shooter, new ArrayList<>());
    }

    @Test
    void aChangeToASetThatSharesTheLockKeepsItAndItsSlotAndAChangeToOneThatDoesNotLetsItGo() {
        var scene = scene();
        var weapons = scene.weapons();
        scene.run(1);
        assertTrue(weapons.lock(1, WeaponUpdate.Lock.PERMANENTLY));
        assertEquals(List.of("Cannon"), scene.run(10), "locked to the plain set's second slot");

        scene.shooter().setCondition("UPGRADED");
        assertEquals(List.of("CannonTwo"), scene.run(10), "the upgraded set's second slot, the lock kept");
        assertEquals(1, weapons.getLockedSlot());

        scene.shooter().setCondition("VETERAN");
        scene.run(1);
        assertEquals(-1, weapons.getLockedSlot(), "a set that says nothing lets it go, as ever");
    }

    @Test
    void theSetArrivedAtSaysNotTheSetLeft() {
        var scene = scene();
        var weapons = scene.weapons();
        scene.shooter().setCondition("UPGRADED");
        scene.run(1);
        assertTrue(weapons.lock(1, WeaponUpdate.Lock.PERMANENTLY));

        scene.shooter().clearCondition("UPGRADED");
        scene.run(1);
        assertEquals(-1, weapons.getLockedSlot(), "back to the plain set, which does not share it");
    }

    @Test
    void aLockUntilTheAttackIsOverIsKeptAcrossToASetThatSharesItToo() {
        var scene = scene();
        var weapons = scene.weapons();
        scene.run(1);
        assertTrue(weapons.lock(0, WeaponUpdate.Lock.TEMPORARILY));

        scene.shooter().setCondition("UPGRADED");
        scene.run(1);
        assertEquals(0, weapons.getLockedSlot());
    }
}
