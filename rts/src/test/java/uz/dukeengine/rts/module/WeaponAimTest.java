package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.combat.event.WeaponFired;
import uz.dukeengine.combat.module.Weapon;
import uz.dukeengine.combat.module.WeaponAim;
import uz.dukeengine.combat.module.WeaponSet;
import uz.dukeengine.combat.module.WeaponSlot;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * A weapon fires once its thing is aimed — the reference's attack waiting on a turret's turn or the body's
 * ({@code AIAttackAimAtTargetState}): a module says whether a slot is aimed, and the weapon says which slot it would
 * fire, so a game knows what to turn.
 */
class WeaponAimTest {

    /** Aimed 30 frames after it first sees the weapon's target: a turret at 180 degrees a second, a half round. */
    static final class Turning extends Module implements WeaponAim {
        private GameObject turningTo;
        private int since;

        Turning(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean aimed(int slot, Weapon weapon, GameObject target) {
            int now = getOwner().getWorld().getFrame();
            if (target != turningTo) {
                turningTo = target;
                since = now;
            }
            return now - since >= 30;
        }
    }

    private record Field(CombatTest.CombatLogic logic, GameObject shooter, int enemy, List<WeaponFired> shots) {

        GameObject enemy(float x) {
            var thing = logic.createObject(logic.getThingFactory().findTemplate("Target"));
            thing.setPlayerIndex(enemy);
            thing.setPosition(new Coord3D(x, 0f, 0f));
            return thing;
        }

        /** Frames run until the first shot, or -1 within {@code most}. */
        int firstShot(int most) {
            for (int frame = 0; frame < most; frame++) {
                logic.update();
                for (var event : logic.drainEvents()) {
                    if (event instanceof WeaponFired shot) {
                        shots.add(shot);
                    }
                }
                if (!shots.isEmpty()) {
                    return frame;
                }
            }
            return -1;
        }
    }

    private static Field field(boolean turns) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        var slots = List.of(new WeaponSlot("Cannon"), new WeaponSlot("Gun"));
        factory.addTemplate(RtsTemplate.named("Tank").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), slots)))).build());
        factory.addTemplate(RtsTemplate.named("Target").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(1_000f)).build());
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        logic.addWeapons(List.of(
                new Weapon("Cannon", 40f, 60f, 20, 0, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true),
                new Weapon("Gun", 5f, 60f, 5, 0, DamageType.NORMAL, 0f, true, List.of(), 0, 0, true)));
        var us = logic.getPlayerList().addPlayer("Us");
        var them = logic.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        var shooter = logic.createObject(factory.findTemplate("Tank"));
        shooter.setPlayerIndex(us.getIndex());
        shooter.setPosition(Coord3D.ZERO);
        if (turns) {
            shooter.addModule(new Turning(shooter));
        }
        return new Field(logic, shooter, them.getIndex(), new ArrayList<>());
    }

    @Test
    void aTargetItPicksItselfIsFirstFiredAtOnceItIsAimed() {
        var waits = field(true);
        waits.enemy(20f);
        assertEquals(30, waits.firstShot(60), "its first shot waits the 30 frames of the turn");

        var free = field(false);
        free.enemy(20f);
        assertEquals(0, free.firstShot(60), "with nothing to aim, the frame it is in range, as ever");
    }

    @Test
    void notAimedItKeepsLookingAndTakesTheNextEnemyWhenItsTargetIsKilled() {
        var field = field(true);
        var first = field.enemy(20f);
        var second = field.enemy(-25f);
        field.firstShot(10);
        var weapon = field.shooter().findModule(WeaponUpdate.class);
        assertEquals(first.getId(), weapon.getTarget(), "the nearer enemy, taken though it is not aimed at yet");

        first.getBody().setHealth(0f);
        field.logic().update();
        field.logic().update();
        assertEquals(second.getId(), weapon.getTarget(), "the next enemy in range, at its next look");
        assertTrue(field.shots().isEmpty(), "and nothing fired meanwhile");
    }

    @Test
    void theSlotItSaysItWouldFireIsTheSlotThatFires() {
        var field = field(false);
        var enemy = field.enemy(20f);
        int said = field.shooter().findModule(WeaponUpdate.class).slotFor(enemy);
        field.firstShot(5);
        assertEquals(0, said, "the cannon, for its damage");
        assertEquals(said, field.shots().getFirst().slot());
    }
}
