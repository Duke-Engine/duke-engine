package uz.dukeengine.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleFactory;

/** A thing's bones where its model is drawn: in its own frame, turned with it in the world, by its words' model. */
class BonesTest {

    record Building(String name, List<ModuleData> modules, String model, float facing, Map<String, String> models)
            implements Drawn {
    }

    private static GameObject barracks(float facing, float orientation) {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        var template = new Building("Barracks", List.of(), "models/bones/barracks.gltf", facing,
                Map.of("DAMAGED", "models/bones/barracks_damaged.gltf"));
        factory.addTemplate(template);
        var world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        var thing = world.spawn(template, new Coord3D(100f, 100f, 0f), 1);
        thing.setOrientation(orientation);
        return thing;
    }

    private static void assertNear(Coord3D expected, Coord3D actual) {
        assertEquals(expected.x(), actual.x(), 1e-3f);
        assertEquals(expected.y(), actual.y(), 1e-3f);
        assertEquals(expected.z(), actual.z(), 1e-3f);
    }

    /**
     * A bone turned a quarter round in its file points a quarter round from its thing's facing, the way a thing turns,
     * and turns with its thing in the world; one tipped up in its file points up by as much, straight ahead.
     */
    @Test
    void aBonePointsAsItsFileTurnsIt() {
        var factory = new ThingFactory(ModuleFactory.withDefaults());
        var template = new Building("Airfield", List.of(), "models/bones/pointing.gltf", 0f, Map.of());
        factory.addTemplate(template);
        var world = new GameLogic(factory) {
            @Override
            protected void simulate() {
            }
        };
        world.init();
        var thing = world.spawn(template, new Coord3D(100f, 100f, 0f), 1);

        var quarter = Bones.pointingInFrame(thing, "Quarter");
        assertEquals(Math.PI / 2, quarter.turn(), 1e-5, "a quarter round from its facing");
        assertEquals(0.0, quarter.tilt(), 1e-5, "level");
        thing.setOrientation(0.5f);
        assertEquals(0.5 + Math.PI / 2, Bones.pointingInWorld(thing, "Quarter").turn(), 1e-5, "turned with it");

        var tipped = Bones.pointingInFrame(thing, "Tipped");
        assertEquals(Math.PI / 4, tipped.tilt(), 1e-5, "tipped up an eighth");
        assertEquals(0.0, tipped.turn(), 1e-5, "straight ahead");
        assertNull(Bones.pointingInFrame(thing, "Nowhere"), "a missing bone says so");
    }

    @Test
    void aBoneStandsInItsThingsFrameAsItsModelIsDrawn() {
        assertNear(new Coord3D(1f, 20f, 10f), Bones.inFrame(barracks(0f, 0f), "EXITSTART"));
        assertNear(new Coord3D(20f, -1f, 10f), Bones.inFrame(barracks(90f, 0f), "EXITSTART"),
                "a model turned a quarter in its file, turned with it");
    }

    @Test
    void aTurnedThingsBoneIsTurnedWithIt() {
        assertNear(new Coord3D(80f, 101f, 10f), Bones.inWorld(barracks(0f, (float) (Math.PI / 2)), "EXITSTART"));
    }

    @Test
    void theSameOnTwoRunsAndTheWordsModelWhereTheyHold() {
        var one = Bones.inWorld(barracks(0f, 0.7f), "EXITSTART");
        var two = Bones.inWorld(barracks(0f, 0.7f), "EXITSTART");
        assertEquals(Float.floatToIntBits(one.x()), Float.floatToIntBits(two.x()));
        assertEquals(Float.floatToIntBits(one.y()), Float.floatToIntBits(two.y()));

        assertNear(new Coord3D(7f, 0f, 0f), Bones.inFrame(barracks(0f, 0f), "EXITSTART", Set.of("DAMAGED")));
        assertNull(Bones.inFrame(barracks(0f, 0f), "DOCKACTION"), "a missing bone says so");
    }

    /**
     * A bone of one of its draw layers' models, at its clip's last frame: the barracks turned a quarter carries a
     * raised antenna's FXMain up 23.97, where the antenna model stands on it.
     */
    @Test
    void aBoneOfADrawLayersModelAtItsClipsLastFrameIsWhereTheLayerIsDrawn() {
        var thing = barracks(0f, (float) (Math.PI / 2));

        assertNear(new Coord3D(100f, 100f, 23.97f),
                Bones.inWorld(thing, "models/bones/antenna.gltf", "fxmain", "Raise", null, 0f), "raised");
        assertNear(new Coord3D(100f, 104f, 0f),
                Bones.inWorld(thing, "models/bones/antenna.gltf", "Dish", null, null, 0f), "turned with the thing");
    }

    private static void assertNear(Coord3D expected, Coord3D actual, String message) {
        assertEquals(expected.x(), actual.x(), 1e-3f, message);
        assertEquals(expected.y(), actual.y(), 1e-3f, message);
        assertEquals(expected.z(), actual.z(), 1e-3f, message);
    }
}
