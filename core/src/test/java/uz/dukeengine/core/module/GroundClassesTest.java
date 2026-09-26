package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.ObstacleRules;
import uz.dukeengine.core.pathfind.Path;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * Ground of classes a mover may or may not cross — the reference's cell types and locomotor surfaces ({@code
 * validLocomotorSurfacesForCellType}): a commando climbing a cliff a tank goes round, at his cliff locomotor's speed,
 * and a ruin's rubble infantry walk.
 */
class GroundClassesTest {

    private static final class Field extends GameLogic {
        Field(ThingFactory things) {
            super(things);
        }

        @Override
        protected void simulate() {
        }
    }

    private static final ThingTemplate PLAIN = ThingTemplate.named("Tank").geometry(new Geometry.Cylinder(5f, 6f))
            .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build();
    private static final ThingTemplate WADER = ThingTemplate.named("Boat").geometry(new Geometry.Cylinder(5f, 6f))
            .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f, 0f, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f,
                    false, MoveUpdate.Gait.OTHER, 0, MoveUpdate.MovePriority.FRONT, List.of("WATER"), Map.of()))
            .build();
    private static final ThingTemplate CLIMBER = ThingTemplate.named("Commando")
            .geometry(new Geometry.Cylinder(5f, 6f)).module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(30f, 0f, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f, false, MoveUpdate.Gait.OTHER, 0,
                    MoveUpdate.MovePriority.FRONT, List.of(), Map.of("CLIFF", new MoveUpdate.Data(20f))))
            .build();
    private static final ThingTemplate INFANTRY = ThingTemplate.named("Ranger").geometry(new Geometry.Cylinder(5f, 6f))
            .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f, 0f, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f,
                    false, MoveUpdate.Gait.OTHER, 0, MoveUpdate.MovePriority.FRONT, List.of("RUBBLE"), Map.of()))
            .build();
    private static final ThingTemplate RUIN = ThingTemplate.named("House").geometry(new Geometry.Box(40f, 40f, 20f))
            .module(new ActiveBody.Data(500f)).build();

    private static Field field() {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        for (var template : List.of(PLAIN, WADER, CLIMBER, INFANTRY, RUIN)) {
            things.addTemplate(template);
        }
        var world = new Field(things);
        world.init();
        world.setPathGrid(new PathGrid(60, 60));
        return world;
    }

    private static GameObject put(Field world, ThingTemplate template, float x, float y) {
        var thing = world.createObject(template);
        thing.setPosition(new Coord3D(x, y, 0f));
        return thing;
    }

    private static float mostY(Path path) {
        float most = 0f;
        for (var point : path.getWaypoints()) {
            most = Math.max(most, point.y());
        }
        return most;
    }

    @Test
    void aBandOfWaterIsCrossedByAMoverThatNamesItAndGoneRoundByOneThatDoesNot() {
        var world = field();
        var grid = world.getPathGrid();
        for (int cy = 0; cy < 55; cy++) {
            for (int cx = 28; cx <= 32; cx++) {
                grid.setGroundClass(cx, cy, "WATER"); // 5 wide, with a way round at the top
            }
        }
        var boat = put(world, WADER, 100f, 300f);
        var tank = put(world, PLAIN, 100f, 305f);
        var across = new Coord3D(500f, 300f, 0f);

        var sailed = world.findPath(boat, across);
        assertTrue(sailed.reachesGoal());
        assertTrue(mostY(sailed) < 320f, "straight across: " + sailed.getWaypoints());
        var driven = world.findPath(tank, across);
        assertTrue(driven.reachesGoal());
        assertTrue(mostY(driven) > 540f, "round the end of it: " + driven.getWaypoints());

        for (int cy = 55; cy < 60; cy++) {
            for (int cx = 28; cx <= 32; cx++) {
                grid.setGroundClass(cx, cy, "WATER"); // no way round now
            }
        }
        assertFalse(world.findPath(tank, across).reachesGoal(), "refused: no way round");
        assertTrue(world.findPath(boat, across).reachesGoal());
        var plainZones = world.zones();
        assertFalse(plainZones.connected(10, 30, 50, 30), "the band parts a tank's zones");
        assertEquals("WATER", world.groundClassUnder(put(world, WADER, 300f, 300f)));
    }

    @Test
    void aClimberCrossesCliffAtItsCliffLocomotorsSpeed() {
        var world = field();
        var grid = world.getPathGrid();
        for (int cy = 0; cy < 60; cy++) {
            for (int cx = 20; cx < 30; cx++) {
                grid.setGroundClass(cx, cy, "CLIFF"); // 100 across
            }
        }
        var commando = put(world, CLIMBER, 105f, 305f);
        commando.getLocomotor().moveTo(new Coord3D(505f, 305f, 0f));
        int onIt = 0;
        for (int frame = 0; frame < 900; frame++) {
            world.update();
            if ("CLIFF".equals(world.groundClassUnder(commando))) {
                onIt++;
            }
        }
        assertEquals(150, onIt, 3, "100 of cliff at 20 a second");
        assertTrue(commando.getPosition().x() > 490f, "and on across it at 30");
    }

    @Test
    void aRuinLayingRubbleIsWalkedByInfantryAndGoneRoundByATank() {
        var world = field();
        world.setObstacleRules(new ObstacleRules(Set.of(), Set.of(), 0f,
                List.of(new ObstacleRules.Laid("RUBBLE_WORD", null, "RUBBLE"))));
        var house = put(world, RUIN, 300f, 300f);
        var ranger = put(world, INFANTRY, 100f, 300f);
        var tank = put(world, PLAIN, 100f, 305f);
        var beyond = new Coord3D(500f, 300f, 0f);
        world.update();
        assertTrue(mostY(world.findPath(ranger, beyond)) > 330f, "standing, every mover goes round it");

        house.setCondition("RUBBLE_WORD"); // in ruins
        world.update();
        assertTrue(mostY(world.findPath(ranger, beyond)) < 310f, "infantry walk its rubble");
        assertTrue(mostY(world.findPath(tank, beyond)) > 330f, "a tank still goes round");
    }
}
