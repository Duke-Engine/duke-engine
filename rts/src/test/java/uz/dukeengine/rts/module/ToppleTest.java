package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
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
        factory.addTemplate(RtsTemplate.named("Fence").geometry(new Geometry.Cylinder(2f, 5f))
                .module(new ActiveBody.Data(100f))
                .module(new ToppleUpdate.Data(1, 7f, 0.2f, 0.2f, 0.01f, 0.3f, 10f, 30, false, true)).build());
        factory.addTemplate(RtsTemplate.named("Pole").geometry(new Geometry.Cylinder(1f, 10f))
                .module(new ActiveBody.Data(100f))
                .module(new ToppleUpdate.Data(1, 7f, 0.2f, 0.2f, 0.01f, 0.3f, 10f, 30, true, false)).build());
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

    @Test
    void aFenceFallsOnlyToOneSideOfItsFacingKeepingIt() {
        var fence = spawn("Fence", 0f, 0f);
        fence.setOrientation(0f); // along x: its sides are +y (left) and -y (right)
        var topple = fence.findModule(ToppleUpdate.class);

        topple.topple(null, new Coord3D(-10f, 3f, 0f)); // pushed mostly along its facing, a little from the left
        run(10);

        assertEquals(0f, fence.getOrientation(), "kept its facing");
        assertEquals(0f, fence.getPitch(), "not along its facing");
        assertTrue(fence.getRoll() > 0f, "banked to its right, away from the push's side");
    }

    @Test
    void aPushWithNoBounceLiesStillTheFirstTimeItLands() {
        var bouncing = spawn("Tree", 0f, 0f);
        var still = spawn("Tree", 100f, 0f);
        bouncing.findModule(ToppleUpdate.class).topple(null, new Coord3D(-10f, 0f, 0f));
        still.findModule(ToppleUpdate.class).topple(null, new Coord3D(90f, 0f, 0f), 0.2f,
                EnumSet.of(ToppleUpdate.Option.NO_BOUNCE));

        float limit = -(float) (Math.PI / 2 - Math.PI / 64);
        int frames = 0;
        while (still.getPitch() > limit + 1e-4f) {
            run(1);
            frames++;
        }
        assertTrue(still.isEffectivelyDead(), "dead the frame it landed");
        assertFalse(bouncing.isEffectivelyDead(), "the other bounces");
        run(3);
        assertEquals(limit, still.getPitch(), 1e-4f, "and lies still at once");
        assertTrue(bouncing.getPitch() > limit, "bounced up");
        assertTrue(frames > 1);
    }

    @Test
    void aPoleKilledWhenItStartsTopplingDiesTheFrameItIsPushed() {
        var pole = spawn("Pole", 0f, 0f);
        var topple = pole.findModule(ToppleUpdate.class);

        topple.topple(null, new Coord3D(-10f, 0f, 0f));

        assertTrue(pole.isEffectivelyDead(), "dead at once");
        assertFalse(topple.toppled(), "and never falls");
        run(1);
        assertEquals(1, died.size());
        assertEquals(0f, pole.getPitch());
    }
}
