package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rts.RtsTemplate;

/**
 * A weapon shorter than a cell closes until it touches, as the reference's contact weapons do: a Terrorist's charge of
 * range 1 used to stand a cell off a tank for good, unfired.
 */
class ContactWeaponTest {

    private static CombatTest.CombatLogic world() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(attacker("Terrorist", 1f, 30f));
        factory.addTemplate(attacker("Launcher", 150f, 30f));
        factory.addTemplate(RtsTemplate.named("Tank")
                .geometry(new Geometry.Box(7.5f, 5f, 10f))
                .module(new ActiveBody.Data(1000f))
                .module(new MoveUpdate.Data(30f))
                .build());
        var world = new CombatTest.CombatLogic(factory);
        world.init();
        world.setPathGrid(new PathGrid(60, 60));
        var us = world.getPlayerList().addPlayer("Us");
        var them = world.getPlayerList().addPlayer("Them");
        us.setRelationshipTo(them, Relationship.ENEMIES);
        them.setRelationshipTo(us, Relationship.ENEMIES);
        return world;
    }

    private static uz.dukeengine.core.thing.ThingTemplate attacker(String name, float range, float speed) {
        return RtsTemplate.named(name)
                .geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f))
                .module(new MoveUpdate.Data(speed))
                .module(new WeaponUpdate.Data(50f, range, 30, DamageType.NORMAL))
                .module(new PursueUpdate.Data(0f, 0))
                .build();
    }

    private static GameObject spawn(CombatTest.CombatLogic world, String template, int side, float x, float y) {
        var thing = world.createObject(world.getThingFactory().findTemplate(template));
        thing.setPlayerIndex(side);
        thing.setPosition(new Coord3D(x, y, 0f));
        return thing;
    }

    private static float closestAndHurt(CombatTest.CombatLogic world, String template, float away) {
        var attacker = spawn(world, template, 0, 300f - away, 300f);
        var tank = spawn(world, "Tank", 1, 300f, 300f);
        attacker.findModule(WeaponUpdate.class).attack(tank.getId());
        for (int frame = 0; frame < 300 && tank.getBody().getHealth() == 1000f; frame++) {
            world.update();
        }
        assertTrue(tank.getBody().getHealth() < 1000f, template + " fired at the tank");
        return World.reachBetween(attacker, tank);
    }

    @Test
    void aRangeOneWeaponOrderedAtATank60AwayTouchesItAndFires() {
        float reach = closestAndHurt(world(), "Terrorist", 60f);
        assertTrue(reach <= 1f, "touching it: " + reach);
    }

    @Test
    void aRange150WeaponStillStopsWhereItCanFireFrom() {
        float reach = closestAndHurt(world(), "Launcher", 300f);
        assertTrue(reach > 100f && reach <= 150f, "where it could fire from: " + reach);
    }
}
