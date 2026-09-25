package uz.dukeengine.client3d;

/**
 * The player's mouse on the camera, as plain numbers: a middle-button drag turning the view, and whether letting go
 * of it was a click. Held apart from the application because none of it needs a window.
 */
final class Steering {

    /** A middle click moves no further than this either way, in pixels (the reference's {@code PIXEL_OFFSET}). */
    static final float MIDDLE_CLICK_PIXELS = 5f;
    /** And is let go within five of the reference's frames at thirty a second ({@code CLICK_DURATION}). */
    static final float MIDDLE_CLICK_SECONDS = 5f / 30f;

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
