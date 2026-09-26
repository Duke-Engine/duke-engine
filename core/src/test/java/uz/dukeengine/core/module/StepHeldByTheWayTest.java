package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.ObstacleRules;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * A mover's step held only by what the game's obstacle rules put in the way — the reference's movers pass palms,
 * lamps and barrels ({@code Pathfinder::classifyObjectFootprint}, {@code AIUpdateInterface::processCollision}).
 */
class StepHeldByTheWayTest {

    private static final Kind STRUCTURE = Kind.of("STRUCTURE");
    private static final ThingTemplate TANK = ThingTemplate.named("Tank").geometry(new Geometry.Cylinder(5f, 6f))
            .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build();
    private static final ThingTemplate PALM = ThingTemplate.named("Palm").geometry(new Geometry.Cylinder(4f, 10f))
            .kindOf(Kind.of("PROP")).module(new ActiveBody.Data(10f)).build();
    /** A wall across the way, too long to go round in the time. */
    private static final ThingTemplate WALL = ThingTemplate.named("Wall").geometry(new Geometry.Box(5f, 500f, 10f))
            .kindOf(STRUCTURE).module(new ActiveBody.Data(500f)).build();

    /** A world with no path grid: a mover walks straight, and only its step's blockers stop it. */
    private static GameLogic world(ObstacleRules rules) {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        for (var template : List.of(TANK, PALM, WALL)) {
            things.addTemplate(template);
        }
        var world = new GameLogic(things) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        world.setObstacleRules(rules);
        return world;
    }

    private static GameObject put(GameLogic world, ThingTemplate template, float x) {
        var thing = world.createObject(template);
        thing.setPosition(new Coord3D(x, 100f, 0f));
        return thing;
    }

    /** A tank's drive from x 0 toward x 300, a palm at 100 and a wall at 200: how far it strays passing the palm. */
    private record Drive(float strayAtThePalm, float reached) {
    }

    private static Drive drive(ObstacleRules rules) {
        var world = world(rules);
        put(world, PALM, 100f);
        put(world, WALL, 200f);
        var tank = put(world, TANK, 0f);
        tank.getLocomotor().moveTo(new Coord3D(300f, 100f, 0f));
        float stray = 0f;
        for (int frame = 0; frame < 300; frame++) {
            world.update();
            var at = tank.getPosition();
            if (at.x() > 95f && at.x() < 105f) {
                stray = Math.max(stray, Math.abs(at.y() - 100f));
            }
        }
        return new Drive(stray, tank.getPosition().x());
    }

    @Test
    void withOnlyStructuresInTheWayATankDrivesThroughAPalmAndStopsAtAWall() {
        var drive = drive(new ObstacleRules(Set.of(STRUCTURE), Set.of(), 0f));
        assertTrue(drive.strayAtThePalm() < 1f, "straight through the palm: " + drive.strayAtThePalm());
        assertTrue(drive.reached() > 150f && drive.reached() < 191f, "held at the wall: " + drive.reached());
    }

    @Test
    void withEverythingInTheWayThePalmHoldsItsStepToo() {
        var drive = drive(ObstacleRules.EVERYTHING);
        assertTrue(drive.strayAtThePalm() >= 8f || drive.reached() < 95f,
                "round the palm or held at it: " + drive.strayAtThePalm() + ", " + drive.reached());
        assertTrue(drive.reached() < 191f, "and at the wall");
    }
}
