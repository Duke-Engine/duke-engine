package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;

/**
 * A tree pushed over by what drives past it, as the reference's are: away from the vehicle, falling to nearly flat,
 * dying of it, sinking and gone.
 */
class ToppleTest {

    private CombatTest.CombatLogic logic;
    private int player;
    private final List<ObjectDied> died = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(RtsModules.withDefaults());
        factory.addTemplate(RtsTemplate.named("Tank").geometry(new Geometry.Cylinder(5f, 4f))
                .module(new ActiveBody.Data(500f)).module(new MoveUpdate.Data(30f))
                .module(new CrushUpdate.Data(2)).build());
        factory.addTemplate(RtsTemplate.named("Truck").geometry(new Geometry.Cylinder(5f, 4f))
                .module(new ActiveBody.Data(500f)).module(new MoveUpdate.Data(30f))
                .module(new CrushUpdate.Data(1)).build());
        factory.addTemplate(RtsTemplate.named("Tree").geometry(new Geometry.Cylinder(2f, 20f))
                .module(new ActiveBody.Data(100f))
                .module(new ToppleUpdate.Data(1, 7f, 0.2f, 0.2f, 0.01f, 0.3f, 10f, 30)).build());
        logic = new CombatTest.CombatLogic(factory);
        logic.init();
        player = logic.getPlayerList().addPlayer("Red").getIndex();
    }

    private GameObject spawn(String template, float x, float y) {
        var thing = logic.createObject(logic.getThingFactory().findTemplate(template));
        thing.setPlayerIndex(player);
        thing.setPosition(new Coord3D(x, y, 0f));
        return thing;
    }

    private void run(int frames) {
        for (int frame = 0; frame < frames; frame++) {
            logic.update();
            logic.drainEvents().stream().filter(ObjectDied.class::isInstance).map(ObjectDied.class::cast)
                    .forEach(died::add);
        }
    }

    @Test
    void aCrushingVehicleDrivenPastATreeTopplesItAwaySinksItAndItIsGone() {
        var tank = spawn("Tank", 0f, 0f);
        var tree = spawn("Tree", 40f, 8f); // beside the tank's way, within its 5 and the tree's 7
        var topple = tree.findModule(ToppleUpdate.class);

        tank.findModule(MoveUpdate.class).moveTo(new Coord3D(80f, 0f, 0f));
        for (int frame = 0; frame < 200 && !topple.toppled(); frame++) {
            run(1);
        }
        assertTrue(topple.toppled(), "pushed over as the tank came by");
        var from = tank.getPosition();
        float away = (float) StrictMath.atan2(8f - from.y(), 40f - from.x());
        assertEquals(away, tree.getOrientation(), 0.2f, "away from the tank");

        float pitch = 0f;
        for (int frame = 0; frame < 120 && died.isEmpty(); frame++) {
            run(1);
            assertTrue(tree.getPitch() <= pitch + 0.2f, "falling, bounces aside");
            pitch = tree.getPitch();
        }
        assertEquals(1, died.size(), "dead of it once it lay still");
        assertSame(ToppleUpdate.TOPPLED, died.getFirst().deathType());
        assertEquals(tank.getId(), died.getFirst().killer());
        assertEquals(-(float) (Math.PI / 2 - Math.PI / 64), tree.getPitch(), 1e-3f, "nearly flat");

        run(15);
        assertEquals(-5f, tree.getPosition().z(), 1e-3f, "half its 10 sunk halfway through its 30 frames");
        run(15);
        assertNull(logic.findObject(tree.getId()), "and gone");
    }

    @Test
    void aVehicleNoStrongerThanItsThresholdDrivesPast() {
        var truck = spawn("Truck", 0f, 0f);
        var tree = spawn("Tree", 40f, 8f);

        truck.findModule(MoveUpdate.class).moveTo(new Coord3D(80f, 0f, 0f));
        run(150);

        assertFalse(tree.findModule(ToppleUpdate.class).toppled(), "a truck of 1 pushes over nothing of 1");
    }
}
