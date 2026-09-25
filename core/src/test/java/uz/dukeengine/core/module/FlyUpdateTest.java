package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.ObjectTemplate;
import uz.dukeengine.core.thing.ThingFactory;

/**
 * Things in the air: over a cliff at their own height, a winged one circling when it has nowhere to go, and
 * neither of them in a ground unit's way — which still walks round the cliff.
 */
class FlyUpdateTest {

    private static final class Sky extends GameLogic {
        Sky(ThingFactory factory) {
            super(factory);
        }

        @Override
        protected void simulate() {
        }
    }

    private Sky world;
    private PathGrid grid;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(ObjectTemplate.named("Helicopter").geometry(new Geometry.Cylinder(4f, 3f))
                .module(new ActiveBody.Data(100f))
                .module(new FlyUpdate.Data(FlyUpdate.Kind.HOVERING, 60f, 0f, 60f, 60f, 180f, 50f, 100f)).build());
        factory.addTemplate(ObjectTemplate.named("Jet").geometry(new Geometry.Cylinder(4f, 3f))
                .module(new ActiveBody.Data(100f))
                .module(new FlyUpdate.Data(FlyUpdate.Kind.WINGED, 90f, 60f, 60f, 60f, 90f, 120f, 60f)).build());
        factory.addTemplate(ObjectTemplate.named("Tank").geometry(new Geometry.Cylinder(4f, 3f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build());
        world = new Sky(factory);
        world.init();
        // Twenty cells of ten a side, and a cliff down the middle with a gap at the top: x 100 to 110, y 0 to 160.
        grid = new PathGrid(20, 20);
        for (int cy = 0; cy < 16; cy++) {
            grid.setBlocked(10, cy, true);
        }
        world.setPathGrid(grid);
    }

    private GameObject spawn(String template, float x, float y) {
        return world.spawn(world.getThingFactory().findTemplate(template), new Coord3D(x, y, 0f), 1);
    }

    @Test
    void aHeldHelicopterHangsWhereItIsAndGoesOnOnceLetGo() {
        var helicopter = spawn("Helicopter", 50f, 50f);
        for (int frame = 0; frame < 60; frame++) {
            world.update(); // up to its height first
        }
        var hanging = helicopter.getPosition();
        helicopter.setStatus(ObjectStatus.HELD);

        helicopter.getLocomotor().moveTo(new Coord3D(150f, 50f, 0f));
        for (int frame = 0; frame < 120; frame++) {
            world.update();
        }

        assertEquals(hanging, helicopter.getPosition(), "held in the air: nothing moves it");
        assertTrue(helicopter.hasStatus(ObjectStatus.AIRBORNE), "and it is still aloft");
        helicopter.clearStatus(ObjectStatus.HELD);
        for (int frame = 0; frame < 30; frame++) {
            world.update();
        }
        assertTrue(helicopter.getPosition().x() > hanging.x(), "let go, it flies its order");
    }

    @Test
    void aHoveringThingFliesStraightOverTheCliffAtItsHeightAndStopsThere() {
        var helicopter = spawn("Helicopter", 50f, 50f);
        var legs = helicopter.getLocomotor();
        legs.moveTo(new Coord3D(150f, 50f, 0f));

        float furthestOff = 0f;
        int frames = 0;
        while (legs.isMoving() && frames < 600) {
            world.update();
            frames++;
            furthestOff = Math.max(furthestOff, Math.abs(helicopter.getPosition().y() - 50f));
        }

        assertEquals(0f, furthestOff, 1e-3f, "straight over the cliff, not round it");
        assertEquals(150f, helicopter.getPosition().x(), 1e-3f, "stopped on the goal");
        assertEquals(50f, helicopter.getPosition().z(), 1e-3f, "at its height above the ground");
        assertTrue(helicopter.hasStatus(ObjectStatus.AIRBORNE));
        assertTrue(frames < 4 * GameConstants.LOGICFRAMES_PER_SECOND, "a hundred at sixty a second, braking");
        world.update();
        assertEquals(150f, helicopter.getPosition().x(), 1e-3f, "and it stays there, in the air");
    }

    @Test
    void aWingedThingWithNothingToDoKeepsMovingAndCirclesItsLastGoal() {
        var jet = spawn("Jet", 40f, 100f);
        var legs = (FlyUpdate) jet.getLocomotor();
        var goal = new Coord3D(100f, 100f, 0f);
        legs.moveTo(goal);
        for (int frame = 0; frame < 300 && legs.isMoving(); frame++) {
            world.update();
        }
        assertFalse(legs.isMoving(), "it got there");
        float radius = legs.circleRadius();

        var was = jet.getPosition();
        float leastStep = Float.MAX_VALUE;
        float furthest = 0f;
        for (int frame = 0; frame < 300; frame++) {
            world.update();
            var now = jet.getPosition();
            float dx = now.x() - was.x();
            float dy = now.y() - was.y();
            leastStep = Math.min(leastStep, (float) Math.sqrt(dx * dx + dy * dy));
            furthest = Math.max(furthest, (float) Math.hypot(now.x() - goal.x(), now.y() - goal.y()));
            was = now;
        }

        assertTrue(leastStep >= 60f * GameConstants.SECONDS_PER_LOGICFRAME * 0.99f,
                "never slower than its least speed: " + leastStep);
        assertTrue(furthest <= 2.5f * radius, "within a circle round its last goal: " + furthest + " of " + radius);
        assertTrue(Math.abs(jet.getRoll()) > 0.05f, "banked, as it is always turning");
    }

    /**
     * A bomber over open sky and no map edge: 90 a second at 18 degrees a second, a turning circle of 286 — the
     * reference's B-52 falls that far short of its goal when "arrived" is "inside the circle".
     */
    private static GameObject bomber(Sky[] sky) {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(ObjectTemplate.named("Bomber").geometry(new Geometry.Cylinder(4f, 3f))
                .module(new ActiveBody.Data(100f))
                .module(new FlyUpdate.Data(FlyUpdate.Kind.WINGED, 90f, 90f, 0f, 0f, 18f, 120f, 60f)).build());
        sky[0] = new Sky(factory);
        sky[0].init();
        return sky[0].spawn(factory.findTemplate("Bomber"), new Coord3D(1000f, 1000f, 0f), 1);
    }

    /** Flies the bomber to {@code goal}: how near it came, and on which frames it said it had arrived. */
    private static float[] flyTo(Sky sky, GameObject bomber, Coord3D goal) {
        var legs = bomber.getLocomotor();
        legs.moveTo(goal);
        float nearest = Float.MAX_VALUE;
        float nearestWhenArrived = Float.NaN;
        int arrivals = 0;
        boolean moving = legs.isMoving();
        for (int frame = 0; frame < 3000; frame++) {
            var before = bomber.getPosition();
            sky.update();
            var after = bomber.getPosition();
            nearest = Math.min(nearest, distanceToSegment(goal, before, after));
            if (moving && !legs.isMoving()) {
                arrivals++;
                nearestWhenArrived = distanceToSegment(goal, before, after);
            }
            moving = legs.isMoving();
        }
        return new float[] {nearest, arrivals, nearestWhenArrived};
    }

    /** How near the path from {@code a} to {@code b} passes {@code p}, on the ground. */
    private static float distanceToSegment(Coord3D p, Coord3D a, Coord3D b) {
        float abx = b.x() - a.x();
        float aby = b.y() - a.y();
        float length = abx * abx + aby * aby;
        float t = length == 0f ? 0f : Math.clamp(((p.x() - a.x()) * abx + (p.y() - a.y()) * aby) / length, 0f, 1f);
        return (float) Math.hypot(p.x() - (a.x() + abx * t), p.y() - (a.y() + aby * t));
    }

    @Test
    void aBomberSentToAPointAheadInsideItsTurningCircleFliesOverIt() {
        var sky = new Sky[1];
        var bomber = bomber(sky);
        var flown = flyTo(sky[0], bomber, new Coord3D(1200f, 1000f, 0f));

        assertTrue(flown[0] <= 1f, "it passes over the point, not a turning circle short of it: " + flown[0]);
        assertEquals(1f, flown[1], "arrival is said once");
        assertTrue(flown[2] <= 90f * GameConstants.SECONDS_PER_LOGICFRAME, "on the frame it passes: " + flown[2]);
    }

    @Test
    void aBomberSentToAPointBesideItLoopsAndComesBackOverIt() {
        var sky = new Sky[1];
        var bomber = bomber(sky);
        var flown = flyTo(sky[0], bomber, new Coord3D(1000f, 1150f, 0f));

        assertTrue(flown[0] <= 90f * GameConstants.SECONDS_PER_LOGICFRAME, "it went wide and came back over it: "
                + flown[0]);
        assertEquals(1f, flown[1], "arrival is said once");
    }

    /**
     * Its turning circle is the circle it keeps, so from the middle it cannot join it without swinging out once — as
     * far as two radii, half a turn on — before it settles round it; it used to fly straight on to the map's edge.
     */
    @Test
    void aWingedThingGivenNothingCirclesWhereItWasMade() {
        var sky = new Sky[1];
        var bomber = bomber(sky);
        float radius = ((FlyUpdate) bomber.getLocomotor()).circleRadius();
        float furthest = 0f;
        float nearestLate = Float.MAX_VALUE;
        float furthestLate = 0f;
        for (int frame = 0; frame < 30 * GameConstants.LOGICFRAMES_PER_SECOND; frame++) {
            sky[0].update();
            var at = bomber.getPosition();
            float away = (float) Math.hypot(at.x() - 1000f, at.y() - 1000f);
            furthest = Math.max(furthest, away);
            if (frame >= 20 * GameConstants.LOGICFRAMES_PER_SECOND) {
                nearestLate = Math.min(nearestLate, away);
                furthestLate = Math.max(furthestLate, away);
            }
        }

        assertTrue(furthest <= 2.01f * radius, "never beyond its one swing out: " + furthest + " of " + radius);
        assertTrue(nearestLate >= 0.8f * radius && furthestLate <= 1.2f * radius,
                "then round where it was made, a turning radius out: " + nearestLate + ".." + furthestLate);
    }

    @Test
    void aThingInTheAirIsInNoGroundUnitsWay() {
        var helicopter = spawn("Helicopter", 100f, 180f);
        world.update(); // it lifts off
        var tank = spawn("Tank", 50f, 180f);
        tank.getLocomotor().moveTo(new Coord3D(150f, 180f, 0f));

        float nearest = Float.MAX_VALUE;
        for (int frame = 0; frame < 300 && tank.getLocomotor().isMoving(); frame++) {
            world.update();
            nearest = Math.min(nearest, Math.abs(tank.getPosition().x() - helicopter.getPosition().x())
                    + Math.abs(tank.getPosition().y() - helicopter.getPosition().y()));
        }

        assertTrue(nearest < 1f, "it drove straight under it: " + nearest);
        assertEquals(150f, tank.getPosition().x(), 1e-3f);
    }

    @Test
    void aGroundUnitSentTheSameWayStillGoesRoundTheCliff() {
        var tank = spawn("Tank", 50f, 50f);
        tank.getLocomotor().moveTo(new Coord3D(150f, 50f, 0f));

        float highest = 0f;
        for (int frame = 0; frame < 900 && tank.getLocomotor().isMoving(); frame++) {
            world.update();
            var at = tank.getPosition();
            assertFalse(grid.isTerrainBlocked(grid.toCellX(at), grid.toCellY(at)), "never on the cliff");
            highest = Math.max(highest, at.y());
        }

        assertTrue(highest > 150f, "round by the gap at the top: " + highest);
        assertEquals(150f, tank.getPosition().x(), 1f);
    }
}
