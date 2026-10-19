package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.SceneryFootprint;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/**
 * A walker sent where its body cannot go but by a gap too narrow for it, ordered there again and again as a game's
 * walk orders it: it must not stand wedged for ever, still and "moving" (E102).
 */
class WedgedWalkerTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory thingFactory) {
            super(thingFactory);
        }

        @Override
        protected void simulate() {
        }
    }

    private static final ThingTemplate CHAMPION = ThingTemplate.named("Champion")
            .geometry(new Geometry.Cylinder(8f, 16f))
            .module(new ActiveBody.Data(100f))
            .module(new MoveUpdate.Data(60f))
            .build();

    @Test
    void aWalkerOrderedThroughAGapTooNarrowForItIsNeverStillAndMovingForEver() {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(CHAMPION);
        var logic = new TestLogic(things);
        logic.init();
        var grid = new PathGrid(40, 24);
        for (int cy = 0; cy < 24; cy++) {
            if (cy != 10 && cy != 11) {
                grid.setBlocked(10, cy, true); // a wall, its one opening two cells wide
            }
        }
        logic.setPathGrid(grid, 2);
        logic.setRouteSectors(8);
        // Two trunks across the opening, 11 apart: no way through for a body of 8 but squeezing.
        logic.setSceneryFootprints(List.of(new SceneryFootprint(105f, 105f - 5.5f, 1.5f),
                new SceneryFootprint(105f, 105f + 5.5f, 1.5f)));
        var champion = logic.createObject(CHAMPION);
        champion.setPosition(new Coord3D(40f, 105f, 0f));
        var goal = new Coord3D(300f, 105f, 0f);
        var legs = champion.findModule(MoveUpdate.class);
        int stillAndMoving = 0;
        int longest = 0;
        Coord3D was = champion.getPosition();
        for (int frame = 0; frame < 3000; frame++) {
            if (frame % 60 == 0) {
                legs.moveTo(goal); // the game's walk: the same goal again every two seconds
            }
            logic.update();
            var at = champion.getPosition();
            boolean still = Math.abs(at.x() - was.x()) < 0.01f && Math.abs(at.y() - was.y()) < 0.01f;
            stillAndMoving = still && legs.isMoving() ? stillAndMoving + 1 : 0;
            longest = Math.max(longest, stillAndMoving);
            was = at;
        }
        assertTrue(longest < 600, "still and moving for " + longest + " frames, at " + champion.getPosition());
    }
}
