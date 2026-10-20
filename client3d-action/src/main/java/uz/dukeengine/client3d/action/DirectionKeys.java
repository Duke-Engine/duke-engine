package uz.dukeengine.client3d.action;

/**
 * The four keys held to walk the hero — up, left, down and right on the screen — read by the client each frame and
 * turned into a direction on the ground: see {@link ActionControls#walk}.
 */
public record DirectionKeys(char up, char left, char down, char right) {

    /** W A S D. */
    public static final DirectionKeys WASD = new DirectionKeys('W', 'A', 'S', 'D');

    /** No direction: none held, or they cancel — the {@code ActionOrders.NONE} it is sent as. */
    public static final int NONE = -1;

    /**
     * The direction those held make on the ground, the camera turned {@code yaw}: in 256ths of a turn, 0 along +x and
     * counter-clockwise seen from above, as {@code ActionOrders.along} takes it; {@link #NONE} for none.
     */
    static int angleOf(boolean up, boolean left, boolean down, boolean right, float yaw) {
        float across = (right ? 1f : 0f) - (left ? 1f : 0f);
        float downScreen = (down ? 1f : 0f) - (up ? 1f : 0f);
        if (across == 0f && downScreen == 0f) {
            return NONE;
        }
        // Across and down the screen, which is the ground turned the way the camera is — as the pan keys go.
        float cos = (float) Math.cos(yaw);
        float sin = (float) Math.sin(yaw);
        float x = across * cos + downScreen * sin;
        float y = downScreen * cos - across * sin;
        double turns = Math.atan2(y, x) / (2.0 * Math.PI);
        return (int) Math.round(turns * 256.0) & 0xFF;
    }

    /** The direction last told, so it is told only when it changes. */
    static final class Held {
        private int last = NONE;

        /**
         * The direction now, or null where it is the one last told — {@link #NONE} while the keys are someone else's
         * ({@code taken}: a menu, a screen of the game's, the window not focused).
         */
        Integer next(boolean up, boolean left, boolean down, boolean right, float yaw, boolean taken) {
            int now = taken ? NONE : angleOf(up, left, down, right, yaw);
            if (now == last) {
                return null;
            }
            last = now;
            return now;
        }
    }
}
