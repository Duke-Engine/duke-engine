package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.DeathType;
import uz.dukeengine.core.module.FlyUpdate;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.rts.event.WeaponFired;

/**
 * A weapon's least range — the reference's {@code MinimumAttackRange}: a SCUD launcher, 200 of its 350, fires at
 * nothing nearer, backs away from what is, and takes no such target by itself; a thing that cannot move lets one go.
 * Its weapons are stood still for, as a launcher's are, so where it fires from is where it stands.
 */
class LeastRangeTest {

    private static Weapon weapon(String name, float reach, float least) {
        return new Weapon(name, 10f, reach, 10, 10, DamageType.NORMAL, 0f, false, List.of(), 0, 0, true,
                DeathType.NORMAL, List.of(), List.of(), 0f, 0f, false, -180f, 180f, least);
    }

    private record Field(CombatTest.CombatLogic logic, int us, int them) {

        GameObject put(String template, int side, float x, float y) {
            var thing = logic.createObject(logic.getThingFactory().findTemplate(template));
            thing.setPlayerIndex(side);
            thing.setPosition(new Coord3D(x, y, 0f));
            return thing;
        }

        /** The frames run until the first shot, or -1 within {@code most}. */
        int untilItFires(int most) {
            for (int frame = 0; frame < most; frame++) {
                logic.update();
                if (logic.drainEvents().stream().anyMatch(event -> event instanceof WeaponFired)) {
                    return frame;
                }
            }
            return -1;
        }
    }

    private static Field field(ModuleData... shooter) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        var scud = RtsTemplate.named("Shooter").geometry(new Geometry.Cylinder(5f, 6f))
                .module(new ActiveBody.Data(100f));
        for (var data : shooter) {
            scud.module(data);
        }
        factory.addTemplate(scud.build());
        factory.addTemplate(RtsTemplate.named("Tank").geometry(new Geometry.Cylinder(5f, 6f))
                .module(new ActiveBody.Data(1_000_000f)).build());
        var logic = new CombatTest.CombatLogic(factory);
        logic.init();
        logic.setPathGrid(new PathGrid(120, 120));
        logic.addWeapons(List.of(weapon("Scud", 350f, 200f), weapon("Rockets", 320f, 100f),
                weapon("Cannon", 300f, 100f), weapon("Fists", 10f, 0f)));
        var us = logic.getPlayerList().addPlayer("Us");
        var them = logic.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        return new Field(logic, us.getIndex(), them.getIndex());
    }

    private static WeaponUpdate.Data armed(String weapon) {
        return WeaponUpdate.Data.sets(List.of(new WeaponSet(List.of(), List.of(new WeaponSlot(weapon)))));
    }

    private static final MoveUpdate.Data WHEELS = new MoveUpdate.Data(30f);

    @Test
    void orderedAtATargetTooNearItBacksStraightAwayAndFiresFromJustPastItsLeastRange() {
        var field = field(WHEELS, armed("Scud"), new PursueUpdate.Data(0f, 0));
        var scud = field.put("Shooter", field.us(), 600f, 605f);
        var tank = field.put("Tank", field.them(), 490f, 605f); // 100 off, outline to outline
        scud.findModule(WeaponUpdate.class).attack(tank.getId());

        int fired = field.untilItFires(900);
        assertTrue(fired > 0, "no shot from 100 off, then one");
        float gap = World.reachBetween(scud, tank);
        assertTrue(gap >= 200f && gap <= 210f, "fired standing 200 to 210 from it: " + gap);
        assertEquals(605f, scud.getPosition().y(), 1f, "straight away from it");
        assertTrue(scud.getPosition().x() > 600f);
    }

    @Test
    void idleItTakesNoEnemyInsideItsLeastRangeAndTakesOneBeyondIt() {
        var field = field(WHEELS, armed("Scud"));
        var scud = field.put("Shooter", field.us(), 600f, 605f);
        field.put("Tank", field.them(), 490f, 605f); // 100 off
        var far = field.put("Tank", field.them(), 600f, 345f); // 250 off
        field.logic().update();
        field.logic().update();
        assertEquals(far.getId(), scud.findModule(WeaponUpdate.class).getTarget(), "the one 250 off, not 100");
    }

    /** Outlines that overlap are no distance apart, never less: in reach of fists, and too near for a launcher. */
    @Test
    void aTargetItsOutlineOverlapsIsInReachOfAWeaponWithNoLeastRangeAndTooNearForOneWithAny() {
        var fists = field(armed("Fists"));
        var brawler = fists.put("Shooter", fists.us(), 600f, 605f);
        var tank = fists.put("Tank", fists.them(), 606f, 605f); // outlines 4 into each other
        assertTrue(fists.untilItFires(30) >= 0, "it takes what it overlaps by itself, and fires");
        var weapon = brawler.findModule(WeaponUpdate.class);
        assertEquals(tank.getId(), weapon.getTarget());
        assertTrue(weapon.isInRange(tank));

        var scud = field(armed("Scud"));
        var launcher = scud.put("Shooter", scud.us(), 600f, 605f);
        var near = scud.put("Tank", scud.them(), 606f, 605f);
        assertTrue(launcher.findModule(WeaponUpdate.class).isTooNear(near));
    }

    @Test
    void aThingThatCannotMoveLetsGoOfATargetThatComesInsideAndIsRefusedOneThere() {
        var field = field(armed("Cannon"));
        var tower = field.put("Shooter", field.us(), 600f, 605f);
        var walker = field.put("Tank", field.them(), 440f, 605f); // 150 off
        field.logic().update();
        var weapon = tower.findModule(WeaponUpdate.class);
        assertEquals(walker.getId(), weapon.getTarget());

        var other = field.put("Tank", field.them(), 600f, 395f); // 200 off
        walker.setPosition(new Coord3D(540f, 605f, 0f)); // 50 off
        field.logic().update();
        field.logic().update();
        assertEquals(other.getId(), weapon.getTarget(), "let go, and the one 200 off taken");

        weapon.holdFire();
        assertFalse(weapon.attack(walker.getId()), "an order at one 50 off is refused");
        assertNull(weapon.getTarget(), "and nothing taken instead");
    }

    @Test
    void aFlierTooNearFliesOnToHalfwayBetweenItsRangesOrPastTheTargetAndFiresFromThere() {
        var hover = new FlyUpdate.Data(FlyUpdate.Kind.HOVERING, 60f, 0f, 0f, 0f, 0f, 50f, 0f);
        // (320 + 97.5) / 2: halfway between its reach and its least range less a quarter cell, as the reference has it
        float halfway = (320f + 100f - 2.5f) / 2f + 10f;

        var away = field(hover, armed("Rockets"), new PursueUpdate.Data(0f, 0));
        var flier = away.put("Shooter", away.us(), 555f, 605f);
        flier.setOrientation(0f); // flying away, east
        var tank = away.put("Tank", away.them(), 500f, 605f); // 45 off
        flier.findModule(WeaponUpdate.class).attack(tank.getId());
        assertTrue(away.untilItFires(900) > 0);
        assertEquals(500f + halfway, flier.getPosition().x(), 2f, "on to 208.75 from it, and fired there");

        var toward = field(hover, armed("Rockets"), new PursueUpdate.Data(0f, 0));
        var over = toward.put("Shooter", toward.us(), 555f, 605f);
        over.setOrientation((float) Math.PI); // flying toward it, west
        var target = toward.put("Tank", toward.them(), 500f, 605f);
        over.findModule(WeaponUpdate.class).attack(target.getId());
        assertTrue(toward.untilItFires(900) > 0);
        assertEquals(500f - halfway, over.getPosition().x(), 2f, "past it, and fired from the far side");
    }
}
