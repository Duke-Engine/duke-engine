package uz.duke.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.duke.core.thing.Geometry;
import uz.duke.core.thing.Kind;

class BinderTest {

    enum Channel { EFFECTS, MUSIC }

    record At(float x, float y, float z) {
    }

    record Held(String model, String in, At at) {
    }

    record Generation(int mapWidth, int mapHeight, boolean roomy) {
        static final Generation DEFAULTS = new Generation(50, 36, true);
    }

    record Sound(String name, Channel channel, List<String> files) {
        Sound {
            if (files.isEmpty()) {
                throw new IllegalArgumentException("a sound needs at least one file");
            }
        }
    }

    interface Part {
    }

    record Wheel(int size) implements Part {
    }

    record Crate(String name, float weight, Set<Kind> kindOf, Geometry geometry, List<Held> held,
            Generation generation, List<Part> parts, int colour) {
    }

    private static <R> R bind(String text, Class<R> type) {
        var binder = new Binder().vocabulary(Part.class, Map.of("Wheel", Wheel.class));
        return binder.bind(DukeText.parse(text, "crate.duke").getFirst(), type);
    }

    @Test
    void eachKeyFillsTheComponentOfItsNameAndEachInnerBlockTheOneItsWordNames() {
        var crate = bind("""
                Crate
                  Name = Box
                  weight = 2.5
                  KindOf = [PROP, SELECTABLE]
                  Colour = 0x8E5BD0
                  Cylinder
                    Radius = 6
                    Height = 16
                  End
                  Held
                    Model = models/bow.gltf
                    In = handslot.l
                    At = [0.12, 0.1, -0.22]
                  End
                  Held
                    Model = models/quiver.gltf
                  End
                  Generation
                    MapWidth = 80
                  End
                  Wheel
                    Size = 3
                  End
                End
                """, Crate.class);

        assertEquals("Box", crate.name());
        assertEquals(2.5f, crate.weight());
        assertEquals(Set.of(Kind.of("PROP"), Kind.of("SELECTABLE")), crate.kindOf());
        assertEquals(0x8E5BD0, crate.colour());
        assertEquals(new Geometry.Cylinder(6, 16), crate.geometry());
        assertEquals(List.of(new Held("models/bow.gltf", "handslot.l", new At(0.12f, 0.1f, -0.22f)),
                new Held("models/quiver.gltf", null, null)), crate.held());
        assertEquals(new Generation(80, 36, true), crate.generation(), "what it leaves out is the default");
        assertEquals(List.of(new Wheel(3)), crate.parts());
    }

    @Test
    void whatTheRecordRefusesIsAnErrorWhereItWasWritten() {
        assertError("crate.duke:2: 'Sound' has no field 'Volume'", "Sound\n  Volume = 3\nEnd\n", Sound.class);
        assertError("crate.duke:1: 'Sound': a sound needs at least one file", "Sound\n  Name = x\nEnd\n", Sound.class);
        assertError("crate.duke:2: 'Channel' is one of [EFFECTS, MUSIC], not 'Loud'",
                "Sound\n  Channel = Loud\nEnd\n", Sound.class);
        assertError("crate.duke:2: 'Files' is a list: write it [a, b]", "Sound\n  Files = a.ogg\nEnd\n", Sound.class);
        assertError("crate.duke:2: 'Weight' is a number, not 'heavy'", "Crate\n  Weight = heavy\nEnd\n", Crate.class);
        assertError("crate.duke:2: 'Crate' holds no block 'Lid'", "Crate\n  Lid\n  End\nEnd\n", Crate.class);
        assertError("crate.duke:4: 'Generation' is written twice in 'Crate'",
                "Crate\n  Generation\n  End\n  Generation\n  End\nEnd\n", Crate.class);
    }

    private static void assertError(String message, String text, Class<?> type) {
        var error = assertThrows(DataException.class, () -> bind(text, type));
        assertEquals(message, error.getMessage());
    }
}
