package uz.duke.core.module;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.GameLogic;
import uz.duke.core.math.Coord3D;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingFactory;
import uz.duke.core.thing.ThingTemplate;

class PathFollowingTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    @Test
    void unitDetoursThroughTheGapInAWall() {
        var thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        var template = ThingTemplate.named("Runner")
                .module("ActiveBody", new ActiveBody.Data(100f))
                .module("MoveUpdate", new MoveUpdate.Data(100f)) // fast, so it finishes in-test
                .build();
        thingFactory.addTemplate(template);

        var logic = new TestLogic(thingFactory);
        logic.init();

        // 10x10 grid of 10-unit cells. Wall at column x=5 for rows 0..8, gap at row 9.
        var grid = new PathGrid(10, 10);
        for (int cy = 0; cy <= 8; cy++) {
            grid.setBlocked(5, cy, true);
        }
        logic.setPathGrid(grid);

        GameObject runner = logic.createObject(template);
        runner.setPosition(new Coord3D(15f, 15f, 0f)); // cell (1,1)
        runner.findModule(MoveUpdate.class).moveTo(new Coord3D(85f, 15f, 0f)); // cell (8,1)

        float maxY = runner.getPosition().y();
        boolean enteredBlocked = false;
        for (int i = 0; i < 400 && runner.findModule(MoveUpdate.class).isMoving(); i++) {
            logic.update();
            var p = runner.getPosition();
            maxY = Math.max(maxY, p.y());
            if (grid.isBlocked(grid.toCellX(p), grid.toCellY(p))) {
                enteredBlocked = true;
            }
        }

        assertFalse(runner.findModule(MoveUpdate.class).isMoving(), "runner should reach its goal");
        assertTrue(runner.getPosition().distance(new Coord3D(85f, 15f, 0f)) < 1f, "arrived at destination");
        assertTrue(maxY > 50f, "runner must detour up toward the gap, not phase through the wall");
        assertFalse(enteredBlocked, "runner must never stand in a blocked cell");
    }
}
