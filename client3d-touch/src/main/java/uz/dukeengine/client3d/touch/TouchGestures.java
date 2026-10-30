package uz.dukeengine.client3d.touch;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fingers in, gestures out. Fed each finger's id, place (pixels, as jME gives them) and the time, it says what the
 * hand is doing: a tap, a one-finger pan, a box begun by a long press, or two fingers pinching and twisting. It knows
 * no window, so a test can feed it by hand.
 */
final class TouchGestures {

    /** What the hand does; every call is optional. */
    interface Listener {
        /** A finger touched and lifted in place. */
        default void tap(float x, float y) {
        }

        /** One finger moved past the slop: its first place. */
        default void panBegin(float x, float y) {
        }

        /** The panning finger is here now. */
        default void pan(float x, float y) {
        }

        default void panEnd() {
        }

        /** One finger held in place long enough: a box begins where it is. */
        default void boxBegin(float x, float y) {
        }

        /** The finger that held and dragged lifted here. */
        default void boxEnd(float x, float y) {
        }

        /** Two fingers' distance changed: {@code factor} over 1 for spreading. */
        default void pinch(float factor) {
        }

        /** Two fingers' line turned by {@code radians}, counterclockwise on the screen (y up) for positive. */
        default void twist(float radians) {
        }
    }

    /** Pixels a finger may stray and still be held or tapping; the scheme of a dp-aware screen scales it (3.5). */
    static final float SLOP = 16f;
    /** Seconds a finger holds before a drag becomes a box. */
    static final float LONG_PRESS = 0.4f;

    private enum Mode { IDLE, UNDECIDED, PAN, BOX, PINCH, SPENT }

    private final Listener out;
    /** Fingers down, in the order they came: id to {startX, startY, x, y}. Only the first two are read. */
    private final Map<Integer, float[]> fingers = new LinkedHashMap<>();
    private Mode mode = Mode.IDLE;
    private float since;
    private float lastDistance;
    private float lastAngle;

    TouchGestures(Listener out) {
        this.out = out;
    }

    void down(int id, float x, float y, float time) {
        fingers.put(id, new float[] {x, y, x, y});
        if (fingers.size() == 1) {
            mode = Mode.UNDECIDED;
            since = time;
        } else if (fingers.size() == 2 && mode != Mode.BOX) {
            if (mode == Mode.PAN) {
                out.panEnd();
            }
            mode = Mode.PINCH;
            lastDistance = distance();
            lastAngle = angle();
        } // a third finger, or a second during a box, is ignored
    }

    void move(int id, float x, float y) {
        var finger = fingers.get(id);
        if (finger == null) {
            return;
        }
        finger[2] = x;
        finger[3] = y;
        switch (mode) {
            case UNDECIDED -> {
                if (Math.hypot(x - finger[0], y - finger[1]) > SLOP) {
                    mode = Mode.PAN;
                    out.panBegin(finger[0], finger[1]);
                    out.pan(x, y);
                }
            }
            case PAN -> out.pan(x, y);
            case PINCH -> {
                if (fingers.size() >= 2) {
                    pinchAndTwist();
                }
            }
            default -> {
            }
        }
    }

    /** Each frame: a finger held still long enough begins a box. */
    void update(float time) {
        if (mode == Mode.UNDECIDED && time - since >= LONG_PRESS) {
            var finger = fingers.values().iterator().next();
            mode = Mode.BOX;
            out.boxBegin(finger[2], finger[3]);
        }
    }

    void up(int id, float x, float y, float time) {
        var finger = fingers.get(id);
        if (finger == null) {
            return;
        }
        update(time);
        finger[2] = x;
        finger[3] = y;
        boolean first = fingers.keySet().iterator().next() == id;
        switch (mode) {
            case UNDECIDED -> out.tap(x, y);
            case PAN -> out.panEnd();
            case BOX -> {
                if (first) {
                    out.boxEnd(x, y);
                }
            }
            default -> {
            }
        }
        fingers.remove(id);
        mode = fingers.isEmpty() ? Mode.IDLE : mode == Mode.BOX && !first ? Mode.BOX : Mode.SPENT;
    }

    /** Whether any finger is down. */
    boolean touching() {
        return !fingers.isEmpty();
    }

    private void pinchAndTwist() {
        float distance = distance();
        float angle = angle();
        if (lastDistance > 0f && distance > 0f) {
            out.pinch(distance / lastDistance);
        }
        float turned = angle - lastAngle;
        turned = (float) Math.atan2(Math.sin(turned), Math.cos(turned)); // the short way round
        out.twist(turned);
        lastDistance = distance;
        lastAngle = angle;
    }

    private float[][] two() {
        var it = fingers.values().iterator();
        return new float[][] {it.next(), it.next()};
    }

    private float distance() {
        var two = two();
        return (float) Math.hypot(two[1][2] - two[0][2], two[1][3] - two[0][3]);
    }

    private float angle() {
        var two = two();
        return (float) Math.atan2(two[1][3] - two[0][3], two[1][2] - two[0][2]);
    }
}
