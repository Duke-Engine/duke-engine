package uz.dukeengine.render;

import com.jme3.math.Matrix4f;
import com.jme3.math.Vector2f;

/** The sub-pixel shifts of the camera that {@link TaaFilter} sums frames over, and the matrix work of putting one on and taking it off. */
final class Jitter {

    private Jitter() {
    }

    /** The {@code index}th (from 1) of the low-discrepancy Halton sequence in {@code base}, in [0, 1). */
    static float halton(int index, int base) {
        float result = 0f;
        float fraction = 1f;
        for (int i = index; i > 0; i /= base) {
            fraction /= base;
            result += fraction * (i % base);
        }
        return result;
    }

    /** Where the {@code frame}th of {@code samples} puts the picture, in pixels from -0.5 to 0.5 (Halton 2, 3). */
    static Vector2f pixels(int frame, int samples, Vector2f store) {
        int index = Math.floorMod(frame, samples) + 1;
        return store.set(halton(index, 2) - 0.5f, halton(index, 3) - 0.5f);
    }

    /**
     * Moves what {@code viewProjection} draws by {@code x, y} in normalised device coordinates, at any depth: the clip
     * position takes {@code x} times its w, which the perspective divide turns into plain {@code x}. Shifting by the
     * negative undoes it.
     */
    static Matrix4f shift(Matrix4f viewProjection, float x, float y) {
        viewProjection.m00 += x * viewProjection.m30;
        viewProjection.m01 += x * viewProjection.m31;
        viewProjection.m02 += x * viewProjection.m32;
        viewProjection.m03 += x * viewProjection.m33;
        viewProjection.m10 += y * viewProjection.m30;
        viewProjection.m11 += y * viewProjection.m31;
        viewProjection.m12 += y * viewProjection.m32;
        viewProjection.m13 += y * viewProjection.m33;
        return viewProjection;
    }
}
