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

    private static void assertNear(Coord3D expected, Coord3D actual, String message) {
        assertEquals(expected.x(), actual.x(), 1e-3f, message);
        assertEquals(expected.y(), actual.y(), 1e-3f, message);
        assertEquals(expected.z(), actual.z(), 1e-3f, message);
    }
}
