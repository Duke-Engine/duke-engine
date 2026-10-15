package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.TraversalLink;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.core.thing.ThingTemplate;

/** A gap no walk crosses, a link over it taken in its frames, and a link one way never taken the other (E81). */
class TraversalLinkTest {

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

    /** A chasm down x 100 to 120, all the way across; a jump over it from (95, 100) to (125, 100), 20 frames. */
    private static TestLogic world(boolean bothWays) {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(HERO);
        var logic = new TestLogic(things);
        logic.init();
        var grid = new PathGrid(25, 20);
        for (int cy = 0; cy < 20; cy++) {
            grid.setBlocked(10, cy, true);
            grid.setBlocked(11, cy, true);
        }
        logic.setPathGrid(grid);
        logic.setTraversalLinks(List.of(new TraversalLink(new Coord3D(95f, 100f, 0f), new Coord3D(125f, 100f, 0f),
                "jump", 20, 8f, "Jump", bothWays, false)));
        return logic;
    }

    private static GameObject hero(TestLogic logic, float x) {
        var hero = logic.createObject(HERO);
        hero.setPosition(new Coord3D(x, 100f, 0f));
        return hero;
    }

    @Test
    void aMoveToTheFarSideGoesOverTheLinkInItsFrames() {
        var logic = world(false);
        var hero = hero(logic, 50f);
        hero.getLocomotor().moveTo(new Coord3D(170f, 100f, 0f));
        boolean jumped = false;
        for (int frame = 0; frame < 200; frame++) {
            logic.update();
            jumped |= "Jump".equals(hero.getBusyClip());
        }
        assertTrue(jumped, "busy at the jump while it crossed");
        assertEquals(170f, hero.getPosition().x(), 6f,
                "arrived on the far side, at its place there: " + hero.getPosition());
    }

    @Test
    void aLinkOneWayFromAIsNeverTakenFromB() {
        var logic = world(false);
        var hero = hero(logic, 160f);
        hero.getLocomotor().moveTo(new Coord3D(40f, 100f, 0f));
        for (int frame = 0; frame < 200; frame++) {
            logic.update();
        }
        assertTrue(hero.getPosition().x() > 115f, "kept to its own side: " + hero.getPosition());
    }
}
