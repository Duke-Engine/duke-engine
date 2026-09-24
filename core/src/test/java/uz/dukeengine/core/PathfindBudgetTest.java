package uz.dukeengine.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.Pathfinder;
import uz.dukeengine.core.pathfind.Zones;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectTemplate;
import uz.dukeengine.core.thing.ThingFactory;

/**
 * Path searches kept to a budget a frame, as the reference keeps them, and a goal out of reach known to be at once.
 */
class PathfindBudgetTest {

    private static final class Ground extends GameLogic {
        Ground(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void simulate() {
        }
    }

    private static Ground world(PathGrid grid) {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(ObjectTemplate.named("Walker").module(new MoveUpdate.Data(30f)).build());
        var world = new Ground(factory);
        world.init();
        world.setPathGrid(grid);
        return world;
    }

    /** Open ground with a few short walls about: the searches of an ordinary map. */
    private static PathGrid fewWalls() {
        var grid = new PathGrid(60, 60);
        for (int n = 0; n < 8; n++) {
            grid.setBlocked(30, 20 + n, true);
            grid.setBlocked(40 + n, 10, true);
            grid.setBlocked(45, 40 + n, true);
        }
        return grid;
    }

    @Test
    void twoHundredOrdersAreAllServedInAFewFramesAndNoFrameSearchesMoreThanItsBudget() {
        var grid = fewWalls();
        var world = world(grid);
        var walkers = new ArrayList<GameObject>();
        var goal = new Coord3D(550f, 300f, 0f);
        int largest = 0;
        for (int n = 0; n < 200; n++) {
            var walker = world.createObject(world.getThingFactory().findTemplate("Walker"));
            walker.setPosition(new Coord3D(20f + n % 10 * 20f, 20f + n / 10 * 25f, 0f));
            walkers.add(walker);
            var alone = new Pathfinder.Tally();
            Pathfinder.findPathOrNearest(grid, walker.getPosition(), goal, 0f, Zones.of(grid), alone);
            largest = Math.max(largest, alone.cells());
        }
        world.update(); // a frame with nothing asked: the next begins with its whole budget

        for (var walker : walkers) {
            walker.findModule(MoveUpdate.class).moveTo(goal);
        }
        long waitingAtFirst = walkers.stream()
                .filter(walker -> walker.findModule(MoveUpdate.class).isWaitingForRoute()).count();
        assertTrue(waitingAtFirst > 0, "more than a frame's searching: some wait their turn");

        var perFrame = new ArrayList<Integer>();
        int frames = 1;
        world.update();
        perFrame.add(world.getCellsExaminedLastFrame());
        while (walkers.stream().anyMatch(walker -> walker.findModule(MoveUpdate.class).isWaitingForRoute())
                && frames < 60) {
            world.update();
            frames++;
            perFrame.add(world.getCellsExaminedLastFrame());
        }

        int over = largest;
        assertTrue(frames <= 10, "every order served within a few frames: " + frames + " " + perFrame);
        assertTrue(perFrame.stream().allMatch(cells -> cells < GameLogic.DEFAULT_PATHFIND_BUDGET + over),
                "no search started once a frame's budget was spent — over it by one search at most, the largest "
                        + over + ": " + perFrame);
        assertTrue(walkers.stream().allMatch(walker -> walker.findModule(MoveUpdate.class).isMoving()),
                "and every one of them on its way");
    }

    @Test
    void aGoalInAnEnclosedPlaceGetsARouteToTheNearestReachableCellWithoutAFlood() {
        var grid = new PathGrid(30, 30);
        for (int cx = 13; cx <= 17; cx++) {
            for (int cy = 13; cy <= 17; cy++) {
                grid.setBlocked(cx, cy, cx == 13 || cx == 17 || cy == 13 || cy == 17);
            }
        }
        var zones = Zones.of(grid);
        var tally = new Pathfinder.Tally();

        var path = Pathfinder.findPathOrNearest(grid, new Coord3D(55f, 155f, 0f), new Coord3D(155f, 155f, 0f), 0f,
                zones, tally);

        assertFalse(path.reachesGoal(), "the goal is walled in");
        assertEquals(new Coord3D(125f, 155f, 0f), path.getDestination(), "against the wall on this side");
        assertTrue(tally.cells() < 40, "a walk of seven cells, not the 800 it can reach: " + tally.cells());

        var searched = new Pathfinder.Tally();
        Pathfinder.findPathOrNearest(grid, new Coord3D(55f, 155f, 0f), new Coord3D(155f, 155f, 0f), 0f, null,
                searched);
        assertTrue(searched.cells() > 800, "where searching without zones walked all of it: " + searched.cells());
    }

    @Test
    void zonesChangeWhenTheGroundDoes() {
        var grid = new PathGrid(10, 10);
        var zones = Zones.of(grid);
        assertTrue(zones.connected(0, 0, 9, 9));

        for (int cy = 0; cy < 10; cy++) {
            grid.setBlocked(5, cy, true);
        }

        assertFalse(zones.isCurrent(grid), "the grid changed shape");
        var again = Zones.of(grid);
        assertFalse(again.connected(0, 0, 9, 9), "and now the two sides are two zones");
        assertEquals(2, again.count());
    }
}
