package uz.dukeengine.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Kind;

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
            Generation generation, List<Part> parts, int colour, Map<Channel, Float> volume) {
    }

    private static <R> R bind(String text, Class<R> type) {
        var binder = new Binder().vocabulary(Part.class, Map.of("Wheel", Wheel.class));
        return binder.bind(DukeText.parse(text, "crate.duke").getFirst(), type);
    }

    @Test
    void everyLineFillsTheComponentOfItsName() {
        var crate = bind("""
                Crate
                  Name = Box
                  weight = 2.5
                  KindOf = [PROP, SELECTABLE]
                  Colour = 0x8E5BD0
                  Geometry = Cylinder
                    Radius = 6
                    Height = 16
                  End
                  Held = [
                    Held
                      Model = models/bow.gltf
                      In = handslot.l
                      At = [0.12, 0.1, -0.22]
                    End
                    Held
                      Model = models/quiver.gltf
                    End
                  ]
                  Generation = Generation
                    MapWidth = 80
                  End
                  Parts = [
                    Wheel
                      Size = 3
                    End
                  ]
                  Volume
                    MUSIC = 0.5
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
        assertEquals(Map.of(Channel.MUSIC, 0.5f), crate.volume());
    }

    /** One thing on a line, {@code Skeleton 17 16} — and the same record as a block, where a line is not enough. */
    record Placed(String kind, int x, int y, int facing) {

        public static Placed of(String line) {
            var words = line.strip().split(" ");
            return new Placed(words[0], Integer.parseInt(words[1]), Integer.parseInt(words[2]), 0);
        }
    }

    record Floor(String name, List<Placed> monsters) {
    }

    /**
     * A list of things read from one line each may be written as blocks instead, for the day one of them has
     * more to say than a line holds — the lines it replaces say the same thing.
     */
    @Test
    void aListOfOneLinersMayBeWrittenAsBlocks() {
        var lines = bind("""
                Floor
                  Name = crypt
                  Monsters = [Skeleton 17 16, Ghoul 4 9]
                End
                """, Floor.class);
        var blocks = bind("""
                Floor
                  Name = crypt
                  Monsters = [
                    Placed
                      Kind = Skeleton
                      X = 17
                      Y = 16
                    End
                    Placed
                      Kind = Ghoul
                      X = 4
                      Y = 9
                      Facing = 90
                    End
                  ]
                End
                """, Floor.class);

        assertEquals(List.of(new Placed("Skeleton", 17, 16, 0), new Placed("Ghoul", 4, 9, 0)), lines.monsters());
        assertEquals(new Placed("Skeleton", 17, 16, 0), blocks.monsters().getFirst());
        assertEquals(90, blocks.monsters().get(1).facing(), "and a block may say what a line cannot");
    }

    @Test
    void aRecordWithNothingWrittenInItIsItsWordAlone() {
        var crate = bind("Crate\n  Geometry = Sphere\n  Generation = Generation\nEnd\n", Crate.class);

        assertEquals(new Geometry.Sphere(0), crate.geometry());
        assertEquals(Generation.DEFAULTS, crate.generation());
    }

    @Test
    void whatTheRecordRefusesIsAnErrorWhereItWasWritten() {
        assertError("crate.duke:2: 'Sound' has no field 'Volume'", "Sound\n  Volume = 3\nEnd\n", Sound.class);
        assertError("crate.duke:1: 'Sound': a sound needs at least one file", "Sound\n  Name = x\nEnd\n", Sound.class);
        assertError("crate.duke:2: 'Channel' is one of [EFFECTS, MUSIC], not 'Loud'",
                "Sound\n  Channel = Loud\nEnd\n", Sound.class);
        assertError("crate.duke:2: 'Files' is a list: write it [a, b]", "Sound\n  Files = a.ogg\nEnd\n", Sound.class);
        assertError("crate.duke:2: 'Weight' is a number, not 'heavy'", "Crate\n  Weight = heavy\nEnd\n", Crate.class);
        assertError("crate.duke:3: 'Radius' is a number, not 'wide'",
                "Crate\n  Geometry = Cylinder\n    Radius = wide\n  End\nEnd\n", Crate.class);
        assertError("crate.duke:2: 'Geometry' is one of [Sphere, Cylinder, Box], not 'Cone'",
                "Crate\n  Geometry = Cone\nEnd\n", Crate.class);
        assertError("crate.duke:3: 'Parts' is one of [Wheel], not 'Tyre'",
                "Crate\n  Parts = [\n    Tyre\n    End\n  ]\nEnd\n", Crate.class);
        assertError("crate.duke:2: 'Parts' is a list of blocks: 'Parts = [', a block for each, then ']'",
                "Crate\n  Parts = Wheel\nEnd\n", Crate.class);
        assertError("crate.duke:2: 'Crate' holds no block 'Lid'", "Crate\n  Lid\n  End\nEnd\n", Crate.class);
        assertError("crate.duke:4: 'Volume' is written twice in 'Crate'",
                "Crate\n  Volume\n  End\n  Volume\n  End\nEnd\n", Crate.class);
    }

    /** The files were written the other way first, so a block in the old place says where it goes now. */
    @Test
    void aBlockWhereAFieldShouldBeSaysHowItIsWritten() {
        assertError("crate.duke:2: 'Cylinder' is the value of its field: Geometry = Cylinder",
                "Crate\n  Cylinder\n  End\nEnd\n", Crate.class);
        assertError("crate.duke:2: 'Wheel' goes in its list: 'Parts = [', then Wheel … End, then ']'",
                "Crate\n  Wheel\n  End\nEnd\n", Crate.class);
        assertError("crate.duke:2: 'Generation' is written 'Generation = Generation', its fields under it",
                "Crate\n  Generation\n  End\nEnd\n", Crate.class);
        assertError("crate.duke:2: 'Held' is a list of blocks: 'Held = [', a block for each, then ']'",
                "Crate\n  Held\n  End\nEnd\n", Crate.class);
    }

    private static void assertError(String message, String text, Class<?> type) {
        var error = assertThrows(DataException.class, () -> bind(text, type));
        assertEquals(message, error.getMessage());
    }
}
