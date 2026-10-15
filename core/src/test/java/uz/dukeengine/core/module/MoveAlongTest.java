package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A held direction walked with no route: its speed a second, stopped at once, sliding along a wall, alike twice (E74). */
class MoveAlongTest {

    static final class TestLogic extends GameLogic {
        TestLogic(ThingFactory things) {
            super(things);
        }

        @Override
        protected void simulate() {
        }
    }

    /** Sixty units a second: two a frame. */
    private static final ThingTemplate HERO = ThingTemplate.named("Hero").geometry(new Geometry.Cylinder(4f, 9f))
            .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(60f)).build();

    private static TestLogic logic(boolean wall) {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(HERO);
        var logic = new TestLogic(things);
        logic.init();
        var grid = new PathGrid(20, 20);
        if (wall) {
            for (int cx = 0; cx < 20; cx++) {
                grid.setBlocked(cx, 12, true); // a wall along x, from y 120
            }
        }
        logic.setPathGrid(grid);
        return logic;
    }

    private static GameObject hero(TestLogic logic) {
        var hero = logic.createObject(HERO);
        hero.setPosition(new Coord3D(100f, 50f, 0f));
        return hero;
    }

    @Test
    void sixtyFourWalksAlongYItsSpeedASecondAndNoneStopsAtOnce() {
        var logic = logic(false);
        var hero = hero(logic);
        hero.getLocomotor().moveAlong(64);
        for (int frame = 0; frame < 30; frame++) {
            logic.update();
        }
        assertEquals(100f, hero.getPosition().x(), 1e-3f);
        assertEquals(110f, hero.getPosition().y(), 1e-3f, "sixty units in a second, along +y");
        assertTrue(hero.getLocomotor().isMoving());

        hero.getLocomotor().moveAlong(-1);
        var stopped = hero.getPosition();
        logic.update();
        assertEquals(stopped, hero.getPosition(), "none: stands where it is");
        assertFalse(hero.getLocomotor().isMoving());
    }

    @Test
    void intoAWallAtAnAngleItSlidesAlongIt() {
        var logic = logic(true);
        var hero = hero(logic);
        hero.getLocomotor().moveAlong(32); // up and right, at the wall along x
        for (int frame = 0; frame < 90; frame++) {
            logic.update();
        }
        assertTrue(hero.getPosition().y() < 120f, "kept off the wall: " + hero.getPosition());
        assertTrue(hero.getPosition().x() > 160f, "slid along it: " + hero.getPosition());
    }

    @Test
    void twoRunsWithTheSameOrdersEndAlike() {
        long[] sums = new long[2];
        for (int run = 0; run < 2; run++) {
            var logic = logic(true);
            var hero = hero(logic);
            for (int frame = 0; frame < 120; frame++) {
                if (frame % 20 == 0) {
                    hero.getLocomotor().moveAlong(frame * 7);
                }
                logic.update();
            }
            sums[run] = logic.checksum();
        }
        assertEquals(sums[0], sums[1]);
    }
}
