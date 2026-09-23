package uz.dukeengine.client3d;

/**
 * The mask behind each of {@link uz.dukeengine.core.data.Fade}'s sixteen shapes: how strong the overlay is
 * at a cell's four corners and at its middle.
 *
 * <p>Five numbers rather than a picture, because five are enough to be exact. A cell of overlay is drawn
 * as four triangles meeting at its middle, and every one of the shapes is a straight ramp on each of
 * them: a side ramps straight across, and a corner ramps from its point to the diagonal that does not
 * touch it — whose midpoint the middle vertex is. With the two triangles a cell is otherwise cut into, a
 * corner that lies across the cut would come out kinked. No texture is fetched, and a map with no overlay
 * builds none of this.
 */
final class FadeShape {

    /** Top-left, top-right, bottom-right, bottom-left, middle — for shapes 0 to 7. */
    private static final float[][] SHAPES = {
        {1f, 0f, 0f, 1f, 0.5f}, // 0 from the left
        {0f, 1f, 1f, 0f, 0.5f}, // 1 from the right
        {1f, 1f, 0f, 0f, 0.5f}, // 2 from the top
        {0f, 0f, 1f, 1f, 0.5f}, // 3 from the bottom
        {1f, 0f, 0f, 0f, 0f},   // 4 the top-left corner
        {0f, 1f, 0f, 0f, 0f},   // 5 the top-right corner
        {0f, 0f, 1f, 0f, 0f},   // 6 the bottom-right corner
        {0f, 0f, 0f, 1f, 0f},   // 7 the bottom-left corner
    };

    /** What a cell with no overlay says. */
    static final int NONE = -1;

    /** What a character that is neither says. */
    static final int UNREADABLE = -2;

    private FadeShape() {
    }

    /** The overlay's strength at the four corners, top-left clockwise, and the middle; 8 and up reversed. */
    static float[] strengths(int shape) {
        var base = SHAPES[shape & 7];
        if (shape < 8) {
            return base.clone();
        }
        var reversed = new float[base.length];
        for (int i = 0; i < base.length; i++) {
            reversed[i] = 1f - base[i];
        }
        return reversed;
    }

    /** The shape a character names: a hexadecimal digit, {@code .} for none, anything else unreadable. */
    static int of(char written) {
        if (written == '.') {
            return NONE;
        }
        int digit = Character.digit(written, 16);
        return digit < 0 ? UNREADABLE : digit;
    }
}
