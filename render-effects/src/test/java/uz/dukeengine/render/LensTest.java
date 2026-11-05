package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** That the disc a point spreads to is the thin lens's: none in focus, the distance's ratio off it, bounded far away. */
class LensTest {

    // A 50 mm lens at f/1.4 is 35.7 mm across; focused on 2 m, with a 90 degree field of view (projection00 = 1) on 1000 pixels.
    private final Lens lens = new Lens(2f, 1.4f, 50f, 0.5f);

    @Test
    void aPointInTheFocusPlaneIsASharpOne() {
        assertEquals(0f, lens.confusionPixels(2f, 1f, 1000), 1e-6f);
    }

    @Test
    void theDiscIsTheApertureOverTheWidthOfTheFocusPlaneTimesHowFarOffInDistanceRatio() {
        float aperture = 0.05f / 1.4f;
        float reach = aperture / 4f * 1000f; // a plane 2 m away is 4 m across at this field of view
        assertEquals(reach, lens.reachPixels(1f, 1000), 1e-3f);
        assertEquals(reach * 0.5f, lens.confusionPixels(4f, 1f, 1000), 1e-3f);
        assertEquals(-reach, lens.confusionPixels(1f, 1f, 1000), 1e-3f);
        assertEquals(reach, lens.confusionPixels(1e9f, 1f, 1000), 1e-2f);
    }

    @Test
    void aWiderApertureAndANarrowerFieldOfViewBlurMore() {
        assertTrue(new Lens(2f, 1.4f, 50f, 0f).reachPixels(1f, 1000) > new Lens(2f, 2.8f, 50f, 0f).reachPixels(1f, 1000));
        assertTrue(lens.reachPixels(2f, 1000) > lens.reachPixels(1f, 1000));
    }

    @Test
    void aLensNeedsARealApertureAndAFocusItCanReach() {
        assertThrows(IllegalArgumentException.class, () -> new Lens(2f, 0f, 50f, 0f));
        assertThrows(IllegalArgumentException.class, () -> new Lens(0.04f, 1.4f, 50f, 0f));
        assertThrows(IllegalArgumentException.class, () -> new Lens(2f, 1.4f, 50f, 1.5f));
        assertFalse(Lens.OFF.depthOfField() || Lens.OFF.motionBlur());
    }
}
