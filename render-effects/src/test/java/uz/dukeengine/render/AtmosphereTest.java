package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

class AtmosphereTest {

    private static final ColorRGBA SUN = new ColorRGBA(1f, 1f, 1f, 1f).mult(2.4f);

    private static Vector3f elevated(float degrees, float azimuthDegrees) {
        float e = degrees * FastMath.DEG_TO_RAD;
        float a = azimuthDegrees * FastMath.DEG_TO_RAD;
        return new Vector3f(FastMath.cos(e) * FastMath.cos(a), FastMath.sin(e), FastMath.cos(e) * FastMath.sin(a));
    }

    private static float blueOverRed(ColorRGBA c) {
        return c.b / c.r;
    }

    @Test
    void atNoonTheZenithIsBluerThanTheHorizon() {
        var sky = new Atmosphere(elevated(70f, 0f), SUN);
        var zenith = sky.at(new Vector3f(0f, 1f, 0f));
        var horizon = sky.at(elevated(1f, 180f));
        assertTrue(blueOverRed(zenith) > 2f * blueOverRed(horizon), "zenith " + zenith + " horizon " + horizon);
        assertTrue(zenith.b > zenith.g && zenith.g > zenith.r, "blue over green over red: " + zenith);
    }

    @Test
    void atSunsetTheHorizonNearTheSunIsRedderThanAwayFromItAndThanAtNoon() {
        var sunset = new Atmosphere(elevated(2f, 0f), SUN);
        var noon = new Atmosphere(elevated(70f, 0f), SUN);
        var nearSun = sunset.at(elevated(4f, 0f));
        var awayFromSun = sunset.at(elevated(4f, 180f));
        assertTrue(nearSun.r > nearSun.b, "orange near the sun: " + nearSun);
        assertTrue(blueOverRed(nearSun) < blueOverRed(awayFromSun), "redder near the sun than away from it");
        assertTrue(blueOverRed(nearSun) < blueOverRed(noon.at(elevated(4f, 0f))), "redder than noon's horizon");
    }

    @Test
    void theSunItselfIsRedderTheLowerItStandsAndGoneUnderTheGround() {
        var noon = Atmosphere.transmittance(elevated(70f, 0f));
        var low = Atmosphere.transmittance(elevated(2f, 0f));
        assertTrue(low.b / low.r < 0.5f * noon.b / noon.r, "noon " + noon + " low " + low);
        assertTrue(low.r < noon.r, "dimmer too");
        assertEquals(0f, Atmosphere.transmittance(elevated(-10f, 0f)).r, "the planet is in the way");
    }

    @Test
    void underTheHorizonIsNotBrighterThanAboveIt() {
        for (float sun : new float[] {70f, 20f, 2f, -5f}) {
            var sky = new Atmosphere(elevated(sun, 30f), SUN);
            for (float azimuth : new float[] {0f, 90f, 200f}) {
                var above = sky.at(elevated(10f, azimuth));
                var below = sky.at(elevated(-10f, azimuth));
                assertTrue(below.g <= above.g + 1e-6f, "sun " + sun + " azimuth " + azimuth + " above " + above + " below " + below);
            }
        }
    }

    @Test
    void everyDirectionIsFiniteAndNotNegativeEvenWithTheSunUnderTheGround() {
        for (float sun : new float[] {80f, 0f, -3f, -30f}) {
            var sky = new Atmosphere(elevated(sun, 0f), SUN);
            for (float e = -90f; e <= 90f; e += 15f) {
                for (float a = 0f; a < 360f; a += 45f) {
                    var c = sky.at(elevated(e, a));
                    assertTrue(c.r >= 0f && c.g >= 0f && c.b >= 0f && Float.isFinite(c.r + c.g + c.b), "sun " + sun + " " + c);
                }
            }
        }
        var sunAtZenith = new Atmosphere(new Vector3f(0f, 1f, 0f), SUN);
        assertTrue(Float.isFinite(sunAtZenith.at(new Vector3f(0f, 1f, 0f)).g));
        assertTrue(Float.isFinite(sunAtZenith.at(new Vector3f(0f, -1f, 0f)).g));
    }

    @Test
    void theBakedSkyHasNoSunDiscInItNorAnyFirefly() {
        // The disc is drawn apart (AtmosphereSky); the sky around the sun stays a few times the sun's own colour at most.
        for (float sun : new float[] {70f, 8f, 0.5f}) {
            var sky = new Atmosphere(elevated(sun, 0f), SUN);
            float brightest = 0f;
            for (float e = 0f; e <= 90f; e += 0.5f) {
                for (float a = -30f; a <= 30f; a += 0.5f) {
                    brightest = Math.max(brightest, sky.at(elevated(e, a)).g);
                }
            }
            assertTrue(brightest < SUN.g, "sun " + sun + " sky peaks at " + brightest);
        }
    }
}
