package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.Shot;
import uz.dukeengine.combat.module.ShotListener;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;

/**
 * The reference's {@code ShareWeaponReloadTime} done by a game's module: each shot's wait handed to the other slots
 * ({@code Weapon::reloadWithBonus} and {@code privateFireWeapon} setting every slot's next shot) — so a gun whose one
 * round empties its clip keeps the cannon beside it from firing until the gun has reloaded.
 */
class SharedReloadTest {

    /** What a game writes for a set that shares: the wait a shot leaves, given to every other slot. */
    private static final class ShareReload extends Module implements ShotListener {
        ShareReload(GameObject owner) {
            super(owner);
        }

        @Override
        public void onSpent(Shot shot, int frames, boolean emptied) {
            getOwner().findModule(WeaponUpdate.class).delaySlots(shot.slot(), frames);
        }
    }

    /** The frames each weapon fired on over {@code frames} frames, as {@code weapon@frame}. */
    private static List<String> shots(boolean shared, int frames) {
        // The gun: one round, a 30-frame reload. The cannon: weaker, every 2 frames.
        var gun = new Weapon("Gun", 10f, 60f, 2, 0, DamageType.NORMAL, 0f, true, List.of(), 1, 30, true);
        var cannon = new Weapon("Cannon", 5f, 60f, 2, 0, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true);
        var sets = List.of(new WeaponSet(List.of(), List.of(new WeaponSlot("Gun"), new WeaponSlot("Cannon"))));
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Shooter").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(WeaponUpdate.Data.sets(sets)).build());
        factory.addTemplate(RtsTemplate.named("Wall").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(1_000_000f)).build());
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        logic.addWeapons(List.of(gun, cannon));
        var us = logic.getPlayerList().addPlayer("Us");
        var them = logic.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        var shooter = logic.spawn(factory.findTemplate("Shooter"), Coord3D.ZERO, us.getIndex());
        if (shared) {
            shooter.addModule(new ShareReload(shooter));
        }
        logic.spawn(factory.findTemplate("Wall"), new Coord3D(20f, 0f, 0f), them.getIndex());

        var fired = new ArrayList<String>();
        for (int frame = 0; frame < frames; frame++) {
            logic.update();
            for (var event : logic.drainEvents()) {
                if (event instanceof WeaponFired shot) {
                    fired.add(shot.weapon() + "@" + shot.frame());
                }
            }
        }
        int first = Integer.parseInt(fired.getFirst().split("@")[1]);
        return fired.stream().map(shot -> {
            var parts = shot.split("@");
            return parts[0] + "@" + (Integer.parseInt(parts[1]) - first);
        }).toList();
    }

    @Test
    void slotZeroEmptyingItsClipKeepsSlotOneFromFiringUntilItHasReloaded() {
        var shots = shots(true, 45);
        assertEquals("Gun@0", shots.getFirst());
        assertEquals("Gun@30", shots.get(1), "nothing between: the cannon waits out the gun's reload");
    }

    @Test
    void withoutItTheSlotsReloadApart() {
        var shots = shots(false, 45);
        assertEquals("Gun@0", shots.getFirst());
        assertEquals("Cannon", shots.get(1).split("@")[0], "the cannon fires while the gun reloads");
    }
}
