package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

/** Trees swaying in the wind as the reference's do: within their range, toward their bearing, not all as one. */
class SwayTest {

    /** The reference's breeze: 0 to 0.11 radians, a five-second period, ten groups. */
    private static final Visuals.UnitVisual.Sway BREEZE = new Visuals.UnitVisual.Sway(0f, 0.11f, 0.5f, 5f, 10);

    @Test
    void twoTreesOfDifferentGroupsLeanDifferentlyAtOneMomentEachWithinTheRange() {
        float seconds = 7.5f;
        float first = BREEZE.angleAt(3, seconds);
        float second = BREEZE.angleAt(8, seconds);

        assertNotEquals(first, second, 1e-4f, "a forest does not move as one");
        for (float angle : new float[] {first, second}) {
            assertTrue(angle >= 0f && angle <= 0.11f, "within its range: " + angle);
        }
        assertEquals(first, BREEZE.angleAt(13, seconds), 1e-6f, "one group, one lean");
    }

    @Test
    void itLeansTowardItsBearing() {
        var top = BREEZE.tiltAt(0, 0f).mult(Vector3f.UNIT_Y);

        assertEquals(0.11f, (float) Math.asin(Math.hypot(top.x, top.z)), 1e-4f, "its most, at the start of a swing");
        assertEquals(0.5f, (float) Math.atan2(top.z, top.x), 1e-4f, "toward its bearing, the map's y the scene's z");
    }
}
