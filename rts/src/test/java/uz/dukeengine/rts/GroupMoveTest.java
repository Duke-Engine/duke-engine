package uz.dukeengine.rts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.Block;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.RtsModules;

/** A group sent to one point with one order, placed in the simulation as the reference places it. */
class GroupMoveTest {

    private static final class Field extends RtsSimulation {
        Field(ThingFactory things) {
            super(things);
        }

        @Override
        protected void onRtsCommand(GameMessage command) {
        }

        @Override
        protected void simulate() {
        }
    }

    private static MoveUpdate.Data moving(float speed, float turnRate, MoveUpdate.Gait gait) {
        return new MoveUpdate.Data(speed, turnRate, 0f, 0f, 0f, 0f, 0.1f, 0f, 0f, 1f, false, gait);
    }

    /** Infantry of radius 7 on legs, 20 a second: 300 in 15 s. */
    private static final ThingTemplate RANGER = ThingTemplate.named("Ranger")
            .geometry(new Geometry.Cylinder(7f, 12f)).module(new ActiveBody.Data(100f))
            .module(moving(20f, 0f, MoveUpdate.Gait.LEGS)).build();
    /** A box of radii 15 and 10 on treads, 30 a second: the reference's Crusader. */
    private static final ThingTemplate CRUSADER = ThingTemplate.named("Crusader")
            .geometry(new Geometry.Box(15f, 10f, 10f)).module(new ActiveBody.Data(400f))
            .module(moving(30f, 180f, MoveUpdate.Gait.TREADS)).build();

    private static Field field() {
        var things = new ThingFactory(RtsModules.withDefaults());
        things.addTemplate(RANGER);
        things.addTemplate(CRUSADER);
        var world = new Field(things);
        world.init();
        world.setPathGrid(new PathGrid(80, 80));
        return world;
    }

    private static List<GameObject> block(Field world, ThingTemplate template, float x, float y, float apart) {
        var units = new ArrayList<GameObject>();
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 5; column++) {
                units.add(world.spawn(template, new Coord3D(x + apart * column, y + apart * row, 0f), 1));
            }
        }
        return units;
    }

    private static MoveUpdate legs(GameObject unit) {
        return unit.findModule(MoveUpdate.class);
    }

    private record Walk(int frames, int mostHeld) {
    }

    /** Frames until every one has stopped, or {@code most} have run; and the most frames one stood while moving. */
    private static Walk walk(Field world, List<GameObject> units, int most) {
        var held = new HashMap<GameObject, Integer>();
        int frame = 0;
        while (frame < most && units.stream().anyMatch(unit -> legs(unit).isMoving())) {
            var before = new HashMap<GameObject, Coord3D>();
            units.forEach(unit -> before.put(unit, unit.getPosition()));
            world.update();
            frame++;
            for (var unit : units) {
                if (legs(unit).isMoving() && unit.getPosition().equals(before.get(unit))) {
                    held.merge(unit, 1, Integer::sum);
                }
            }
        }
        return new Walk(frame, held.values().stream().mapToInt(Integer::intValue).max().orElse(0));
    }

    private static void assertNoTwoWithin(float apart, List<GameObject> units) {
        for (int i = 0; i < units.size(); i++) {
            for (int j = i + 1; j < units.size(); j++) {
                var a = units.get(i).getPosition();
                var b = units.get(j).getPosition();
                assertFalse(Math.abs(a.x() - b.x()) < apart && Math.abs(a.y() - b.y()) < apart,
                        "no two within " + apart + " along both x and y: " + a + " and " + b);
            }
        }
    }

    @Test
    void tenInfantrySentFarWithOneOrderEndInColumnsApartNearThePointInTheTimeTheirWalkTakes() {
        var world = field();
        var rangers = block(world, RANGER, 100f, 100f, 20f);
        var point = new Coord3D(450f, 110f, 0f); // 310 from the block's middle

        GroupMove.REFERENCE.send(world, rangers, point, true);
        var walk = walk(world, rangers, 1200);

        assertTrue(walk.frames() <= 18 * 30, "the last stops within 18 s: " + walk.frames() + " frames");
        assertTrue(walk.mostHeld() <= 10, "none held back more than 10 frames: " + walk.mostHeld());
        for (var ranger : rangers) {
            assertFalse(legs(ranger).stoppedShort(), "none short: " + ranger.getPosition());
            assertTrue(ranger.getPosition().distance(point) <= 80f, "within 80 of the point: " + ranger.getPosition());
        }
        assertNoTwoWithin(18f, rangers);
    }

    @Test
    void tenBoxesSentFarWithOneOrderEndApartNearThePointWithNoTwoIntoEachOther() {
        var world = field();
        var boxes = block(world, CRUSADER, 100f, 100f, 40f);
        var point = new Coord3D(490f, 120f, 0f); // 310 from the block's middle

        GroupMove.REFERENCE.send(world, boxes, point, true);
        var walk = walk(world, boxes, 1200);

        assertTrue(walk.frames() <= 13 * 30, "the last stops within 13 s: " + walk.frames() + " frames");
        for (var box : boxes) {
            assertFalse(legs(box).stoppedShort(), "none short: " + box.getPosition());
            // The reference cuts a place to six sizes out (108) and pulls it in only through free blocks: five in a row
            // thirty apart end the row 120 out, and one whose place is taken goes a block further. Not the 100 asked.
            assertTrue(box.getPosition().distance(point) <= 6f * 18.03f + 30f,
                    "within six sizes and a block of the point: " + box.getPosition());
        }
        assertNoTwoWithin(28f, boxes);
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                float into = -Footprint.of(boxes.get(i)).separation(Footprint.of(boxes.get(j)));
                assertTrue(into <= 2f, "no two footprints more than 2 into each other: " + into + " between "
                        + boxes.get(i).getPosition() + " and " + boxes.get(j).getPosition());
            }
        }
    }

    @Test
    void tenInfantrySentRoundTheEndOfAWallWalkItInColumnsAndEndApartNearThePoint() {
        var world = field();
        for (int y = 0; y <= 30; y++) {
            world.getPathGrid().setBlocked(30, y, true); // the way bends round its northern end
        }
        var rangers = block(world, RANGER, 100f, 100f, 20f);
        var point = new Coord3D(450f, 110f, 0f);

        GroupMove.REFERENCE.send(world, rangers, point, true);
        var walk = walk(world, rangers, 3000);

        assertTrue(walk.frames() < 3000, "all stopped");
        for (var ranger : rangers) {
            assertFalse(legs(ranger).stoppedShort(), "none short: " + ranger.getPosition());
            assertTrue(ranger.getPosition().distance(point) <= 80f, "within 80 of the point: " + ranger.getPosition());
        }
        assertNoTwoWithin(18f, rangers);
    }

    @Test
    void tenStandingRoundAPointAndClickedThereGatherOntoBlocksOfTheirOwnRoundIt() {
        var world = field();
        var rangers = new ArrayList<GameObject>();
        for (int k = 0; k < 10; k++) {
            double angle = 2.0 * StrictMath.PI * k / 10.0;
            rangers.add(world.spawn(RANGER, new Coord3D(300f + (float) (40.0 * StrictMath.cos(angle)),
                    300f + (float) (40.0 * StrictMath.sin(angle)), 0f), 1));
        }
        var point = new Coord3D(300f, 300f, 0f);

        GroupMove.REFERENCE.send(world, rangers, point, true);
        walk(world, rangers, 1200);

        float sum = 0f;
        for (var ranger : rangers) {
            assertFalse(legs(ranger).stoppedShort(), "none short: " + ranger.getPosition());
            assertTrue(ranger.getPosition().distance(point) < 45f, "round it: " + ranger.getPosition());
            sum += ranger.getPosition().distance(point);
        }
        assertTrue(sum / rangers.size() < 30f, "gathered in from the 40 they stood at: " + sum / rangers.size());
        for (int i = 0; i < rangers.size(); i++) {
            for (int j = i + 1; j < rangers.size(); j++) {
                assertFalse(blockOf(rangers.get(i)).overlaps(blockOf(rangers.get(j))),
                        "blocks of their own: " + rangers.get(i).getPosition() + " and " + rangers.get(j).getPosition());
            }
        }
    }

    private static Block blockOf(GameObject unit) {
        return Block.of(Solid.of(unit.getTemplate()).footprintRadius(), 10f, unit.getPosition().x(),
                unit.getPosition().y());
    }
}
