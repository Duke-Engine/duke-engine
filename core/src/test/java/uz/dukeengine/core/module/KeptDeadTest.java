package uz.dukeengine.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectTemplate;
import uz.dukeengine.core.thing.ThingFactory;

/** A dead thing kept in the world while its death plays out, and one that is not, leaving the frame it dies. */
class KeptDeadTest {

    /** The reference's slow death: keeps its thing sixty frames after it dies, running, then destroys it. */
    static final class SlowDeath extends UpdateModule implements KeepsDead {
        record Data() implements ModuleData {
        }

        int updatesDead;

        SlowDeath(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean keepsDead() {
            return true;
        }

        @Override
        public void update() {
            var owner = getOwner();
            if (!owner.isEffectivelyDead()) {
                return;
            }
            if (++updatesDead == 60) {
                ((GameLogic) owner.getWorld()).destroyObject(owner);
            }
        }
    }

    private GameLogic world;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(ModuleFactory.withDefaults()
                .register(SlowDeath.Data.class, (owner, data) -> new SlowDeath(owner)));
        factory.addTemplate(ObjectTemplate.named("Soldier").geometry(new Geometry.Cylinder(5f, 10f))
                .module(new ActiveBody.Data(100f)).module(new SlowDeath.Data()).build());
        factory.addTemplate(ObjectTemplate.named("Rifleman").geometry(new Geometry.Cylinder(5f, 10f))
                .module(new ActiveBody.Data(100f)).build());
        factory.addTemplate(ObjectTemplate.named("Walker").geometry(new Geometry.Cylinder(5f, 10f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build());
        world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
    }

    @Test
    void itIsToldDeadOnceStaysSixtyFramesRunningAndInNobodysWayThenGoes() {
        var soldier = world.spawn(world.findTemplate("Soldier"), new Coord3D(100f, 100f, 0f), 1);
        var walker = world.spawn(world.findTemplate("Walker"), new Coord3D(100f, 70f, 0f), 2);
        world.update();
        world.drainEvents();

        soldier.getBody().damage(500f);
        int deaths = 0;
        int frames = 0;
        while (world.findObject(soldier.getId()) != null && frames < 200) {
            world.update();
            frames++;
            deaths += (int) world.drainEvents().stream().filter(ObjectDied.class::isInstance).count();
            if (world.findObject(soldier.getId()) != null) {
                assertTrue(world.getVisibleObjects(1).contains(soldier), "still there to be drawn at " + frames);
                assertNull(world.findBlocker(walker, new Coord3D(100f, 100f, 0f)), "in nobody's way");
            }
        }

        assertEquals(1, deaths, "its death told once, the frame it died");
        assertEquals(60, soldier.findModule(SlowDeath.class).updatesDead, "its modules ran every frame it lay");
        assertEquals(60, frames, "gone the frame after it was destroyed, told nothing more");
    }

    @Test
    void oneWithNothingToKeepItLeavesTheFrameItDiesAsBefore() {
        var rifleman = world.spawn(world.findTemplate("Rifleman"), new Coord3D(100f, 100f, 0f), 1);
        world.update();

        rifleman.getBody().damage(500f);
        world.update();

        assertNull(world.findObject(rifleman.getId()));
        assertNotNull(world.drainEvents().stream().filter(ObjectDied.class::isInstance).findFirst().orElse(null));
    }
}
