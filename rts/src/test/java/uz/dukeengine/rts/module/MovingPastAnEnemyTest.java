package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * A unit sent somewhere goes there, firing on the move at what it passes, as the reference's move state looks for no
 * target; the same unit ordered to attack stops in range to fight.
 */
class MovingPastAnEnemyTest {

    private static CombatTest.CombatLogic world() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(ThingTemplate.named("Humvee").module(new ActiveBody.Data(300f))
                .module(new MoveUpdate.Data(30f))
                .module(new WeaponUpdate.Data(5f, 100f, 10, DamageType.NORMAL, 0f, true))
                .module(new PursueUpdate.Data(0f, 0)).build());
        factory.addTemplate(ThingTemplate.named("Tank").module(new ActiveBody.Data(100_000f)).build());
        var world = new CombatTest.CombatLogic(factory);
        world.init();
        var usa = world.getPlayerList().addPlayer("USA");
        var china = world.getPlayerList().addPlayer("China");
        usa.setRelationshipTo(china, Relationship.ENEMIES);
        china.setRelationshipTo(usa, Relationship.ENEMIES);
        return world;
    }

    private static GameObject spawn(CombatTest.CombatLogic world, String template, int side, float x, float y) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, y, 0f), side);
    }

    @Test
    void aUnitSentPastAnEnemyWithinItsRangeFiresOnTheMoveAndArrives() {
        var world = world();
        var humvee = spawn(world, "Humvee", 1, 100f, 300f);
        var tank = spawn(world, "Tank", 2, 300f, 350f);
        var goal = new Coord3D(500f, 300f, 0f);

        humvee.getLocomotor().moveTo(goal);
        for (int frame = 0; frame < 900 && (frame < 5 || humvee.getLocomotor().isMoving()); frame++) {
            world.update();
        }

        for (int frame = 0; frame < 60; frame++) {
            world.update();
        }

        assertTrue(tank.getBody().getHealth() < 100_000f, "it fired on the move");
        assertTrue(humvee.getPosition().distance(goal) < 1f, "and arrived, and stayed: " + humvee.getPosition());
    }

    @Test
    void theSameUnitOrderedToAttackThatEnemyStopsInRangeAndFires() {
        var world = world();
        var humvee = spawn(world, "Humvee", 1, 100f, 300f);
        var tank = spawn(world, "Tank", 2, 300f, 350f);
        var weapon = humvee.findModule(WeaponUpdate.class);

        weapon.attack(tank.getId());
        for (int frame = 0; frame < 300; frame++) {
            world.update();
        }

        assertFalse(humvee.getLocomotor().isMoving(), "stood to fight");
        assertTrue(weapon.isInRange(tank), "in range: " + humvee.getPosition());
        assertTrue(humvee.getPosition().x() < 300f, "short of it, not past it");
        assertTrue(tank.getBody().getHealth() < 100_000f, "and fired");
    }
}
