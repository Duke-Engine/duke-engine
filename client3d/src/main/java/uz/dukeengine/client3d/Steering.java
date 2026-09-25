package uz.dukeengine.client3d;

import com.jme3.math.Vector2f;

/**
 * The player's hands on the camera, as plain numbers: how far the pan keys, the pointer at the window's edges and the
 * right button held scroll the view this frame, a middle-button drag turning it, and whether letting go of a button
 * was a click. Held apart from the application because none of it needs a window.
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
     * game's own — at {@code edges}' share of them, the two added so both hands work at once, as the reference scrolls
     * its keys and its display's edges alike ({@code LookAtTranslator}); and, while the right button is held, by how
     * far the pointer is from its anchor, the anchor dragged after it first.
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
        var pan = new Vector2f(across * speedAcross * tpf, down * speedAlong * tpf);
        if (drag != null) {
            if (drag.reach() > 0f) {
                float mostAcross = drag.reach() * hands.width();
                float mostDown = drag.reach() * hands.height();
                anchorX = Math.clamp(anchorX, hands.x() - mostAcross, hands.x() + mostAcross);
                anchorY = Math.clamp(anchorY, hands.y() - mostDown, hands.y() + mostDown);
            }
            pan.x += drag.across() * (hands.x() - anchorX) * tpf;
            pan.y += drag.along() * (hands.y() - anchorY) * tpf;
        }
        return pan;
    }

    /** The right button's drag while it is held, or null. */
    private RightDrag drag;
    private float anchorX;
    private float anchorY;
    private float rightDownX;
    private float rightDownY;
    private float rightDownAt;
    private float rightDownViewX;
    private float rightDownViewZ;

    /**
     * The right button down at {@code (x, y)}, {@code seconds} into the client's time, the view looking at {@code
     * (viewX, viewZ)}: where it went down is the anchor the view scrolls from while it is held, as {@code drag} says.
     */
    void rightDown(float x, float y, float seconds, float viewX, float viewZ, RightDrag drag) {
        this.drag = drag;
        anchorX = x;
        anchorY = y;
        rightDownX = x;
        rightDownY = y;
        rightDownAt = seconds;
        rightDownViewX = viewX;
        rightDownViewZ = viewZ;
    }

    /**
     * Let go at {@code (x, y)}, the view looking at {@code (viewX, viewZ)}: whether it was a click — near where it went
     * down, soon after, and the view hardly moved meanwhile, as the reference's {@code SelectionTranslator} asks — and
     * not a drag.
     */
    boolean rightUp(float x, float y, float seconds, float viewX, float viewZ) {
        if (drag == null) {
            return false;
        }
        var was = drag;
        drag = null;
        return Math.abs(x - rightDownX) <= was.clickPixels() && Math.abs(y - rightDownY) <= was.clickPixels()
                && (seconds - rightDownAt) * 1000f <= was.clickMillis()
                && Math.hypot(viewX - rightDownViewX, viewZ - rightDownViewZ) <= was.clickMoved();
    }

    /**
     * Which buttons the window says are still held: a drag whose button was let go where the client never heard it —
     * the game's canvas took the release — goes on no further.
     */
    void stillHeld(boolean right, boolean middle) {
        if (!right) {
            drag = null;
        }
        if (!middle) {
            turning = false;
        }
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
