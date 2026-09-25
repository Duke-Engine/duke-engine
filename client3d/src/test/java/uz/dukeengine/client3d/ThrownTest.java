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

    /** A life counted from rest: gone the same 60 frames after it rests, however long it flew. */
    @Test
    void aLifeFromRestIsCountedFromWhenItRests() {
        assertEquals(60, framesLyingAfterFlying(5f), "ten frames in the air");
        assertEquals(60, framesLyingAfterFlying(50f), "a hundred");
    }

    private static int framesLyingAfterFlying(float up) {
        var thrown = new Thrown(new Vector3f(), new Vector3f(0f, up, 0f), Vector3f.UNIT_Y, 0f, 1f, 0f, 0f, 60, true,
                0);
        int flew = 0;
        while (!thrown.resting()) {
            assertTrue(thrown.frame(FLAT), "alive while it flies");
            flew++;
        }
        assertTrue(Math.abs(flew - up * 2f) <= 2f, "it flew " + flew + " frames");
        int lying = 0;
        while (thrown.frame(FLAT)) {
            lying++;
        }
        return lying + 1; // the frame it went on
    }

    /**
     * Thrown along the ground at 2 a frame with 0.15 of friction: 0.85 of its speed kept a frame, it is creeping by
     * the thirtieth and lies still at the third after — the reference's {@code isVerySmall3D}, under 0.01 a frame.
     */
    @Test
    void aPieceSlidesToAStopByItsFriction() {
        var thrown = new Thrown(new Vector3f(), new Vector3f(2f, 0f, 0f), Vector3f.UNIT_Y, 0f, 1f, 0.5f, 0.15f, 1000,
                false, 0);
        float was = 0f;
        for (int frame = 1; frame <= 30; frame++) {
            thrown.frame(FLAT);
            assertEquals(0f, thrown.at().y, "along the ground, never struck off it");
            assertTrue(!thrown.struck());
            if (frame == 30) {
                assertTrue(thrown.at().x - was < 0.02f, "creeping by the thirtieth: " + (thrown.at().x - was));
            }
            was = thrown.at().x;
        }
        for (int frame = 31; frame <= 33; frame++) {
            thrown.frame(FLAT);
        }
        assertTrue(thrown.resting(), "still by the thirty-third");
        assertEquals(2f * 0.85f / 0.15f, thrown.at().x, 0.1f, "having slid what its speed sums to");
    }
}
