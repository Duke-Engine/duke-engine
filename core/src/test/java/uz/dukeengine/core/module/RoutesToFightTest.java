package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.Pathfinder;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * Routes that do not walk the whole map to find their goal: a route to fight ends at the first cell a weapon reaches
 * from (the reference's {@code findAttackPath}); a goal too tight for the mover is searched for once, at its width, as
 * the reference moves it to a cell the mover can stand on first ({@code adjustDestination}); and a mover waits for one
 * route at a time.
 */
class RoutesToFightTest {

    private static final class Field extends GameLogic {
        Field(ThingFactory things) {
            super(things);
        }

        @Override
        protected void simulate() {
        }
    }

    private static final ThingTemplate TANK = ThingTemplate.named("Tank").geometry(new Geometry.Cylinder(5f, 6f))
            .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build();
    private static final ThingTemplate WIDE = ThingTemplate.named("Wide").geometry(new Geometry.Cylinder(18f, 6f))
            .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build();
    private static final ThingTemplate TARGET = ThingTemplate.named("Target").geometry(new Geometry.Cylinder(5f, 6f))
            .module(new ActiveBody.Data(100f)).build();

    private static Field field() {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(TANK);
        things.addTemplate(WIDE);
        things.addTemplate(TARGET);
        var world = new Field(things);
        world.init();
        world.setPathGrid(new PathGrid(100, 100));
        return world;
    }

    private static GameObject put(Field world, ThingTemplate template, float x, float y) {
        var thing = world.createObject(template);
        thing.setPosition(new Coord3D(x, y, 0f));
        return thing;
    }

    @Test
    void aRouteToFightEndsWithinTheWeaponsReachAndExaminesFewerCellsThanOneToTheTargetsSide() {
        var world = field();
        var tank = put(world, TANK, 100f, 505f);
        var target = put(world, TARGET, 500f, 505f);
        var grid = world.getPathGrid();
        float reach = 150f;
        var outline = Footprint.of(target);
        var toFight = new Pathfinder.Tally();
        var path = Pathfinder.findPathWithin(grid, tank.getPosition(), target.getPosition(), 5f, at -> {
            float gap = Footprint.of(tank, at).separation(outline);
            return Math.max(0f, gap - reach);
        }, toFight, null);
        var toTheSide = new Pathfinder.Tally();
        Pathfinder.findPathOrNearest(grid, tank.getPosition(), world.standingNextTo(tank, target), 5f, world.zones(),
                toTheSide);

        float ends = Footprint.of(tank, path.getWaypoints().getLast()).separation(outline);
        assertTrue(ends <= reach, "its route ends within 150 of the target: " + ends);
        assertTrue(ends > reach - world.cellSize() * 1.5f, "at the first cell it reaches from, not nearer: " + ends);
        assertTrue(toFight.cells() < toTheSide.cells(),
                "fewer cells than a route to its side: " + toFight.cells() + " against " + toTheSide.cells());
    }

    @Test
    void aMoverSentNearerAWallThanItsWidthSearchesOnceAndNoMoreThanTheWayThere() {
        var world = field();
        var grid = world.getPathGrid();
        for (int cy = 0; cy < 100; cy++) {
            grid.setBlocked(60, cy, true); // a wall across the map, its face at x 600
        }
        var wide = put(world, WIDE, 105f, 505f);
        var nearTheWall = new Coord3D(595f, 505f, 0f);
        var tally = new Pathfinder.Tally();
        var path = Pathfinder.findPathOrNearest(grid, wide.getPosition(), nearTheWall, 18f, world.zones(), tally);
        var wayThere = new Pathfinder.Tally();
        Pathfinder.findPathOrNearest(grid, wide.getPosition(), nearTheWall, 0f, world.zones(), wayThere);

        assertTrue(path.reachesGoal(), "it gets there, the last of it squeezed");
        assertEquals(nearTheWall, path.getWaypoints().getLast());
        assertTrue(tally.cells() <= wayThere.cells(),
                "one search, of no more cells than the way there: " + tally.cells() + " against " + wayThere.cells());
    }

    @Test
    void aMoverAskedAgainBeforeItsRouteWasServedIsServedOnceForItsLatestGoal() {
        var world = field();
        world.setPathfindBudget(1);
        var first = put(world, TANK, 105f, 105f);
        var second = put(world, TANK, 105f, 305f);
        world.update();
        first.findModule(MoveUpdate.class).moveTo(new Coord3D(705f, 105f, 0f)); // the frame's searching spent on it
        var legs = second.findModule(MoveUpdate.class);
        int before = legs.plans();
        legs.moveTo(new Coord3D(705f, 305f, 0f));
        legs.moveTo(new Coord3D(705f, 505f, 0f));
        assertEquals(before, legs.plans(), "nothing searched while the frame's searching is spent");

        for (int frame = 0; frame < 3; frame++) {
            world.update(); // what was searched between frames counts against the next: its turn comes after
        }
        assertEquals(before + 1, legs.plans(), "one route when its turn came");
        var goal = legs.getGoal();
        assertTrue(goal.y() > 400f, "for the goal it was given last: " + goal);
    }
}
