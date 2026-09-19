package uz.duke.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.duke.core.data.DataException;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleFactory;

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
                Object
                  Name = Crusader
                  DisplayName = Crusader Tank
                  KindOf = [SELECTABLE, VEHICLE, CAN_ATTACK]
                  Modules = [
                    ActiveBody
                      MaxHealth = 480.0
                    End
                  ]
                End
                """, "units.duke");

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
                Object
                  Name = Alpha
                  KindOf = [STRUCTURE]
                  Modules = [
                    ActiveBody
                      MaxHealth = 100
                    End
                  ]
                End
                Object
                  Name = Beta
                  KindOf = [INFANTRY]
                  Modules = [
                    ActiveBody
                      MaxHealth = 50
                    End
                  ]
                End
                """, "units.duke");

        assertTrue(((ObjectTemplate) thingFactory.findTemplate("Alpha")).isKindOf(Kind.of("STRUCTURE")));
        assertTrue(((ObjectTemplate) thingFactory.findTemplate("Beta")).isKindOf(Kind.of("INFANTRY")));

        var beta = thingFactory.newObject(thingFactory.findTemplate("Beta"), new ObjectId(2));
        assertEquals(50f, beta.getBody().getMaxHealth(), 1e-6f);
    }

    /** A game's own template: solid, with no eyes, and a field of its own. */
    record Crate(String name, List<ModuleData> modules, Geometry geometry, int weight) implements Solid {
    }

    /** A block type a game adds is its record: the fields it takes are the record's, and no others. */
    @Test
    void aGameTypeIsItsRecord() {
        loader.type(Crate.class).load("""
                Crate
                  Name = Box
                  Weight = 7
                  Geometry = Cylinder
                    Radius = 3
                  End
                  Modules = [
                    ActiveBody
                      MaxHealth = 20
                    End
                  ]
                End
                """, "crates.duke");

        var crate = (Crate) thingFactory.findTemplate("Box");
        assertEquals(new Geometry.Cylinder(3f, 0f), crate.geometry());
        assertEquals(7, crate.weight());
        assertEquals(20f, thingFactory.newObject(crate, new ObjectId(3)).getBody().getMaxHealth(), 1e-6f);

        var sighted = assertThrows(DataException.class,
                () -> loader.load("Crate\n  Name = Blind\n  VisionRange = 5\nEnd\n", "crates.duke"));
        assertEquals("crates.duke:3: 'Crate' has no field 'VisionRange'", sighted.getMessage());
        var unknown = assertThrows(DataException.class, () -> loader.load("Barrel\nEnd\n", "crates.duke"));
        assertEquals("crates.duke:1: no template is called 'Barrel'; these are: [Object, Crate]", unknown.getMessage());
    }
}
