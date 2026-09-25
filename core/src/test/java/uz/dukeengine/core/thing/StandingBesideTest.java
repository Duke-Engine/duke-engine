package uz.dukeengine.core.thing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.ModuleFactory;

/** Standing beside a thing means beside its outline: at a box's side, off its corner, and round a circle. */
class StandingBesideTest {

    private GameLogic world;

    @BeforeEach
    void setUp() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        factory.addTemplate(ObjectTemplate.named("Saboteur").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).build());
        factory.addTemplate(ObjectTemplate.named("SupplyCenter").geometry(new Geometry.Box(40f, 30f, 20f))
                .module(new ActiveBody.Data(1000f)).build());
        factory.addTemplate(ObjectTemplate.named("Silo").geometry(new Geometry.Cylinder(25f, 20f))
                .module(new ActiveBody.Data(1000f)).build());
        world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
    }

    /** How far from {@code target}'s outline a saboteur standing where he is sent stands. */
    private float gapAfterSending(String target, float fromX, float fromY) {
        var thing = world.spawn(world.findTemplate(target), new Coord3D(500f, 500f, 0f), 2);
        var saboteur = world.spawn(world.findTemplate("Saboteur"), new Coord3D(fromX, fromY, 0f), 1);
        var spot = world.standingNextTo(saboteur, thing);
        return Footprint.of(saboteur, spot).separation(Footprint.of(thing));
    }

    private void assertBeside(float gap, String where) {
        assertTrue(gap >= -1e-3f && gap <= world.cellSize() + 1e-3f, where + ": " + gap + " off its outline");
    }

    @Test
    void atABoxsSide() {
        assertBeside(gapAfterSending("SupplyCenter", 500f, 300f), "below its long side");
    }

    @Test
    void offABoxsCorner() {
        assertBeside(gapAfterSending("SupplyCenter", 700f, 400f), "off a corner, where it stood 13 off, needing 10");
    }

    @Test
    void roundACircle() {
        assertBeside(gapAfterSending("Silo", 620f, 610f), "round a silo");
    }
}
