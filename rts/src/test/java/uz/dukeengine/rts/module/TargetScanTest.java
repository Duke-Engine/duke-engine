package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;
import uz.dukeengine.combat.module.WeaponUpdate;

/**
 * How often a weapon with no target looks for one — the reference's mood check: each thing on its own clock, moved on
 * by its rate at every look, the first move by up to half the rate more, drawn from the world's random numbers; and a
 * thing that falls idle looking at once. Every frame by default, as always.
 */
class TargetScanTest {

    private CombatTest.CombatLogic world;
    private int us;
    private int them;

    private void world(int scanFrames, long seed) {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Tank").module(new ActiveBody.Data(400f))
                .module(new MoveUpdate.Data(30f))
                .module(new WeaponUpdate.Data(10f, 100f, 15, DamageType.NORMAL)).build());
        factory.addTemplate(RtsTemplate.named("Scout").module(new ActiveBody.Data(400f))
                .module(new WeaponUpdate.Data(10f, 100f, 15, DamageType.NORMAL).targetScanFrames(20)).build());
        factory.addTemplate(RtsTemplate.named("Wall").module(new ActiveBody.Data(1_000_000f)).build());
        world = new CombatTest.CombatLogic(factory);
        world.init();
        world.setRandomSeed(seed);
        world.setTargetScanFrames(scanFrames);
        var one = world.getPlayerList().addPlayer("Us");
        var two = world.getPlayerList().addPlayer("Them");
        one.setRelationshipTo(two, Relationship.ENEMIES);
        two.setRelationshipTo(one, Relationship.ENEMIES);
        us = one.getIndex();
        them = two.getIndex();
    }

    private void world(int scanFrames) {
        world(scanFrames, uz.dukeengine.core.GameLogic.DEFAULT_RANDOM_SEED);
    }

    private GameObject spawn(String template, float x, float y, int player) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, y, 0f), player);
    }

    private static uz.dukeengine.core.thing.ObjectId targetOf(GameObject thing) {
        return thing.findModule(WeaponUpdate.class).getTarget();
    }

    /**
     * The frame of the world each of {@code tanks} first took an enemy, looking from the first frame with nothing in
     * reach and an enemy put beside each once that frame has run; -1 for none within {@code frames}.
     */
    private List<Integer> framesTaken(List<GameObject> tanks, int frames) {
        world.update(); // every tank's first look, at nothing
        for (var tank : tanks) {
            spawn("Wall", tank.getPosition().x() + 50f, tank.getPosition().y(), them);
        }
        var taken = new ArrayList<Integer>();
        tanks.forEach(tank -> taken.add(-1));
        for (int frame = 0; frame < frames; frame++) {
            int now = world.getFrame();
            world.update();
            for (int i = 0; i < tanks.size(); i++) {
                if (taken.get(i) < 0 && targetOf(tanks.get(i)) != null) {
                    taken.set(i, now);
                }
            }
        }
        return taken;
    }

    @Test
    void byDefaultAnEnemyInRangeIsTakenTheNextFrame() {
        world(1);
        var tank = spawn("Tank", 100f, 100f, us);
        assertEquals(List.of(1), framesTaken(List.of(tank), 30), "the first frame after it came");
    }

    /** Rate 60: the first look on the first frame, the next 60 to 90 frames on — never sooner, never later. */
    @Test
    void aThingThatHasJustLookedTakesAnEnemyComingIntoReachAtItsNextLook() {
        world(60);
        var tank = spawn("Tank", 100f, 100f, us);
        int taken = framesTaken(List.of(tank), 120).getFirst();
        assertTrue(taken >= 60 && taken <= 90, "its next look, 60 to 90 frames after its first: " + taken);
    }

    /**
     * The asker's measure: at a rate of 60, 65 things given no order with an enemy standing in reach each take it
     * within 61 frames of falling idle — the first frame, a new thing falling idle at once — whatever their ids.
     */
    @Test
    void everyOneOfSixtyFiveThingsFallingIdleWithAnEnemyInReachTakesIt() {
        world(60);
        var tanks = new ArrayList<GameObject>();
        for (int i = 0; i < 65; i++) {
            float y = 100f + i * 250f;
            tanks.add(spawn("Tank", 100f, y, us));
            spawn("Wall", 150f, y, them); // ids interleaved: the tanks' odd, 1 to 129
        }
        assertEquals(129, tanks.getLast().getId().value());
        for (int frame = 0; frame < 61; frame++) {
            world.update();
        }
        for (var tank : tanks) {
            assertTrue(targetOf(tank) != null, "tank " + tank.getId().value() + " took its enemy");
        }
    }

    /** Its order done — a move — it looks at once, whatever its clock said. */
    @Test
    void aThingWhoseMoveIsDoneLooksAtOnce() {
        world(600);
        var tank = spawn("Tank", 100f, 100f, us);
        world.update(); // its first look, at nothing: the next twenty seconds and more away
        spawn("Wall", 350f, 100f, them);
        tank.findModule(MoveUpdate.class).moveTo(new Coord3D(300f, 100f, 0f));
        int stood = -1;
        int taken = -1;
        for (int frame = 0; frame < 590 && taken < 0; frame++) {
            int now = world.getFrame();
            world.update();
            if (stood < 0 && !tank.getLocomotor().isMoving()) {
                stood = now;
            }
            if (targetOf(tank) != null) {
                taken = now;
            }
        }
        assertTrue(stood > 0 && taken >= stood && taken <= stood + 1,
                "taken as it stood, its clock not yet due: stood " + stood + ", took " + taken);
    }

    /** A template's own rate over the world's: the scout's 20 frames where the world's is 600. */
    @Test
    void aTemplatesOwnRateIsItsClockOverTheWorlds() {
        world(600);
        var scout = spawn("Scout", 100f, 100f, us);
        int taken = framesTaken(List.of(scout), 60).getFirst();
        assertTrue(taken >= 20 && taken <= 30, "its own 20, up to 10 more: " + taken);
    }

    /** The offset drawn from the world's numbers: the same seed, the same frames; another seed, others. */
    @Test
    void twoWorldsFromTheSameSeedLookOnTheSameFrames() {
        var once = framesFrom(42L);
        assertEquals(once, framesFrom(42L));
        assertNotEquals(once, framesFrom(43L), "and they do differ, or this would prove nothing");
    }

    private List<Integer> framesFrom(long seed) {
        world(60, seed);
        var tanks = new ArrayList<GameObject>();
        for (int i = 0; i < 20; i++) {
            tanks.add(spawn("Tank", 100f, 100f + i * 250f, us));
        }
        return framesTaken(tanks, 100);
    }
}
