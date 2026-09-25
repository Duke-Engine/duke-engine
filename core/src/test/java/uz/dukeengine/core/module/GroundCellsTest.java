package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.Block;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * Ground movers keep cells of their own, as the reference's pathfinder places them, and give way to each other rather
 * than shoving: a place becomes the nearest block a mover may have, legs pass legs, a mover is held up only by one it
 * drives into, and the one of lower priority steps aside.
 */
class GroundCellsTest {

    private static final class Field extends GameLogic {
        Field(ThingFactory things) {
            super(things);
        }

        @Override
        protected void simulate() {
        }
    }

    private static MoveUpdate.Data legs(float speed, float turnRate, MoveUpdate.Gait gait) {
        return new MoveUpdate.Data(speed, turnRate, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f, false, gait);
    }

    /** A soldier of radius 7 on legs, the reference's Ranger: a 2 by 2 block. */
    private static final ThingTemplate RANGER = ThingTemplate.named("Ranger")
            .geometry(new Geometry.Cylinder(7f, 12f)).module(new ActiveBody.Data(100f))
            .module(legs(20f, 0f, MoveUpdate.Gait.LEGS)).build();
    /** The same body, moving as the engine always moved things. */
    private static final ThingTemplate MOVER = ThingTemplate.named("Mover")
            .geometry(new Geometry.Cylinder(7f, 12f)).module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(20f)).build();
    /** A box of radii 15 and 10 on treads, the reference's Crusader: a circle of 18, a 3 by 3 block. */
    private static final ThingTemplate CRUSADER = ThingTemplate.named("Crusader")
            .geometry(new Geometry.Box(15f, 10f, 10f)).module(new ActiveBody.Data(400f))
            .module(legs(30f, 180f, MoveUpdate.Gait.TREADS)).build();

    private static Field field() {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(RANGER);
        things.addTemplate(MOVER);
        things.addTemplate(CRUSADER);
        var world = new Field(things);
        world.init();
        world.setPathGrid(new PathGrid(80, 80));
        return world;
    }

    private static GameObject spawn(Field world, ThingTemplate template, float x, float y) {
        return world.spawn(template, new Coord3D(x, y, 0f), 1);
    }

    private static MoveUpdate legsOf(GameObject thing) {
        return thing.findModule(MoveUpdate.class);
    }

    /** Frames until every one has stopped, or {@code most} have run. */
    private static int runUntilStill(Field world, List<GameObject> movers, int most) {
        int frame = 0;
        while (frame < most && movers.stream().anyMatch(m -> legsOf(m).isMoving())) {
            world.update();
            frame++;
        }
        return frame;
    }

    private static Block blockOf(GameObject thing) {
        var shape = uz.dukeengine.core.thing.Solid.of(thing.getTemplate());
        return Block.of(shape.footprintRadius(), 10f, thing.getPosition().x(), thing.getPosition().y());
    }

    // ---- cells of their own (ask 1) ----

    @Test
    void twoSentToOnePointStopOnBlocksOfTheirOwnTwentyApartAndNeitherStopsShort() {
        var world = field();
        var a = spawn(world, MOVER, 100f, 300f);
        var b = spawn(world, MOVER, 140f, 300f);
        var point = new Coord3D(300f, 300f, 0f);

        legsOf(a).moveTo(point);
        legsOf(b).moveTo(point);
        runUntilStill(world, List.of(a, b), 1200);

        assertFalse(legsOf(a).stoppedShort() || legsOf(b).stoppedShort(), "neither stops short");
        var mine = blockOf(a);
        var theirs = blockOf(b);
        assertFalse(mine.overlaps(theirs), "blocks of their own");
        assertTrue(Math.abs(mine.x() - theirs.x()) == 2 || Math.abs(mine.y() - theirs.y()) == 2,
                "20 apart along x or y: " + mine + " and " + theirs);
    }

    @Test
    void aMoverSentOntoAnIdleAllyIsGivenTheNearestFreeBlockBesideItAndGetsThere() {
        var world = field();
        var ally = spawn(world, MOVER, 300.5f, 300.5f);
        for (int frame = 0; frame < 16; frame++) {
            world.update(); // standing, it holds its block
        }
        var mover = spawn(world, MOVER, 100f, 300f);

        legsOf(mover).moveTo(ally.getPosition());
        var goal = legsOf(mover).getGoal();
        runUntilStill(world, List.of(mover, ally), 1200);

        var allyBlock = Block.of(7f, 10f, 300.5f, 300.5f);
        var given = Block.of(7f, 10f, goal.x(), goal.y());
        assertFalse(given.overlaps(allyBlock), "not the ally's own block");
        assertTrue(Math.abs(given.x() - allyBlock.x()) <= 2 && Math.abs(given.y() - allyBlock.y()) <= 2,
                "the nearest free one, beside it: " + given);
        assertFalse(legsOf(mover).stoppedShort());
        assertTrue(mover.getPosition().distance(goal) < 1f, "and it gets there: " + mover.getPosition());
    }

    @Test
    void tenSentToOnePointNoneStopsShortAndNoTwoEndOnACommonCell() {
        var world = field();
        var rangers = new ArrayList<GameObject>();
        for (int i = 0; i < 10; i++) {
            rangers.add(spawn(world, RANGER, 100f + 20f * (i % 5), 100f + 20f * (i / 5)));
        }
        var point = new Coord3D(300f, 410f, 0f);

        rangers.forEach(r -> legsOf(r).moveTo(point));
        int frames = runUntilStill(world, rangers, 1800);

        assertTrue(frames < 1800, "all stopped");
        for (var r : rangers) {
            assertFalse(legsOf(r).stoppedShort(), "none stops short: " + r.getPosition());
        }
        for (int i = 0; i < rangers.size(); i++) {
            for (int j = i + 1; j < rangers.size(); j++) {
                assertFalse(blockOf(rangers.get(i)).overlaps(blockOf(rangers.get(j))),
                        "no common cell: " + rangers.get(i).getPosition() + " and " + rangers.get(j).getPosition());
            }
        }
    }

    @Test
    void aMoverSentThroughPointsPassesEachInTurnAndHoldsItsPlaceAtTheEndFromTheStart() {
        var world = field();
        var mover = spawn(world, MOVER, 100f, 100f);
        var first = new Coord3D(300f, 100f, 0f);
        var second = new Coord3D(300f, 300f, 0f);

        legsOf(mover).moveThrough(List.of(first, second), new Coord3D(100f, 300f, 0f));
        var goal = Block.of(7f, 10f, 100f, 300f);
        var rival = spawn(world, MOVER, 60f, 300f);
        legsOf(rival).moveTo(new Coord3D(100f, 300f, 0f));
        float nearFirst = Float.MAX_VALUE;
        float nearSecond = Float.MAX_VALUE;
        boolean secondBeforeFirst = false;
        for (int frame = 0; frame < 1200 && legsOf(mover).isMoving(); frame++) {
            world.update();
            nearFirst = Math.min(nearFirst, mover.getPosition().distance(first));
            nearSecond = Math.min(nearSecond, mover.getPosition().distance(second));
            secondBeforeFirst |= nearSecond < 2f && nearFirst >= 2f;
        }

        assertTrue(nearFirst < 2f && nearSecond < 2f, "through both: " + nearFirst + ", " + nearSecond);
        assertFalse(secondBeforeFirst, "in turn");
        assertEquals(goal, blockOf(mover), "and on the place it was sent to at the end: " + mover.getPosition());
        assertFalse(blockOf(rival).overlaps(goal), "held all the way: the other was given another");
    }

    // ---- giving way (ask 3) ----

    @Test
    void fiveOnLegsWalkThroughFiveInTheSameLanesInTheTimeTheirWalkTakes() {
        var world = field();
        var all = new ArrayList<GameObject>();
        for (int lane = 0; lane < 5; lane++) {
            float x = 100f + 20f * lane;
            var north = spawn(world, RANGER, x, 100f);
            var south = spawn(world, RANGER, x, 430f);
            legsOf(north).moveTo(new Coord3D(x, 400f, 0f));
            legsOf(south).moveTo(new Coord3D(x, 130f, 0f));
            all.add(north);
            all.add(south);
        }

        int frames = runUntilStill(world, all, 900);

        assertTrue(frames <= 453, "all ten there in the 15.1 s their walk takes, none held: " + frames + " frames");
        all.forEach(r -> assertFalse(legsOf(r).stoppedShort()));
    }

    @Test
    void aBoxSentThroughAnIdleAlliedSoldierInTheOneGapItCanPassHasItStepAsideAndKeepsToItsLine() {
        var world = field();
        for (int y = 0; y < 80; y++) {
            if (y < 28 || y > 32) {
                world.getPathGrid().setBlocked(32, y, true); // a wall, and a gap in it the box has room in along one row
            }
        }
        var soldier = spawn(world, RANGER, 320f, 300f);
        for (int frame = 0; frame < 16; frame++) {
            world.update();
        }
        var box = spawn(world, CRUSADER, 100f, 300f);
        legsOf(box).moveTo(new Coord3D(500f, 300f, 0f));
        var goal = legsOf(box).getGoal();

        float furthest = 0f;
        for (int frame = 0; frame < 1200 && legsOf(box).isMoving(); frame++) {
            world.update();
            var at = box.getPosition();
            float along = (at.x() - 100f) / (goal.x() - 100f);
            float lineY = 300f + (goal.y() - 300f) * along;
            furthest = Math.max(furthest, Math.abs(at.y() - lineY));
        }

        assertTrue(soldier.getPosition().distance(new Coord3D(320f, 300f, 0f)) > 14f,
                "the soldier stepped aside: " + soldier.getPosition());
        assertTrue(furthest <= 10f, "the box kept within 10 of its line: " + furthest);
        assertTrue(box.getPosition().distance(goal) < 1f, "and got there: " + box.getPosition());
    }

    @Test
    void inTheOpenABoxDrivesRoundAnIdleAlliedSoldierAndLeavesItWhereItStands() {
        var world = field();
        var soldier = spawn(world, RANGER, 300f, 300f);
        for (int frame = 0; frame < 16; frame++) {
            world.update();
        }
        var box = spawn(world, CRUSADER, 100f, 300f);

        legsOf(box).moveTo(new Coord3D(500f, 300f, 0f));
        var goal = legsOf(box).getGoal();
        runUntilStill(world, List.of(box, soldier), 1200);

        // Two cells round it cost less than the 42 each cell through it costs: the route is not blocked by the ally.
        assertEquals(new Coord3D(300f, 300f, 0f), soldier.getPosition(), "the soldier was not asked aside");
        assertTrue(box.getPosition().distance(goal) < 1f, "and the box got there: " + box.getPosition());
    }

    @Test
    void twoBoxesDrivenAtEachOtherAlongOneLineBothArriveAndNeitherGivesUp() {
        var world = field();
        var east = spawn(world, CRUSADER, 105f, 305f);
        var west = spawn(world, CRUSADER, 405f, 305f);
        west.setOrientation((float) Math.PI);

        legsOf(east).moveTo(new Coord3D(405f, 305f, 0f));
        legsOf(west).moveTo(new Coord3D(105f, 305f, 0f));
        var eastGoal = legsOf(east).getGoal();
        var westGoal = legsOf(west).getGoal();
        runUntilStill(world, List.of(east, west), 3000);

        assertFalse(legsOf(east).stoppedShort() || legsOf(west).stoppedShort(), "neither gave up");
        assertTrue(east.getPosition().distance(eastGoal) < 1f, "east got there: " + east.getPosition());
        assertTrue(west.getPosition().distance(westGoal) < 1f, "west got there: " + west.getPosition());
    }

    @Test
    void tenBoxesSentToOnePointRestWithNoTwoMoreThanTwoIntoEachOther() {
        var world = field();
        var boxes = new ArrayList<GameObject>();
        for (int i = 0; i < 10; i++) {
            boxes.add(spawn(world, CRUSADER, 100f + 40f * (i % 5), 100f + 40f * (i / 5)));
        }
        var point = new Coord3D(400f, 410f, 0f);

        boxes.forEach(b -> legsOf(b).moveTo(point));
        int frames = runUntilStill(world, boxes, 3000);

        assertTrue(frames < 3000, "all stopped");
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                float into = -Footprint.of(boxes.get(i)).separation(Footprint.of(boxes.get(j)));
                assertTrue(into <= 2f, "no two more than 2 into each other: " + into + " between "
                        + boxes.get(i).getPosition() + " and " + boxes.get(j).getPosition());
            }
        }
        assertEquals(10, boxes.size());
    }
}
