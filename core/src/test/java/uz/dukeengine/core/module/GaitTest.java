package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * Movers that gather and shed speed and turn as the reference's locomotors do, by how they move: legs, treads,
 * wheels.
 */
class GaitTest {

    private static final class Flat extends GameLogic {
        Flat(ThingFactory things) {
            super(things);
        }

        @Override
        protected void simulate() {
        }
    }

    private static GameObject mover(MoveUpdate.Data legs, float x, float y, float facing) {
        var template = ThingTemplate.named("Mover").module(new ActiveBody.Data(100f)).module(legs).build();
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(template);
        var world = new Flat(things);
        world.init();
        var mover = world.spawn(template, new Coord3D(x, y, 0f), 1);
        mover.setOrientation(facing);
        return mover;
    }

    private static MoveUpdate.Data data(float speed, float turnRate, float acceleration, float braking,
            MoveUpdate.Gait gait) {
        return new MoveUpdate.Data(speed, turnRate, acceleration, braking, 0f, 0f, 0.1f, 0f, 0f, 1f, false, gait);
    }

    /** Each frame's step, until it stops or {@code frames} have run. */
    private static List<Float> steps(GameObject mover, int frames) {
        var steps = new ArrayList<Float>();
        var legs = mover.findModule(MoveUpdate.class);
        for (int frame = 0; frame < frames && legs.isMoving(); frame++) {
            var was = mover.getPosition();
            ((GameLogic) mover.getWorld()).update();
            steps.add((float) Math.hypot(mover.getPosition().x() - was.x(), mover.getPosition().y() - was.y()));
        }
        return steps;
    }

    /**
     * The reference's heavy tank, its acceleration 15 and 30 once upgraded: given its new locomotor whole on its way,
     * it gains at the new rate from the next frame, going on to where it was going, on the block it holds there.
     */
    @Test
    void aMoverGivenItsLocomotorWholeOnItsWayGainsAtTheNewRateAndKeepsItsWay() {
        var tank = mover(data(30f, 0f, 15f, 15f, MoveUpdate.Gait.LEGS), 25f, 25f, 0f);
        var world = (GameLogic) tank.getWorld();
        world.setPathGrid(new uz.dukeengine.core.pathfind.PathGrid(40, 40));
        var legs = tank.findModule(MoveUpdate.class);
        legs.moveTo(new Coord3D(325f, 25f, 0f));
        var goal = legs.getGoal();
        var block = world.getPathGrid().movers().goalOf(tank.getId().value());

        var before = steps(tank, 3);
        legs.setLocomotor(data(30f, 0f, 30f, 15f, MoveUpdate.Gait.LEGS));
        var after = steps(tank, 3);

        assertEquals(15f / 900f, before.get(2) - before.get(1), 1e-4f, "0.017 a frame each frame, at 15");
        assertEquals(30f / 900f, after.get(0) - before.get(2), 1e-4f, "0.033 from the next frame, at 30");
        assertEquals(30f / 900f, after.get(2) - after.get(1), 1e-4f);
        assertEquals(goal, legs.getGoal(), "still going where it was going");
        assertEquals(block, world.getPathGrid().movers().goalOf(tank.getId().value()), "to the block it held");
    }

    /**
     * An infantryman at exactly his damagedBelow share of his health, 0.35: damaged, as the reference's body is at or
     * below its threshold — he gains at his damaged acceleration, 50, not his 100.
     */
    @Test
    void aMoverAtExactlyItsDamagedShareGainsAtItsDamagedAcceleration() {
        var soldier = mover(new MoveUpdate.Data(30f, 0f, 100f, 100f, 50f, 0f, 0.35f, 0f, 0f, 1f, false,
                MoveUpdate.Gait.LEGS), 25f, 25f, 0f);
        soldier.getBody().setHealth(35f);
        soldier.findModule(MoveUpdate.class).moveTo(new Coord3D(325f, 25f, 0f));

        var steps = steps(soldier, 2);

        assertEquals(50f / 900f, steps.get(0), 1e-4f, "0.056 a frame each frame, at 50");
        assertEquals(100f / 900f, steps.get(1), 1e-4f);
    }

    /** The reference's four-wheeled truck: Speed 40, TurnRate 90, MinTurnSpeed 15, Acceleration 240, Braking 50. */
    private static MoveUpdate.Data truck() {
        return new MoveUpdate.Data(40f, 90f, 240f, 50f, 0f, 0f, 0.1f, 0f, 15f, 1f, false, MoveUpdate.Gait.WHEELS);
    }

    /** Frames until it stops, or {@code most} have run. */
    private static int drive(GameObject mover, int most) {
        var legs = mover.findModule(MoveUpdate.class);
        int frames = 0;
        while (legs.isMoving() && frames < most) {
            ((GameLogic) mover.getWorld()).update();
            frames++;
        }
        return frames;
    }

    /**
     * The truck beside the end of a wall, facing away from where it is sent: its route's first waypoint, round the
     * wall's end, lies 13 to its side and 7 ahead, inside the 19 its turns draw. Steering along its route as the
     * reference does ({@code Path::computePointOnPath}), it passes the waypoint and arrives; steering onto it, it
     * drove round it for the 30 s the test gives it.
     */
    @Test
    void wheelsWhoseFirstWaypointLiesBesideThemPassItAndArrive() {
        var truck = mover(truck(), 112f, 108f, (float) Math.PI);
        var world = (GameLogic) truck.getWorld();
        var grid = new uz.dukeengine.core.pathfind.PathGrid(60, 60);
        for (int cy = 0; cy <= 10; cy++) {
            grid.setBlocked(11, cy, true);
        }
        world.setPathGrid(grid);
        var legs = truck.findModule(MoveUpdate.class);
        legs.moveTo(new Coord3D(350f, 105f, 0f));
        var destination = legs.getDestination();

        int frames = drive(truck, 900);

        assertFalse(legs.stoppedShort(), "it did not give up");
        assertTrue(truck.getPosition().distance(destination) < 1f, "it arrived: " + truck.getPosition() + " at "
                + frames);
    }

    /**
     * The truck sent to a point 30 to its side: within four cells of it for 2.5 s, it brakes and slides onto it (the
     * reference's DONUT_DISTANCE and DONUT_TIME_DELAY_SECONDS) — there within 5 s.
     */
    @Test
    void wheelsSentToAPointThirtyToTheirSideReachItWithinFiveSeconds() {
        var truck = mover(truck(), 305f, 305f, 0f);
        ((GameLogic) truck.getWorld()).setPathGrid(new uz.dukeengine.core.pathfind.PathGrid(60, 60));
        var legs = truck.findModule(MoveUpdate.class);
        legs.moveTo(new Coord3D(305f, 335f, 0f));
        var destination = legs.getDestination();

        int frames = drive(truck, 900);

        assertTrue(frames <= 150, "within 5 s: " + frames);
        assertTrue(truck.getPosition().distance(destination) < 1f, "on it: " + truck.getPosition());
    }

    @Test
    void aWalkerGathersSpeedAtItsAccelerationAndEasesOntoItsGoalOverItsLastTwoPointOne() {
        var walker = mover(data(20f, 0f, 100f, 100f, MoveUpdate.Gait.LEGS), 0f, 0f, 0f);
        var goal = new Coord3D(100f, 0f, 0f);
        walker.findModule(MoveUpdate.class).moveTo(goal);

        var steps = steps(walker, 600);

        float[] gathering = {0.111f, 0.222f, 0.333f, 0.444f, 0.556f, 0.667f};
        for (int frame = 0; frame < gathering.length; frame++) {
            assertEquals(gathering[frame], steps.get(frame), 1e-3f, "frame " + (frame + 1));
        }
        assertEquals(0.667f, steps.get(10), 1e-3f, "and on at its speed");
        float walked = 0f;
        for (int frame = 0; frame < steps.size(); frame++) {
            if (frame >= 6 && steps.get(frame) < steps.get(frame - 1) - 1e-4f) {
                break; // the first frame it walks slower: what was left then decided it
            }
            walked += steps.get(frame);
        }
        float left = 100f - walked;
        assertTrue(left < 2.1f && left + 0.667f >= 2.1f, "it slows over its last 2.1, from " + left);
        assertFalse(walker.findModule(MoveUpdate.class).isMoving(), "and stops");
        assertTrue(walker.getPosition().distance(goal) < 1f, "within 1 of its goal: " + walker.getPosition());
    }

    @Test
    void treadsNinetyDegreesOffTurnWhereTheyStandForSevenFramesThenGatherSpeedAsTheyStraighten() {
        var tank = mover(data(30f, 180f, 0f, 0f, MoveUpdate.Gait.TREADS), 0f, 0f, 0f);
        var goal = new Coord3D(0f, 100f, 0f); // straight up the y axis: 90 degrees to its facing
        tank.findModule(MoveUpdate.class).moveTo(goal);

        var steps = steps(tank, 1200);

        for (int frame = 0; frame < 7; frame++) {
            assertEquals(0f, steps.get(frame), 1e-6f, "turning where it stands, frame " + (frame + 1));
        }
        assertTrue(steps.get(7) > 0f, "under 45 degrees off it moves");
        assertTrue(steps.get(8) > steps.get(7) && steps.get(9) > steps.get(8), "faster as it straightens");
        assertFalse(tank.findModule(MoveUpdate.class).isMoving());
        assertTrue(tank.getPosition().distance(goal) < 1f, "and it arrives: " + tank.getPosition());
    }

    @Test
    void wheelsTurnOnlyWhileTheyRollAndCurveRoundAtTheirTurningSpeed() {
        var truck = mover(data(60f, 90f, 0f, 0f, MoveUpdate.Gait.WHEELS), 100f, 100f, 0f);
        var goal = new Coord3D(40f, 100f, 0f); // behind it
        truck.findModule(MoveUpdate.class).moveTo(goal);
        var legs = truck.findModule(MoveUpdate.class);

        ((GameLogic) truck.getWorld()).update();
        assertEquals(0f, truck.getOrientation(), 0f, "no turn while it stood");
        assertEquals(15f, legs.getSpeedNow(), 1e-4f, "rolling at its turning speed, a quarter of 60");

        ((GameLogic) truck.getWorld()).update();
        assertTrue(Math.abs(truck.getOrientation()) > 0f, "turning once it rolls");
        assertEquals(15f, legs.getSpeedNow(), 1e-4f, "and curving round at 15");

        for (int frame = 0; frame < 1200 && legs.isMoving(); frame++) {
            ((GameLogic) truck.getWorld()).update();
        }
        assertTrue(truck.getPosition().distance(goal) < 1f, "it gets there: " + truck.getPosition());
    }

    @Test
    void aMoverBlockNamesItsGaitAndRates() {
        var block = uz.dukeengine.core.data.DukeText.parse("""
                MoveUpdate
                  Speed = 30
                  TurnRate = 180
                  Acceleration = 60
                  Braking = 90
                  MinTurnSpeed = 12
                  CloseEnough = 3
                  CanMoveBackwards = Yes
                  Gait = WHEELS
                End
                """, "move.duke").getFirst();

        var legs = new uz.dukeengine.core.data.Binder().bind(block, MoveUpdate.Data.class);

        assertEquals(MoveUpdate.Gait.WHEELS, legs.gait());
        assertEquals(60f, legs.acceleration(), 0f);
        assertEquals(90f, legs.braking(), 0f);
        assertEquals(3f, legs.closeEnough(), 0f);
        assertTrue(legs.canMoveBackwards());
        assertEquals(0.1f, legs.damagedBelow(), 0f, "left out: the reference's really damaged");
        var plain = new uz.dukeengine.core.data.Binder().bind(
                uz.dukeengine.core.data.DukeText.parse("MoveUpdate\n  Speed = 20\nEnd\n", "m.duke").getFirst(),
                MoveUpdate.Data.class);
        assertEquals(MoveUpdate.Gait.OTHER, plain.gait(), "a block that names none moves as things always moved");
        assertEquals(1f, plain.closeEnough(), 0f);
    }
}
