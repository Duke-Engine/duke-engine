package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Each step costs no less than the one under it, and what saves time is on at all of them. */
class QualityTest {

    private static int effects(PictureSettings s) {
        int n = 0;
        for (boolean on : new boolean[] {s.ambientOcclusion(), s.bloom(), s.softShadows(), s.contactShadows(), s.shafts(),
                s.reflections(), s.screenSpaceGi(), s.antiAliasing() == AntiAliasing.TAA}) {
            n += on ? 1 : 0;
        }
        return n * 100 + s.localShadows();
    }

    @Test
    void eachStepAddsToTheOneUnderIt() {
        var steps = Quality.values();
        for (int i = 1; i < steps.length; i++) {
            assertTrue(effects(steps[i].settings()) > effects(steps[i - 1].settings()), steps[i] + " over " + steps[i - 1]);
        }
        assertFalse(Quality.LOW.settings().ambientOcclusion());
        assertEquals(8, Quality.EPIC.settings().localShadows());
    }

    @Test
    void whatSavesTimeIsOnAtEveryStep() {
        for (var step : Quality.values()) {
            var s = step.settings();
            assertTrue(s.autoInstancing() && s.autoLod() && s.occlusionCulling() && s.clusteredLights(), step.name());
        }
    }
}
