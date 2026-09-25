package uz.dukeengine.client3d;

import com.jme3.math.Vector2f;

/**
 * The player's hands on the camera, as plain numbers: how far the pan keys and the pointer at the window's edges
 * scroll the view this frame, a middle-button drag turning it, and whether letting go of a button was a click. Held
 * apart from the application because none of it needs a window.
 */
final class Steering {

    /**
     * What the hands are doing this frame.
     *
     * @param x        where the pointer is, in pixels from the window's left
     * @param y        and down from its top
     * @param inWindow whether the pointer is in the window at all: outside it, it is at no edge
     */
    record Hands(boolean left, boolean right, boolean up, boolean down, float x, float y, boolean inWindow,
            int width, int height) {
    }

    /** A middle click moves no further than this either way, in pixels (the reference's {@code PIXEL_OFFSET}). */
    static final float MIDDLE_CLICK_PIXELS = 5f;
    /** And is let go within five of the reference's frames at thirty a second ({@code CLICK_DURATION}). */
    static final float MIDDLE_CLICK_SECONDS = 5f / 30f;

    /**
     * How far the view scrolls this frame, across the screen and down it along the ground, in world units: the pan keys
     * at the speeds given, and the pointer resting at an edge of the window — the bottom one too, under a bar of the
     * game's own — at {@code edges}' share of them, the two added so both hands work at once. The reference scrolls
     * its keys and its display's edges alike ({@code LookAtTranslator}).
     */
    Vector2f scroll(Hands hands, float tpf, float speedAcross, float speedAlong, EdgeScroll edges) {
        float across = (hands.right() ? 1f : 0f) - (hands.left() ? 1f : 0f);
        float down = (hands.down() ? 1f : 0f) - (hands.up() ? 1f : 0f);
        if (edges.wanted() && hands.inWindow()) {
            float margin = edges.marginPixels();
            float share = edges.speedPercent() / 100f;
            across += ((hands.x() >= hands.width() - margin ? 1f : 0f) - (hands.x() < margin ? 1f : 0f)) * share;
            down += ((hands.y() >= hands.height() - margin ? 1f : 0f) - (hands.y() < margin ? 1f : 0f)) * share;
        }
        return new Vector2f(across * speedAcross * tpf, down * speedAlong * tpf);
    }

    private boolean turning;
    private float turnedFrom;
    private float middleDownX;
    private float middleDownY;
    private float middleDownAt;

    /** The middle button down at {@code (x, y)}, {@code seconds} into the client's time: a drag to turn by. */
    void middleDown(float x, float y, float seconds) {
        turning = true;
        turnedFrom = x;
        middleDownX = x;
        middleDownY = y;
        middleDownAt = seconds;
    }

    /** How far the drag turns the view since it was last asked, with the pointer at {@code x}, in radians. */
    float turn(float x, float perPixel) {
        if (!turning) {
            return 0f;
        }
        float by = perPixel * (x - turnedFrom);
        turnedFrom = x;
        return by;
    }

    /** Let go at {@code (x, y)}: whether it was a click — hardly moved, soon let go — and not a drag. */
    boolean middleUp(float x, float y, float seconds) {
        if (!turning) {
            return false;
        }
        turning = false;
        return Math.abs(x - middleDownX) <= MIDDLE_CLICK_PIXELS && Math.abs(y - middleDownY) <= MIDDLE_CLICK_PIXELS
                && seconds - middleDownAt < MIDDLE_CLICK_SECONDS;
    }
}
