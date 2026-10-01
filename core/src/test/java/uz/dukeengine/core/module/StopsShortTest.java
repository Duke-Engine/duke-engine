package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * A walk that ends short of where it was sent says so, and one that bodies shut the way of goes as near as they let it:
 * sent at a building whose doorstep an enemy stands on it walks up beside him; planning round a body and finding no way
 * it has stopped short; held between two still bodies it gets past them or stops short, rather than planning round each
 * in turn for ever; and asked aside on a walk exactly to a point, it goes on to the point afterwards.
 */
class StopsShortTest {

    /** A thing at work where it stands: not asked aside, and not moved off its ground. */
    static final class AtWork extends Module {
        record Data() implements ModuleData {
        }

        AtWork(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean keepsBusy() {
            return true;
        }
    }

    private static final int HERO = 1;
    private static final int ENEMY = 2;
    /** A mover's stuck limit, two seconds; and the second a route asked for too soon waits. */
    private static final int STUCK_LIMIT = 2 * uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND;
    private static final int ROUTE_WAIT = uz.dukeengine.core.GameConstants.LOGICFRAMES_PER_SECOND;

    /** A body of radius 7 moving as the engine always moved things: a 2 by 2 block. */
    private static final ThingTemplate WALKER = ThingTemplate.named("Walker")
            .geometry(new Geometry.Cylinder(7f, 12f)).module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(20f)).build();
    /** The same, at work where it stands. */
    private static final ThingTemplate WORKER = ThingTemplate.named("Worker")
            .geometry(new Geometry.Cylinder(7f, 12f)).module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(20f)).module(new AtWork.Data()).build();
    /** A building five cells a side. */
    private static final ThingTemplate KEEP = ThingTemplate.named("Keep")
            .geometry(new Geometry.Box(25f, 25f, 20f)).module(new ActiveBody.Data(1000f)).build();

    private static GameLogic field() {
        var things = new ThingFactory(ModuleFactory.withDefaults()
                .register(AtWork.Data.class, (owner, data) -> new AtWork(owner)));
        things.addTemplate(WALKER);
        things.addTemplate(WORKER);
        things.addTemplate(KEEP);
        var world = new GameLogic(things) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        world.setPathGrid(new PathGrid(80, 80));
        return world;
    }

    private static MoveUpdate legsOf(GameObject thing) {
        return thing.findModule(MoveUpdate.class);
    }

    private static void run(GameLogic world, int frames) {
        for (int frame = 0; frame < frames; frame++) {
            world.update();
        }
    }

    /** Frames until it has stopped, or {@code most} have run. */
    private static int runUntilStill(GameLogic world, GameObject mover, int most) {
        int frame = 0;
        while (frame < most && legsOf(mover).isMoving()) {
            world.update();
            frame++;
        }
        return frame;
    }

    private static float apart(GameObject a, GameObject b) {
        return a.getPosition().distance(b.getPosition());
    }

    // ---- (1) a goal no zone of its holds, its way shut by a body ----

    @Test
    void sentAtABuildingWhoseDoorstepAStillEnemyStandsOnItWalksUpBesideHim() {
        var world = field();
        var keep = world.spawn(KEEP, new Coord3D(425f, 305f, 0f), 0);
        // On the ground nearest the keep's middle that a body of 7 has room on: where a walk up to it would end.
        var guard = world.spawn(WALKER, new Coord3D(375f, 305f, 0f), ENEMY);
        run(world, 16); // standing, he holds his ground
        var hero = world.spawn(WALKER, new Coord3D(105f, 305f, 0f), HERO);

        legsOf(hero).moveExactlyTo(keep.getPosition());

        assertTrue(legsOf(hero).isMoving(), "it goes: open ground much nearer the keep can be walked to");
        int frames = runUntilStill(world, hero, 1200);
        assertTrue(frames < 1200, "and it stops");
        assertTrue(legsOf(hero).stoppedShort(), "short of the keep, and says so");
        assertTrue(apart(hero, guard) < 30f, "beside the guard: " + hero.getPosition());
    }

    // ---- (2) and (3): a way shut by bodies it plans round ----

    /** Stone everywhere but a corridor from row {@code from} to row {@code to}, a few cells in from the edges. */
    private static void corridor(GameLogic world, int from, int to) {
        var grid = world.getPathGrid();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setBlocked(x, y, y < from || y > to || x < 5 || x > 75);
            }
        }
    }

    @Test
    void planningRoundAWorkerWhoShutsTheCorridorAndFindingNoWayItHasStoppedShort() {
        var world = field();
        corridor(world, 30, 31); // two cells: one body shuts it
        var worker = world.spawn(WORKER, new Coord3D(300f, 310f, 0f), HERO);
        run(world, 16);
        var hero = world.spawn(WALKER, new Coord3D(105f, 310f, 0f), HERO);

        legsOf(hero).moveTo(new Coord3D(505f, 310f, 0f));
        int frames = runUntilStill(world, hero, 1500);

        assertTrue(frames < 1500, "it stands");
        assertTrue(legsOf(hero).stoppedShort(), "short of where it was sent, and says so: " + hero.getPosition());
        assertTrue(hero.getPosition().x() < worker.getPosition().x(), "behind the worker");
        assertTrue(legsOf(hero).plans() >= 2, "having planned round him");
    }

    @Test
    void heldBetweenTwoWorkersWhoShutTheCorridorItStopsShortWithinTheStuckLimit() {
        var world = field();
        // Four cells: two bodies shoulder to shoulder shut it, each route round one running into the other — planned
        // round each in turn, it walked on the spot for ever (81 routes in 3000 frames).
        corridor(world, 30, 33);
        var north = world.spawn(WORKER, new Coord3D(300f, 306f, 0f), HERO);
        var south = world.spawn(WORKER, new Coord3D(300f, 332f, 0f), HERO);
        run(world, 16);
        var hero = world.spawn(WALKER, new Coord3D(105f, 320f, 0f), HERO);

        legsOf(hero).moveTo(new Coord3D(505f, 320f, 0f));
        int firstHeld = -1;
        int frame = 0;
        while (frame < 3000 && legsOf(hero).isMoving() && hero.getPosition().x() < 320f) {
            world.update();
            frame++;
            if (firstHeld < 0 && legsOf(hero).plans() > 1) {
                firstHeld = frame;
            }
        }

        assertTrue(firstHeld > 0, "held, it planned round them");
        boolean past = hero.getPosition().x() >= 320f;
        assertTrue(past || legsOf(hero).stoppedShort(), "through them, or short of them and saying so");
        assertTrue(frame - firstHeld <= STUCK_LIMIT + ROUTE_WAIT,
                "within the stuck limit of first planning round one: " + (frame - firstHeld) + " frames, "
                        + legsOf(hero).plans() + " routes");
        assertTrue(legsOf(hero).plans() <= 4, "a few routes, not one round each in turn: " + legsOf(hero).plans());
        assertTrue(apart(hero, north) > 7f && apart(hero, south) > 7f, "and not standing in either");
    }

    // ---- (5) a walk exactly to a point, asked aside ----

    @Test
    void askedAsideOnAWalkExactlyToAPointItGoesOnToThePointAfterwards() {
        var world = field();
        var hero = world.spawn(WALKER, new Coord3D(105f, 305f, 0f), HERO);
        var chest = new Coord3D(505f, 305f, 0f);
        var friend = world.spawn(WALKER, new Coord3D(205f, 305f, 0f), HERO);
        legsOf(hero).moveExactlyTo(chest);
        run(world, 30);

        legsOf(hero).stepAsideFor(friend, List.of(friend.getPosition(), new Coord3D(105f, 305f, 0f)));
        assertTrue(hero.getPosition().distance(legsOf(hero).getDestination()) > 1f, "stepping aside");
        assertEquals(chest, legsOf(hero).getGoal(), "its walk still the chest");
        runUntilStill(world, hero, 1500);

        assertFalse(legsOf(hero).stoppedShort());
        assertEquals(chest, legsOf(hero).getGoal());
        assertTrue(hero.getPosition().distance(chest) < 1f, "and on to it afterwards: " + hero.getPosition());
    }

    @Test
    void askedAsideTwiceByTwoItStillGoesOnToThePoint() {
        var world = field();
        var hero = world.spawn(WALKER, new Coord3D(105f, 305f, 0f), HERO);
        var chest = new Coord3D(505f, 305f, 0f);
        var one = world.spawn(WALKER, new Coord3D(205f, 305f, 0f), HERO);
        var other = world.spawn(WALKER, new Coord3D(205f, 405f, 0f), HERO);
        legsOf(hero).moveExactlyTo(chest);
        run(world, 30);

        legsOf(hero).stepAsideFor(one, List.of(one.getPosition(), new Coord3D(105f, 305f, 0f)));
        run(world, 5);
        legsOf(hero).stepAsideFor(other, List.of(other.getPosition(), hero.getPosition()));
        runUntilStill(world, hero, 1500);

        assertEquals(chest, legsOf(hero).getGoal());
        assertTrue(hero.getPosition().distance(chest) < 1f, "on to it after both: " + hero.getPosition());
    }

    @Test
    void askedAsideOnAWalkIntoABandRoundAThingItGoesOnIntoTheBand() {
        var world = field();
        var hero = world.spawn(WALKER, new Coord3D(105f, 305f, 0f), HERO);
        var target = world.spawn(KEEP, new Coord3D(525f, 305f, 0f), ENEMY);
        var friend = world.spawn(WALKER, new Coord3D(205f, 305f, 0f), HERO);
        legsOf(hero).moveWithin(target, 10f, 20f);
        run(world, 30);

        legsOf(hero).stepAsideFor(friend, List.of(friend.getPosition(), new Coord3D(105f, 305f, 0f)));
        runUntilStill(world, hero, 1500);

        float gap = uz.dukeengine.core.thing.Footprint.of(hero).separation(uz.dukeengine.core.thing.Footprint.of(target));
        assertTrue(gap >= 9f && gap <= 21f, "in the band afterwards: " + gap + " at " + hero.getPosition());
        assertNotNull(legsOf(hero).getGoal());
    }
}
