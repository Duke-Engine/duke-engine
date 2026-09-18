package uz.duke.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.ini.IniException;
import uz.duke.core.module.ModuleFactory;
import uz.duke.core.thing.ThingTemplate.ModuleEntry;

class ThingTemplateLoaderTest {

    private ThingFactory thingFactory;
    private ThingTemplateLoader loader;

    @BeforeEach
    void setUp() {
        thingFactory = new ThingFactory(ModuleFactory.withDefaults());
        loader = new ThingTemplateLoader(thingFactory);
    }

    @Test
    void loadsObjectWithKindOfAndBodyModule() {
        loader.load("""
                Object Crusader
                  DisplayName = Crusader Tank
                  KindOf = SELECTABLE VEHICLE CAN_ATTACK
                  Body = ActiveBody ModuleTag_01
                    MaxHealth = 480.0
                  End
                End
                """);

        var template = (ObjectTemplate) thingFactory.findTemplate("Crusader");
        assertNotNull(template);
        assertEquals("Crusader Tank", template.displayName());
        assertTrue(template.isKindOf(Kind.of("VEHICLE")));
        assertTrue(template.isKindOf(Kind.of("CAN_ATTACK")));
        assertFalse(template.isKindOf(Kind.of("STRUCTURE")));

        // The body module data round-trips into a built object.
        var object = thingFactory.newObject(template, new ObjectId(1));
        assertEquals(480.0f, object.getBody().getMaxHealth(), 1e-6f);
        assertEquals(480.0f, object.getBody().getHealth(), 1e-6f);
    }

    @Test
    void loadsMultipleObjects() {
        loader.load("""
                Object Alpha
                  KindOf = STRUCTURE
                  Body = ActiveBody Tag
                    MaxHealth = 100
                  End
                End
                Object Beta
                  KindOf = INFANTRY
                  Body = ActiveBody Tag
                    MaxHealth = 50
                  End
                End
                """);

        assertNotNull(thingFactory.findTemplate("Alpha"));
        assertNotNull(thingFactory.findTemplate("Beta"));
        assertTrue(((ObjectTemplate) thingFactory.findTemplate("Alpha")).isKindOf(Kind.of("STRUCTURE")));
        assertTrue(((ObjectTemplate) thingFactory.findTemplate("Beta")).isKindOf(Kind.of("INFANTRY")));

        var beta = thingFactory.newObject(thingFactory.findTemplate("Beta"), new ObjectId(2));
        assertEquals(50f, beta.getBody().getMaxHealth(), 1e-6f);
    }

    /** A game's own template: solid, with no eyes, and a field of its own. */
    record Crate(String name, List<ModuleEntry> modules, Geometry geometry, int weight) implements Solid {
    }

    private static final class Weight {
        private int weight;
    }

    private static final FieldParseTable<Weight> WEIGHT = new FieldParseTable<Weight>()
            .add("Weight", Ini.integer((w, v) -> w.weight = v));

    /**
     * A block type a game adds takes the engine fields of its record's capabilities, and
     * no others: a crate that is only solid has a shape, and no range to see.
     */
    @Test
    void aGameTypeTakesTheFieldsOfWhatItsRecordCanDo() {
        loader.type("Crate", Crate.class, WEIGHT, Weight::new,
                (parts, part) -> new Crate(parts.name(), parts.modules(), parts.geometry(), part.weight));
        loader.load("""
                Crate Box
                  Geometry = CYLINDER
                  GeometryMajorRadius = 3
                  Weight = 7
                  Body = ActiveBody Tag
                    MaxHealth = 20
                  End
                End
                """);

        var crate = (Crate) thingFactory.findTemplate("Box");
        assertEquals(new Geometry.Cylinder(3f, 0f), crate.geometry());
        assertEquals(7, crate.weight());
        assertEquals(20f, thingFactory.newObject(crate, new ObjectId(3)).getBody().getMaxHealth(), 1e-6f);

        var sighted = assertThrows(IniException.class, () -> loader.load("Crate Blind\n  VisionRange = 5\nEnd\n"));
        assertTrue(sighted.getMessage().contains("VisionRange"), sighted.getMessage());
    }

    /** A game reading its own fields from the same block passes the engine's over, and still refuses a misspelt one. */
    @Test
    void aGameReadsItsFieldsAndPassesOverTheEngines() {
        var weight = new Weight();
        var table = ThingTemplateLoader.passingOverEngineFields(WEIGHT, Crate.class);
        Ini.of("""
                Crate Box
                  Geometry = BOX
                  Weight = 4
                  Update = MoveUpdate Tag
                    Speed = 3
                  End
                End
                """, Map.of("Crate", ini -> {
                    ini.getNextToken();
                    ini.initFromIni(weight, table);
                })).load();
        assertEquals(4, weight.weight);

        assertThrows(IniException.class, () -> Ini.of("Crate Box\n  Wieght = 4\nEnd\n", Map.of("Crate", ini -> {
            ini.getNextToken();
            ini.initFromIni(new Weight(), table);
        })).load());
    }
}
