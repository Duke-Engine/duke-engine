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
}
