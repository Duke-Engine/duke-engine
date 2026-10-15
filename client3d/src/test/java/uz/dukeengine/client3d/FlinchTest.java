package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A swinging creature struck again and again keeps its swing; an idle one flinches once an interval (E71). */
class FlinchTest {

    @Test
    void midSwingNoBlowFlinchesAndIdleOnceAnInterval() {
        var look = new Flinch(1f, false);
        var clock = new Flinch.Clock();
        for (float now = 0f; now < 3f; now += 0.25f) {
            assertFalse(clock.flinches(look, 1, now, true, 10f, false, 0f), "swinging at " + now);
        }

        int flinched = 0;
        for (float now = 0f; now < 3f; now += 0.25f) {
            if (clock.flinches(look, 2, now, false, 10f, false, 0f)) {
                flinched++;
            }
        }
        assertEquals(3, flinched, "idle, struck twelve times over three seconds: once a second");
    }

    @Test
    void heavyOnlyFlinchesAtACriticalOrBigBlow() {
        var look = new Flinch(0f, true);
        var clock = new Flinch.Clock();
        assertFalse(clock.flinches(look, 1, 0f, false, 10f, false, 50f), "a light blow");
        assertTrue(clock.flinches(look, 1, 1f, false, 60f, false, 50f), "a big one");
        assertTrue(clock.flinches(look, 1, 2f, false, 10f, true, 50f), "a critical one");
    }
}
