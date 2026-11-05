package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What the water between takes of a colour (the shader's Beer-Lambert, in Java), and the clear water's order of colours. */
class WaterTest {

    @Test
    void noWaterTakesNothingAndAMetreTakesItsExtinctionsShare() {
        assertEquals(1f, Water.transmittance(0.4f, 0f), 1e-6f);
        assertEquals((float) Math.exp(-1.0), Water.transmittance(0.5f, 2f), 1e-6f);
    }

    @Test
    void deepClearWaterLosesRedFirstAndKeepsMostBlue() {
        float red = Water.transmittance(Water.CLEAR.x, 5f);
        float green = Water.transmittance(Water.CLEAR.y, 5f);
        float blue = Water.transmittance(Water.CLEAR.z, 5f);
        assertTrue(red < green && green < blue, "red " + red + " green " + green + " blue " + blue);
        assertTrue(red < 0.2f && blue > 0.7f);
    }
}
