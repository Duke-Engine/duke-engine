package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Matrix4f;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.math.Vector4f;
import org.junit.jupiter.api.Test;

/** That the shifts are the Halton sequence, stay in a pixel, and that a shift put on and taken off leaves no motion behind. */
class JitterTest {

    private static final float EPSILON = 1e-5f;

    private static Matrix4f viewProjection() {
        var projection = new Matrix4f();
        projection.fromFrustum(0.1f, 100f, -0.06f, 0.06f, 0.0375f, -0.0375f, false);
        var view = new Matrix4f();
        view.setTranslation(0.5f, -0.3f, -4f);
        return projection.mult(view);
    }

    private static Vector2f ndc(Matrix4f m, Vector3f point) {
        var clip = m.mult(new Vector4f(point.x, point.y, point.z, 1f));
        return new Vector2f(clip.x / clip.w, clip.y / clip.w);
    }

    @Test
    void haltonIsTheRadicalInverse() {
        assertEquals(0.5f, Jitter.halton(1, 2), EPSILON);
        assertEquals(0.25f, Jitter.halton(2, 2), EPSILON);
        assertEquals(0.75f, Jitter.halton(3, 2), EPSILON);
        assertEquals(1f / 3f, Jitter.halton(1, 3), EPSILON);
        assertEquals(2f / 3f, Jitter.halton(2, 3), EPSILON);
        assertEquals(1f / 9f, Jitter.halton(3, 3), EPSILON);
    }

    @Test
    void aCycleStaysInAPixelRepeatsAndNeverStandsStill() {
        var seen = new java.util.HashSet<Vector2f>();
        var shift = new Vector2f();
        for (int frame = 0; frame < 8; frame++) {
            Jitter.pixels(frame, 8, shift);
            assertTrue(Math.abs(shift.x) <= 0.5f && Math.abs(shift.y) <= 0.5f);
            seen.add(shift.clone());
        }
        assertEquals(8, seen.size());
        assertEquals(Jitter.pixels(3, 8, new Vector2f()), Jitter.pixels(11, 8, new Vector2f()));
    }

    @Test
    void aShiftMovesEveryDepthByTheSameAndTheNegativeTakesItOff() {
        var base = viewProjection();
        var shifted = Jitter.shift(base.clone(), 0.002f, -0.003f);
        for (var point : new Vector3f[] {new Vector3f(1f, 2f, -10f), new Vector3f(-3f, 0.5f, -80f)}) {
            var at = ndc(base, point);
            var moved = ndc(shifted, point);
            assertEquals(0.002f, moved.x - at.x, EPSILON);
            assertEquals(-0.003f, moved.y - at.y, EPSILON);
        }
        var back = Jitter.shift(shifted, -0.002f, 0.003f);
        for (int i = 0; i < 16; i++) {
            assertEquals(base.get(i / 4, i % 4), back.get(i / 4, i % 4), 1e-6f);
        }
    }

    @Test
    void aCameraStandingStillTellsNoMotionHoweverTheShiftsChange() {
        // As SceneBuffers works it out: this frame's shifted position, less last frame's matrix (unshifted) shifted by this frame's.
        var still = viewProjection();
        var point = new Vector3f(1f, 2f, -10f);
        float lastX = 0.0013f;
        float lastY = -0.0007f;
        float nowX = -0.0009f;
        float nowY = 0.0011f;
        var last = Jitter.shift(still.clone(), lastX, lastY);
        var now = Jitter.shift(still.clone(), nowX, nowY);
        var lastUnshifted = Jitter.shift(last.clone(), -lastX, -lastY);
        var previous = Jitter.shift(lastUnshifted, nowX, nowY);
        var motion = ndc(now, point).subtract(ndc(previous, point));
        assertEquals(0f, motion.x, EPSILON);
        assertEquals(0f, motion.y, EPSILON);
        // And a naive difference of the shifted matrices is not zero: it is the bug this avoids.
        assertTrue(ndc(now, point).subtract(ndc(last, point)).length() > 1e-3f);
    }
}
