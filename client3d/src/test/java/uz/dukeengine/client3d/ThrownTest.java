package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

/** A piece thrown off: up, down under gravity, a lower bounce, lying still, and gone after its life. */
class ThrownTest {

    private static final WorldMoments.Floor FLAT = (x, z) -> 0f;

    @Test
    void aPieceThrownUpComesDownBouncesLowerAndIsGoneAfterItsLife() {
        var thrown = new Thrown(new Vector3f(), new Vector3f(0f, 10f, 0f), Vector3f.UNIT_Y, 0.1f, 1f, 0.5f, 60, 10);
        float firstPeak = 0f;
        float secondPeak = 0f;
        boolean landed = false;
        int frames = 0;
        while (thrown.frame(FLAT)) {
            frames++;
            float y = thrown.at().y;
            assertTrue(y >= 0f, "never under the ground: " + y);
            if (!landed) {
                firstPeak = Math.max(firstPeak, y);
                landed = frames > 1 && y <= 1e-4f;
            } else {
                secondPeak = Math.max(secondPeak, y);
            }
        }

        assertEquals(45f, firstPeak, 1e-4f, "up at 10 a frame, gravity taking 1 a frame: 9 + 8 + ... + 1");
        assertTrue(secondPeak > 0f && secondPeak < firstPeak, "it bounced, lower: " + secondPeak);
        assertTrue(thrown.resting(), "and came to lie on the ground");
        assertEquals(59, frames, "gone once its 60 frames are up");
    }

    @Test
    void itFadesOverTheLastFramesOfItsLife() {
        var thrown = new Thrown(new Vector3f(), new Vector3f(), Vector3f.UNIT_Y, 0f, 1f, 0f, 20, 10);
        for (int frame = 0; frame < 10; frame++) {
            thrown.frame(FLAT);
            assertEquals(1f, thrown.opacity(), "whole until its last ten frames");
        }
        for (int frame = 0; frame < 5; frame++) {
            thrown.frame(FLAT);
        }
        assertEquals(0.5f, thrown.opacity(), 1e-6f, "half gone five frames from the end");
    }

    @Test
    void itLandsOnTheGroundUnderIt() {
        var thrown = new Thrown(new Vector3f(0f, 30f, 0f), new Vector3f(1f, 0f, 0f), Vector3f.UNIT_Y, 0f, 1f, 0f,
                100, 0);
        for (int frame = 0; frame < 30; frame++) {
            thrown.frame((x, z) -> 12f);
        }
        assertEquals(12f, thrown.at().y, 1e-4f, "on the hill it came down on, lying there with no bounce");
        assertTrue(thrown.resting());
    }
}
