package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * Scenery with a footprint stands in the way though no thing of the simulation's stands for it: a route keeps a body
 * clear of it by its true distance, closing no cell, and a walker is kept out of it by its shape — so on a grid walked
 * finer than the map a body goes between two trunks where it fits between them.
 */
class SceneryFootprintTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private static final ThingTemplate WALKER = ThingTemplate.named("Walker")
            .geometry(new Geometry.Cylinder(4f, 9f))
            .module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(60f))
            .build();

    private static final Coord3D START = new Coord3D(25f, 105f, 0f);
    private static final Coord3D GOAL = new Coord3D(185f, 105f, 0f);
    private static final SceneryFootprint BOULDER = new SceneryFootprint(105f, 105f, 12f);

    private static TestLogic logic() {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(WALKER);
        var logic = new TestLogic(things);
        logic.init();
        logic.setPathGrid(new PathGrid(20, 20));
        return logic;
    }

    private static GameObject walker(TestLogic logic, Coord3D at) {
        var walker = logic.createObject(WALKER);
        walker.setPosition(at);
        return walker;
    }

    /** The duke-dungeon's measures: trunks 1.5 across, a knight 5 and a champion 8, on a map walked on cells of 5. */
    private static final ThingTemplate KNIGHT = ThingTemplate.named("Knight")
            .geometry(new Geometry.Cylinder(5f, 12f))
            .module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(60f))
            .build();
    private static final ThingTemplate CHAMPION = ThingTemplate.named("Champion")
            .geometry(new Geometry.Cylinder(8f, 16f))
            .module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(60f))
            .build();
    private static final Coord3D WEST = new Coord3D(20f, 52.5f, 0f);
    private static final Coord3D EAST = new Coord3D(100f, 52.5f, 0f);
    /** Past the wall where no straight line from {@link #WEST} goes between the trunks: only a route's cells do. */
    private static final Coord3D BEYOND = new Coord3D(100f, 30f, 0f);

    /**
     * A wall down the map's sixth column of cells with two openings: one between y 40 and 60 with two trunks across it
     * {@code apart} between their middles, and one far along it, between 80 and 110, a body of any size goes through.
     */
    private static TestLogic walled(float apart, int cellsPerCell) {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(KNIGHT);
        things.addTemplate(CHAMPION);
        var logic = new TestLogic(things);
        logic.init();
        var grid = new PathGrid(20, 12);
        for (int cy = 0; cy < 12; cy++) {
            if (cy != 4 && cy != 5 && (cy < 8 || cy > 10)) {
                grid.setBlocked(5, cy, true);
            }
        }
        logic.setPathGrid(grid, cellsPerCell);
        logic.setSceneryFootprints(List.of(new SceneryFootprint(55f, 52.5f - apart / 2f, 1.5f),
                new SceneryFootprint(55f, 52.5f + apart / 2f, 1.5f)));
        return logic;
    }

    private static float highest(Path path) {
        float highest = 0f;
        for (var waypoint : path.getWaypoints()) {
            highest = Math.max(highest, waypoint.y());
        }
        return highest;
    }

    private static float highestOnTheWay(ThingTemplate template, float apart, int cellsPerCell) {
        var logic = walled(apart, cellsPerCell);
        var body = logic.createObject(template);
        body.setPosition(WEST);
        return highest(logic.findPath(body, BEYOND));
    }

    private static float detour(Path path) {
        float worst = 0f;
        for (var waypoint : path.getWaypoints()) {
            worst = Math.max(worst, Math.abs(waypoint.y() - START.y()));
        }
        return worst;
    }

    @Test
    void aPieceOfSceneryWithAFootprintIsRoutedRound() {
        var logic = logic();
        logic.setSceneryFootprints(List.of(BOULDER));

        var path = logic.findPath(START, GOAL);
        var grid = logic.getPathGrid();
        assertFalse(path.isEmpty());
        assertTrue(detour(path) > 15f, "round the boulder, not through it");
        for (var waypoint : path.getWaypoints()) {
            assertFalse(grid.isBlocked(grid.toCellX(waypoint), grid.toCellY(waypoint)), "on open ground: " + waypoint);
        }
    }

    @Test
    void sceneryThatStandsInNobodysWayLeavesTheRouteStraight() {
        var logic = logic();
        logic.setSceneryFootprints(List.of());

        assertTrue(detour(logic.findPath(START, GOAL)) < 15f);
    }

    @Test
    void aWalkerIsWalkedRoundItAndNeverStandsInIt() {
        var logic = logic();
        logic.setSceneryFootprints(List.of(BOULDER));
        var walker = walker(logic, START);
        walker.findModule(MoveUpdate.class).moveTo(GOAL);

        float deepest = 0f;
        for (int frame = 0; frame < 400; frame++) {
            logic.update();
            float gap = walker.getPosition().distance(new Coord3D(BOULDER.x(), BOULDER.y(), 0f)) - 4f
                    - BOULDER.radius();
            deepest = Math.max(deepest, -gap);
        }
        assertTrue(deepest <= 1f + 1e-3f, "never more than a touch into it: " + deepest);
        assertTrue(walker.getPosition().distance(GOAL) < 5f, "and it arrives: " + walker.getPosition());
    }

    @Test
    void aWalkerStandingInItIsLetOutRatherThanHeld() {
        var logic = logic();
        logic.setSceneryFootprints(List.of(BOULDER));
        var walker = walker(logic, new Coord3D(108f, 105f, 0f));
        walker.findModule(MoveUpdate.class).moveTo(GOAL);

        for (int frame = 0; frame < 400; frame++) {
            logic.update();
        }
        assertTrue(walker.getPosition().distance(GOAL) < 5f, "out of it and on: " + walker.getPosition());
    }

    @Test
    void onAGridWalkedFinerAKnightGoesBetweenTrunksFourteenApartAndAChampionGoesRound() {
        assertTrue(highestOnTheWay(KNIGHT, 14f, 2) < 70f, "the knight between them");
        assertTrue(highestOnTheWay(CHAMPION, 14f, 2) > 80f, "the champion by the far opening");
        assertTrue(highestOnTheWay(KNIGHT, 14f, 1) > 80f, "on the map's own cells, none between them holds it");
    }

    @Test
    void elevenApartTheyStopTheKnightToo() {
        assertTrue(highestOnTheWay(KNIGHT, 11f, 2) > 80f);
    }

    @Test
    void theKnightWalksBetweenThemWithoutTouchingEither() {
        var logic = walled(14f, 2);
        var knight = logic.createObject(KNIGHT);
        knight.setPosition(WEST);
        knight.findModule(MoveUpdate.class).moveTo(EAST);

        float nearest = Float.MAX_VALUE;
        for (int frame = 0; frame < 300; frame++) {
            logic.update();
            for (float y : new float[] {45.5f, 59.5f}) {
                nearest = Math.min(nearest, knight.getPosition().distance(new Coord3D(55f, y, 0f)) - 5f - 1.5f);
            }
        }
        assertTrue(nearest >= 0f, "never into a trunk: " + nearest);
        assertTrue(knight.getPosition().distance(EAST) < 5f, "and through: " + knight.getPosition());
    }
}
