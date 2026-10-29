package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.util.TempVars;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A light a thing carries: lit by its words, shining only near it, flickering, and put out with them. */
class UnitLightsTest {

    private static final Visuals.ThingLight FLASHER = new Visuals.ThingLight(0x3050FF, 12f, 2f, 0.6f, 4f);
    private static final Visuals VISUALS = Visuals.create()
            .unit("Police", look -> look.model("models/police.glb").light(Set.of("SIREN"), FLASHER))
            .unit("Lamp", look -> look.model("models/lamp.glb").light(new Visuals.ThingLight(0xFFFFFF, 5f)))
            .unit("Tank", look -> look.model("models/tank.glb"));

    private static boolean reaches(com.jme3.light.PointLight lamp, float x, float z) {
        var box = new BoundingBox(new Vector3f(x, 0f, z), 1f, 1f, 1f);
        var vars = TempVars.get();
        try {
            return lamp.intersectsBox(box, vars);
        } finally {
            vars.release();
        }
    }

    @Test
    void aPoliceLightLightsOnlyTheGroundNearItAndNotThingsFarOff() {
        var lights = new UnitLights(new LightPool(new Node("root"), 4));
        var siren = VISUALS.of("Police").lightFor(Set.of("SIREN"));

        lights.update(List.of(new UnitLights.Line(1, siren, new Vector3f(100f, 0f, 100f))), 0f);

        var lamp = lights.of(1);
        assertNotNull(lamp);
        assertEquals(12f, lamp.getRadius());
        assertEquals(2f, lamp.getPosition().y, "from its height over its feet");
        assertTrue(reaches(lamp, 105f, 100f), "a thing beside the car is lit");
        assertFalse(reaches(lamp, 140f, 100f), "a thing farther than its radius is not");
        assertFalse(reaches(lamp, 0f, 0f), "nor one across the map, which only a sun lights");
    }

    @Test
    void itBurnsWhileItsWordsAreHeldAndIsGivenBackWhenTheyGo() {
        var pool = new LightPool(new Node("root"), 4);
        var lights = new UnitLights(pool);
        var police = VISUALS.of("Police");

        assertNull(police.lightFor(Set.of()), "no siren, no light");
        assertNull(VISUALS.of("Tank").lightFor(Set.of("SIREN")), "a look with none has none");
        assertEquals(5f, VISUALS.of("Lamp").lightFor(Set.of()).radius(), "no words: it burns always");

        lights.update(List.of(new UnitLights.Line(1, police.lightFor(Set.of("SIREN")), new Vector3f())), 0f);
        assertEquals(1, pool.lit());

        lights.update(List.of(new UnitLights.Line(1, police.lightFor(Set.of("SIREN")), new Vector3f(7f, 0f, 0f))), 1f);
        assertEquals(1, pool.lit(), "the same light follows it");
        assertEquals(7f, lights.of(1).getPosition().x);

        lights.update(List.of(), 2f);
        assertEquals(0, pool.lit(), "siren off: out and back in the pool");
        assertNull(lights.of(1));
    }

    @Test
    void aRadiusOfNothingIsNoLightRatherThanLightForTheWholeWorld() {
        var pool = new LightPool(new Node("root"), 2);
        var lights = new UnitLights(pool);

        lights.update(List.of(new UnitLights.Line(1, new Visuals.ThingLight(0xFFFFFF, 0f), new Vector3f())), 0f);

        assertEquals(0, pool.lit());
    }

    @Test
    void aThingFindingEveryLightBurningGoesWithoutOne() {
        var pool = new LightPool(new Node("root"), 1);
        var lights = new UnitLights(pool);
        var siren = FLASHER;

        lights.update(List.of(new UnitLights.Line(1, siren, new Vector3f()),
                new UnitLights.Line(2, siren, new Vector3f())), 0f);

        assertNotNull(lights.of(1));
        assertNull(lights.of(2));
    }

    @Test
    void flickerDimsWithinItsDepthAtItsRateAndFromAPhaseOfEachThingsOwn() {
        float least = 1f;
        float most = 0f;
        for (int step = 0; step < 100; step++) {
            float now = UnitLights.brightness(FLASHER, 1, step * 0.01f);
            least = Math.min(least, now);
            most = Math.max(most, now);
        }
        assertEquals(1f - 0.6f, least, 0.02f, "down by its flicker at the deepest");
        assertEquals(1f, most, 0.02f, "and whole at the brightest");
        assertEquals(UnitLights.brightness(FLASHER, 1, 0f), UnitLights.brightness(FLASHER, 1, 0.25f), 1e-4f,
                "four times a second");
        assertNotEquals(UnitLights.brightness(FLASHER, 1, 0f), UnitLights.brightness(FLASHER, 2, 0f),
                "two flashers in a street do not blink as one");
        assertEquals(1f, UnitLights.brightness(new Visuals.ThingLight(0xFFFFFF, 5f), 9, 0.3f), "steady means steady");
    }

    @Test
    void aLightOfSeveralColoursTakesTheNextEachTimeItFlickersRound() {
        var police = new Visuals.ThingLight(List.of(0xFF0000, 0x0000FF, 0xFFFFFF), 12f, 2f, 0.6f, 4f);
        assertEquals(0xFF0000, police.colour(), "its colour is the first of them");

        var seen = new java.util.ArrayList<Integer>();
        for (int round = 0; round < 3; round++) {
            seen.add(UnitLights.colourAt(police, 0, round * 0.25f + 0.01f));
        }
        assertEquals(List.of(0xFF0000, 0x0000FF, 0xFFFFFF), seen, "red, blue, white, a quarter second each");
        assertEquals(0xFF0000, UnitLights.colourAt(police, 0, 0.76f), "and round again");
        assertEquals(0xFF0000, UnitLights.colourAt(
                new Visuals.ThingLight(List.of(0xFF0000, 0x0000FF), 12f, 2f, 0f, 0f), 0, 5f), "a steady one burns the first");
        assertEquals(List.of(0x3050FF), FLASHER.colours(), "one colour is a list of one");
        assertEquals(0x3050FF, UnitLights.colourAt(FLASHER, 4, 1.3f));
    }
}
