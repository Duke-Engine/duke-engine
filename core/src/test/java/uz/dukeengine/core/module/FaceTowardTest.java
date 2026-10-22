package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectTemplate;
import uz.dukeengine.core.thing.ThingFactory;

/** A unit turned on the spot to face a point before it acts — the reference's {@code NeedToFaceTarget}. */
class FaceTowardTest {

    private GameLogic world;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(ObjectTemplate.named("Walker").geometry(new Geometry.Cylinder(5f, 10f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f, 90f)).build());
        factory.addTemplate(ObjectTemplate.named("Hover").geometry(new Geometry.Cylinder(5f, 10f))
                .module(new ActiveBody.Data(100f))
                .module(new FlyUpdate.Data(FlyUpdate.Kind.HOVERING, 30f, 0f, 0f, 0f, 90f, 50f, 20f)).build());
        world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
    }

    @Test
    void aWalkerTurningNinetyDegreesASecondFacesAPointBehindItInTwoSecondsWithoutMoving() {
        var unit = world.spawn(world.findTemplate("Walker"), new Coord3D(100f, 100f, 0f), 1);
        world.update();
        var at = unit.getPosition();
        var behind = new Coord3D(50f, 100f, 0f);
        var legs = unit.getLocomotor();

        int turning = 0;
        while (legs.faceToward(behind) && turning < 200) {
            world.update();
            turning++;
            assertEquals(at, unit.getPosition(), "on the spot");
            assertFalse(legs.isMoving());
        }

        assertEquals(60, turning, "180 degrees at 3 a frame");
        assertEquals((float) StrictMath.PI, unit.getOrientation(), 1e-6f, "facing it");
        assertFalse(legs.faceToward(behind), "and stays faced");
    }

    @Test
    void anOrderToMoveEndsTheTurn() {
        var unit = world.spawn(world.findTemplate("Walker"), new Coord3D(100f, 100f, 0f), 1);
        world.update();
        var legs = unit.getLocomotor();
        assertTrue(legs.faceToward(new Coord3D(50f, 100f, 0f)));
        world.update();
        float turned = unit.getOrientation();

        legs.moveTo(new Coord3D(100f, 100f, 0f));
        legs.stop();
        world.update();

        assertEquals(turned, unit.getOrientation(), "the turn given up where it got to");
    }

    @Test
    void aHoveringThingTurnsWhereItHangs() {
        var hover = world.spawn(world.findTemplate("Hover"), new Coord3D(100f, 100f, 50f), 1);
        world.update();
        var at = hover.getPosition();
        var legs = hover.getLocomotor();

        int turning = 0;
        while (legs.faceToward(new Coord3D(100f, 200f, 0f)) && turning < 200) {
            world.update();
            turning++;
        }

        assertEquals(30, turning, "90 degrees at 3 a frame");
        assertEquals((float) (StrictMath.PI / 2), hover.getOrientation(), 1e-6f);
        assertEquals(at.x(), hover.getPosition().x(), 1e-4f);
        assertEquals(at.y(), hover.getPosition().y(), 1e-4f);
    }
}
