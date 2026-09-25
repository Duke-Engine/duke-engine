package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.event.WeaponFired;

/**
 * A weapon's wind-up before it fires, and a reach kept once its attack has begun — the reference's {@code
 * PreAttackDelay}, {@code PreAttackType} and {@code LeechRangeWeapon}: a SCUD launcher's half second before every
 * shot, a knife's before the first at a man, the Scud Storm's before the first of its clip — and the knife's reach,
 * kept while its victim walks off.
 */
class WindUpTest {

    private static Weapon weapon(float reach, int reload, int clip, int clipReload, int windUp,
            Weapon.PreAttack when, boolean keepsReach) {
        return new Weapon("Gun", 1f, reach, reload, reload, DamageType.NORMAL, 0f, true, List.of(), clip, clipReload,
                true, DeathType.NORMAL, List.of(), List.of(), 0f, 0f, false, -180f, 180f, 0f, windUp, when,
                keepsReach);
    }

    private record Field(CombatTest.CombatLogic logic, GameObject shooter, int them, List<Integer> shots,
            List<WeaponStatus> statuses) {

        GameObject enemy(float x, float y) {
            var thing = logic.createObject(logic.getThingFactory().findTemplate("Target"));
            thing.setPlayerIndex(them);
            thing.setPosition(new Coord3D(x, y, 0f));
            return thing;
        }

        /** Runs frames {@code from} to {@code to} (not included), noting the frames it fired and its slot's status. */
        void run(int from, int to) {
            for (int frame = from; frame < to; frame++) {
                logic.update();
                int now = frame;
                logic.drainEvents().stream().filter(event -> event instanceof WeaponFired)
                        .forEach(event -> shots.add(now));
                statuses.add(shooter.findModule(WeaponUpdate.class).slotsNow(now).getFirst().status());
            }
        }
    }

    private static Field field(Weapon gun) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Shooter").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), List.of(new WeaponSlot("Gun"))))))
                .build());
        factory.addTemplate(RtsTemplate.named("Target").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(1_000_000f)).module(new MoveUpdate.Data(20f)).build());
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        logic.addWeapons(List.of(gun));
        var us = logic.getPlayerList().addPlayer("Us");
        var them = logic.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        var shooter = logic.createObject(factory.findTemplate("Shooter"));
        shooter.setPlayerIndex(us.getIndex());
        shooter.setPosition(new Coord3D(100f, 100f, 0f));
        return new Field(logic, shooter, them.getIndex(), new ArrayList<>(), new ArrayList<>());
    }

    @Test
    void beforeEveryShotTheShotComesAsItsWindUpEnds() {
        var field = field(weapon(50f, 0, 1, 300, 15, Weapon.PreAttack.PER_SHOT, false));
        field.enemy(120f, 100f);
        field.run(0, 400);
        assertEquals(List.of(15, 330), field.shots(), "15 frames after it is ready, and 315 after that");
        assertEquals(WeaponStatus.PRE_ATTACK, field.statuses().get(0), "winding up from its first frame");
        assertEquals(WeaponStatus.PRE_ATTACK, field.statuses().get(14));
        assertEquals(15, field.statuses().stream().limit(15).filter(WeaponStatus.PRE_ATTACK::equals).count());
    }

    @Test
    void beforeTheFirstShotAtATargetOnlyAndAgainAtTheNext() {
        var field = field(weapon(50f, 0, 1, 40, 25, Weapon.PreAttack.PER_ATTACK, false));
        var a = field.enemy(120f, 100f);
        field.run(0, 90);
        assertEquals(List.of(25, 65), field.shots(), "at A: 25 frames of wind-up, then its reload alone");

        field.shots().clear();
        a.getBody().setHealth(0f);
        field.enemy(100f, 120f);
        field.run(90, 200);
        assertEquals(130, field.shots().getFirst(), "at B, 25 frames of wind-up again once it is ready at 105");
        assertEquals(WeaponStatus.PRE_ATTACK, field.statuses().get(105));
        assertEquals(WeaponStatus.RELOADING, field.statuses().get(104));
    }

    @Test
    void beforeTheFirstOfAFullClip() {
        var field = field(weapon(50f, 5, 9, 60, 90, Weapon.PreAttack.PER_CLIP, false));
        field.enemy(120f, 100f);
        field.run(0, 281);
        assertEquals(List.of(90, 95, 100, 105, 110, 115, 120, 125, 130, 280), field.shots(),
                "90 frames, 9 shots at their delay, the refill, and 90 again");
    }

    @Test
    void aReachKeptHitsAVictimWalkingOffAndOneNotKeptWindsUpAgainOnceItCloses() {
        var kept = field(weapon(3f, 30, 0, 0, 25, Weapon.PreAttack.PER_ATTACK, true));
        var walker = kept.enemy(106f, 100f); // touching
        walker.getLocomotor().moveTo(new Coord3D(600f, 100f, 0f));
        kept.run(0, 26);
        assertEquals(List.of(25), kept.shots(), "hit as its wind-up ends");
        assertTrue(World.reachBetween(kept.shooter(), walker) >= 16f, "16 or more away by then");
        assertEquals(new Coord3D(100f, 100f, 0f), kept.shooter().getPosition(), "and it never moved");

        var lost = field(weapon(3f, 30, 0, 0, 25, Weapon.PreAttack.PER_ATTACK, false));
        var runner = lost.enemy(106f, 100f);
        runner.getLocomotor().moveTo(new Coord3D(600f, 100f, 0f));
        lost.run(0, 26);
        assertEquals(List.of(), lost.shots(), "out of its reach before the wind-up ended: nothing");

        runner.getLocomotor().stop();
        runner.setPosition(new Coord3D(106f, 100f, 0f)); // back within its reach at frame 26
        lost.run(26, 60);
        assertEquals(List.of(51), lost.shots(), "25 frames of wind-up from 0 once it closed");
    }
}
