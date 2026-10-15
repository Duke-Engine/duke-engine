package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A band the story shuts across a corridor: no route through it, a walker stopped at its edge; opened, through. */
class ShutRegionTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory things) {
            super(things);
        }

        @Override
        protected void simulate() {
        }
    }

    private static final ThingTemplate HERO = ThingTemplate.named("Hero").geometry(new Geometry.Cylinder(3f, 9f))
            .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(60f)).build();

    @Test
    void aShutBandStopsAWalkerAtItsEdgeAndOpenedLetsItThrough() {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(HERO);
        var logic = new TestLogic(things);
        logic.init();
        var grid = new PathGrid(30, 10);
        logic.setPathGrid(grid);
        var band = new ArrayList<int[]>();
        for (int cy = 0; cy < 10; cy++) {
            band.add(new int[] {15, cy});
            band.add(new int[] {16, cy});
        }
        grid.shut("toTheCapital", band);
        var hero = logic.createObject(HERO);
        hero.setPosition(new Coord3D(50f, 50f, 0f));

        hero.getLocomotor().moveTo(new Coord3D(250f, 50f, 0f));
        float furthest = 0f;
        for (int frame = 0; frame < 200; frame++) {
            logic.update();
            furthest = Math.max(furthest, hero.getPosition().x());
        }
        assertTrue(furthest < 150f, "never into the shut band: " + furthest);
        assertTrue(hero.getPosition().x() > 130f, "stopped at its edge: " + hero.getPosition());

        grid.open("toTheCapital");
        hero.getLocomotor().moveTo(new Coord3D(250f, 50f, 0f));
        for (int frame = 0; frame < 200; frame++) {
            logic.update();
        }
        assertEquals(250f, hero.getPosition().x(), 6f, "opened: the same order arrives");
        assertTrue(!grid.isShut("toTheCapital"));
    }
}
