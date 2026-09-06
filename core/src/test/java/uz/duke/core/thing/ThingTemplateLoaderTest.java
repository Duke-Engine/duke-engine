package uz.duke.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
                Object Crusader
                  DisplayName = Crusader Tank
                  KindOf = SELECTABLE VEHICLE CAN_ATTACK
                  Body = ActiveBody ModuleTag_01
                    MaxHealth = 480.0
                  End
                End
                """);

        var template = thingFactory.findTemplate("Crusader");
        assertNotNull(template);
        assertEquals("Crusader Tank", template.getDisplayName());
        assertTrue(template.isKindOf(KindOf.VEHICLE));
        assertTrue(template.isKindOf(KindOf.CAN_ATTACK));
        assertFalse(template.isKindOf(KindOf.STRUCTURE));

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
        assertTrue(thingFactory.findTemplate("Alpha").isKindOf(KindOf.STRUCTURE));
        assertTrue(thingFactory.findTemplate("Beta").isKindOf(KindOf.INFANTRY));

        var beta = thingFactory.newObject(thingFactory.findTemplate("Beta"), new ObjectId(2));
        assertEquals(50f, beta.getBody().getMaxHealth(), 1e-6f);
    }
}
