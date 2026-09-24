package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** Held: a thing kept where it is that still fights — SAGE's {@code DISABLED_HELD}. */
class HeldTest {

    private static final float RANGE = 50f;

    private static CombatTest.CombatLogic world() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(ThingTemplate.named("Tank")
                .module(new ActiveBody.Data(300f))
                .module(new MoveUpdate.Data(30f))
                .module(new WeaponUpdate.Data(40f, RANGE, 15, DamageType.NORMAL))
                .module(new PursueUpdate.Data(0f, 0))
                .build());
        var world = new CombatTest.CombatLogic(factory);
        world.init();
        int ours = world.getPlayerList().addPlayer("Ours").getIndex();
        int theirs = world.getPlayerList().addPlayer("Theirs").getIndex();
        world.getPlayerList().getPlayer(ours).setRelationshipTo(world.getPlayerList().getPlayer(theirs),
                Relationship.ENEMIES);
        world.getPlayerList().getPlayer(theirs).setRelationshipTo(world.getPlayerList().getPlayer(ours),
                Relationship.ENEMIES);
        return world;
    }

    private static GameObject tank(CombatTest.CombatLogic world, int side, float x, float y) {
        var tank = world.createObject(world.getThingFactory().findTemplate("Tank"));
        tank.setPlayerIndex(side);
        tank.setPosition(new Coord3D(x, y, 0f));
        return tank;
    }

    private static void run(CombatTest.CombatLogic world, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            world.update();
        }
    }

    @Test
    void aHeldTankOrderedAcrossTheMapStaysAndGoesOnceReleased() {
        var world = world();
        var tank = tank(world, 1, 100f, 100f);
        tank.setStatus(ObjectStatus.HELD);

        tank.getLocomotor().moveTo(new Coord3D(900f, 100f, 0f));
        run(world, 300);

        assertEquals(new Coord3D(100f, 100f, 0f), tank.getPosition(), "the order was taken and went nowhere");
        tank.clearStatus(ObjectStatus.HELD);
        run(world, 30);
        assertTrue(tank.getPosition().x() > 120f, "released, it goes on at once: " + tank.getPosition());
    }

    @Test
    void aHeldTankStillKillsAnEnemyInRange() {
        var world = world();
        var tank = tank(world, 1, 100f, 100f);
        var enemy = tank(world, 2, 100f + RANGE - 5f, 100f);
        enemy.setStatus(ObjectStatus.DISABLED); // a sitting target, so only the held one fights
        tank.setStatus(ObjectStatus.HELD);

        tank.findModule(WeaponUpdate.class).attack(enemy.getId());
        run(world, 200);

        assertTrue(enemy.isEffectivelyDead(), "it fired from where it stood");
        assertEquals(new Coord3D(100f, 100f, 0f), tank.getPosition());
    }

    @Test
    void aHeldTankWithItsTargetJustOutOfRangeNeitherClosesNorFires() {
        var world = world();
        var tank = tank(world, 1, 100f, 100f);
        var enemy = tank(world, 2, 100f + RANGE + 5f, 100f);
        enemy.setStatus(ObjectStatus.DISABLED);
        tank.setStatus(ObjectStatus.HELD);

        tank.findModule(WeaponUpdate.class).attack(enemy.getId());
        run(world, 120);

        assertEquals(new Coord3D(100f, 100f, 0f), tank.getPosition(), "no pursuit");
        assertEquals(300f, enemy.getBody().getHealth(), "and nothing fired");
        tank.clearStatus(ObjectStatus.HELD);
        run(world, 120);
        assertTrue(enemy.getBody().getHealth() < 300f, "released, it closes and fires");
    }

    @Test
    void holdingIsInTheChecksumAndTheSameOnTwoPeers() {
        var first = world();
        var second = world();
        var mine = tank(first, 1, 100f, 100f);
        var theirs = tank(second, 1, 100f, 100f);
        long before = first.checksum();

        mine.setStatus(ObjectStatus.HELD);
        theirs.setStatus(ObjectStatus.HELD);
        mine.getLocomotor().moveTo(new Coord3D(500f, 100f, 0f));
        theirs.getLocomotor().moveTo(new Coord3D(500f, 100f, 0f));
        run(first, 60);
        run(second, 60);

        assertNotEquals(before, first.checksum(), "a hold is a thing's state, and the checksum says so");
        assertEquals(first.checksum(), second.checksum(), "the same on both");
    }
}
