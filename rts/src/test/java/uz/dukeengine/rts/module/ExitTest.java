package uz.dukeengine.rts.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ThingFactory;
import uz.dukeengine.rts.RtsTemplate;

/** A building's passengers come out on clear ground outside it, and walk away at their speed. */
class ExitTest {

    private ProductionTest.TestLogic logic;
    private GameObject bunker;
    private GameObject rifleman;

    private void world() {
        var things = new ThingFactory(RtsModules.withDefaults());
        var building = RtsTemplate.named("Bunker").geometry(new Geometry.Box(30f, 30f, 12f))
                .module(new ActiveBody.Data(1000f)).module(new ContainModule.Data(5)).build();
        var soldier = RtsTemplate.named("Rifleman").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build();
        things.addTemplate(building);
        things.addTemplate(soldier);
        logic = new ProductionTest.TestLogic(things);
        logic.init();
        logic.setPathGrid(new PathGrid(100, 100));
        int usa = logic.getPlayerList().addPlayer("USA").getIndex();
        bunker = logic.spawn(building, new Coord3D(300f, 300f, 0f), usa);
        rifleman = logic.spawn(soldier, new Coord3D(300f, 250f, 0f), usa);
        logic.update();
        assertTrue(bunker.findModule(ContainModule.class).load(rifleman));
    }

    @Test
    void aUnitLetOutOfABuildingStandsOutsideItsFootprint() {
        world();

        bunker.findModule(ContainModule.class).unload(rifleman);

        float gap = Footprint.of(rifleman).separation(Footprint.of(bunker));
        assertTrue(gap > 0f && gap < 2f, "just outside the 60-wide walls, not in them: " + gap);
    }

    @Test
    void outsideItWalksAwayAtItsOwnSpeed() {
        world();
        bunker.findModule(ContainModule.class).unload(rifleman);
        var from = rifleman.getPosition();
        var to = new Coord3D(from.x() + 300f, from.y(), 0f);

        rifleman.getLocomotor().moveTo(to);
        for (int frame = 0; frame < 330; frame++) { // 300 at 30 a second: ten seconds, and a tenth more
            logic.update();
        }

        assertEquals(to.x(), rifleman.getPosition().x(), 1f, "there in the time its speed gives");
    }

    @Test
    void aBuildingThatNamesAnExitPointLetsItsPassengersOutThere() {
        var things = new ThingFactory(RtsModules.withDefaults());
        var building = RtsTemplate.named("Barracks").geometry(new Geometry.Box(30f, 30f, 12f))
                .model("models/bones/barracks.gltf")
                .module(new ActiveBody.Data(1000f))
                .module(new ContainModule.Data(5, null, false, null, false, "EXITSTART")).build();
        var soldier = RtsTemplate.named("Rifleman").geometry(new Geometry.Cylinder(3f, 6f))
                .module(new ActiveBody.Data(100f)).build();
        things.addTemplate(building);
        things.addTemplate(soldier);
        logic = new ProductionTest.TestLogic(things);
        logic.init();
        int usa = logic.getPlayerList().addPlayer("USA").getIndex();
        var barracks = logic.spawn(building, new Coord3D(300f, 300f, 0f), usa);
        var inside = logic.spawn(soldier, new Coord3D(250f, 300f, 0f), usa);
        var exit = uz.dukeengine.core.thing.Bones.inWorld(barracks, "EXITSTART");
        barracks.findModule(ContainModule.class).load(inside);

        barracks.findModule(ContainModule.class).unload(inside);

        assertTrue(exit != null, "the model names its exit");
        assertEquals(exit, inside.getPosition(), "out at it");
    }

    // ---- out along an exit path ----

    /**
     * The round building of the measurement, 50 across, its exit end 34.5 ahead of its middle, two of its side's units
     * standing 40 and 60 ahead of it, and three passengers let out at once: each put at the exit's start and walking to
     * ground of its own by its end.
     */
    private java.util.List<GameObject> threeLetOutAlongThePath(Coord3D rally) {
        var things = new ThingFactory(RtsModules.withDefaults());
        var building = RtsTemplate.named("Garrison").geometry(new Geometry.Cylinder(25f, 20f))
                .model("models/bones/garrison.gltf")
                .module(new ActiveBody.Data(1000f))
                .module(new ProductionUpdate.Data())
                .module(new ContainModule.Data(5, null, false, null, false, null, "EXITSTART", "EXITEND")).build();
        var soldier = RtsTemplate.named("Rifleman").geometry(new Geometry.Cylinder(5f, 6f))
                .module(new ActiveBody.Data(100f)).module(new MoveUpdate.Data(30f)).build();
        things.addTemplate(building);
        things.addTemplate(soldier);
        logic = new ProductionTest.TestLogic(things);
        logic.init();
        logic.setPathGrid(new PathGrid(100, 100));
        int usa = logic.getPlayerList().addPlayer("USA").getIndex();
        var garrison = logic.spawn(building, new Coord3D(300f, 300f, 0f), usa);
        logic.spawn(soldier, new Coord3D(340f, 300f, 0f), usa);
        logic.spawn(soldier, new Coord3D(360f, 300f, 0f), usa);
        var hold = garrison.findModule(ContainModule.class);
        var inside = new java.util.ArrayList<GameObject>();
        for (int n = 0; n < 3; n++) {
            var one = logic.spawn(soldier, new Coord3D(200f, 200f + 20f * n, 0f), usa);
            assertTrue(hold.load(one));
            inside.add(one);
        }
        if (rally != null) {
            garrison.findModule(ProductionUpdate.class).setRallyPoint(rally);
        }
        logic.update();

        hold.unloadAll();
        for (int frame = 0; frame < 450; frame++) {
            logic.update();
        }
        return inside;
    }

    @Test
    void threePassengersLetOutAlongAnExitPathStandApartByItsEnd() {
        var out = threeLetOutAlongThePath(null);

        var end = new Coord3D(334.5f, 300f, 0f);
        for (var one : out) {
            assertTrue(one.getPosition().distance(end) <= 30f, "within 30 of the exit's end: " + one.getPosition());
            for (var other : out) {
                if (other != one) {
                    assertTrue(Footprint.of(one).separation(Footprint.of(other)) >= 0f,
                            "apart: " + one.getPosition() + " and " + other.getPosition());
                }
            }
        }
    }

    @Test
    void withARallyPointEachWalksOnToIt() {
        var rally = new Coord3D(500f, 450f, 0f);
        var out = threeLetOutAlongThePath(rally);

        for (var one : out) {
            assertTrue(one.getPosition().distance(rally) <= 30f, "at the rally point: " + one.getPosition());
        }
    }
}
