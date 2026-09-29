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
 * Scenery with a footprint stands in the way though no thing of the simulation's stands for it: its cells are closed to
 * routes as a still thing's outline closes them, and a walker is kept out of it by its shape.
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
}
