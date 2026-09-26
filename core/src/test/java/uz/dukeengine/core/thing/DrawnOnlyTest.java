package uz.dukeengine.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;

/** A thing's lift, its turn beside its facing and its wheels' corners: drawn only, as its pitch and roll are. */
class DrawnOnlyTest {

    private static final ThingTemplate TANK = ThingTemplate.named("Tank").geometry(new Geometry.Cylinder(5f, 6f))
            .module(new ActiveBody.Data(100f)).build();

    private static GameLogic world() {
        var things = new ThingFactory(ModuleFactory.withDefaults());
        things.addTemplate(TANK);
        var world = new GameLogic(things) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        return world;
    }

    @Test
    void aLiftATurnAndCornersLeaveItsHeightItsFacingAndTheChecksumAsWithNone() {
        var plain = world();
        var lifted = world();
        for (var world : new GameLogic[] {plain, lifted}) {
            var tank = world.createObject(TANK);
            tank.setPosition(new Coord3D(100f, 100f, 0f));
            tank.setOrientation(0.7f);
        }
        var tank = lifted.getObjects().getFirst();
        tank.setLift(3f);
        tank.setYaw(0.1f);
        tank.setCorners(new Corners(-2f, 0f, 0f, 0f));
        plain.update();
        lifted.update();

        assertEquals(0f, tank.getPosition().z(), "its height");
        assertEquals(0.7f, tank.getOrientation(), "its facing");
        assertEquals(plain.checksum(), lifted.checksum(), "and the checksum as with none");
        assertEquals(3f, tank.getLift());
    }
}
