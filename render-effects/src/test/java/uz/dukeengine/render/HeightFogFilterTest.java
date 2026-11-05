package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** That the closed form of the fog along a ray is what summing the density along it comes to. */
class HeightFogFilterTest {

    /** The density summed by midpoints: what the closed form must equal. */
    private static double summed(double density, double falloff, double eyeY, double dirY, double distance) {
        int steps = 200_000;
        double step = distance / steps;
        double total = 0;
        for (int i = 0; i < steps; i++) {
            total += density * Math.exp(-falloff * (eyeY + dirY * (i + 0.5) * step)) * step;
        }
        return total;
    }

    @Test
    void closedFormIsTheSumOfTheDensityAlongTheRay() {
        double[][] rays = {{0.02, 0.06, 2.5, 0.0, 120}, {0.02, 0.06, 2.5, 0.2, 120}, {0.02, 0.06, 40, -0.3, 90},
            {0.005, 0.2, 0, 0.5, 300}, {0.05, 0.01, 10, 0.1, 50}};
        for (var r : rays) {
            assertEquals(summed(r[0], r[1], r[2], r[3], r[4]), HeightFogFilter.opticalDepth(r[0], r[1], r[2], r[3], r[4]),
                    1e-6 * Math.max(1, summed(r[0], r[1], r[2], r[3], r[4])));
        }
    }

    @Test
    void withoutFalloffOrAlongTheLevelItIsDensityTimesDistance() {
        assertEquals(0.02 * 100, HeightFogFilter.opticalDepth(0.02, 0, 7, 0.8, 100), 1e-9);
        assertEquals(0.02 * Math.exp(-0.06 * 3) * 100, HeightFogFilter.opticalDepth(0.02, 0.06, 3, 0, 100), 1e-9);
        // x a hair off 0, on both sides of the series' edge: the quotient would not hold its digits there
        for (double x : new double[] {1e-12, -1e-12, 0.0099, -0.0099, 0.0101, -0.0101}) {
            assertEquals(Math.expm1(-x) / -x, HeightFogFilter.stretch(x), 1e-9);
        }
    }

    @Test
    void aRayToTheSkyIsFoggedThroughOnlyNearTheHorizon() {
        double level = HeightFogFilter.opticalDepth(0.02, 0.06, 2.5, 0.0, 20_000);
        double high = HeightFogFilter.opticalDepth(0.02, 0.06, 2.5, 0.5, 20_000);
        assertTrue(level > 100 && high < 1, level + " " + high);
        // straight down at the sky's distance is finite, not infinity: no NaN into the picture
        assertTrue(Double.isFinite(HeightFogFilter.opticalDepth(0.02, 0.06, 2.5, -1.0, 20_000)));
    }
}
