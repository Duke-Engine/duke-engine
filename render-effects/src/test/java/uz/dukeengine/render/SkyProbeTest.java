package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

class SkyProbeTest {

    private static final Vector3f TO_SUN = new Vector3f(0f, 1f, 0f);
    private static final ColorRGBA AMBIENT = new ColorRGBA(0.5f, 0.5f, 0.5f, 1f);

    @Test
    void theSkyIsBrightestAtTheSunAndDimmestUnderTheGround() {
        var atTheSun = SkyProbe.skyAt(TO_SUN, TO_SUN, ColorRGBA.White, AMBIENT);
        var horizon = SkyProbe.skyAt(new Vector3f(1f, 0f, 0f), TO_SUN, ColorRGBA.White, AMBIENT);
        var below = SkyProbe.skyAt(new Vector3f(0f, -1f, 0f), TO_SUN, ColorRGBA.White, AMBIENT);
        assertTrue(atTheSun.g > horizon.g, "the sun over the horizon");
        assertTrue(horizon.g > below.g, "the horizon over the ground");
        assertEquals(0.25f, below.g, 1e-5f, "under the ground: half the ambient, no sun");
    }
}
