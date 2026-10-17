package uz.dukeengine.client3d;

/**
 * The mouse held by the window — the action scheme's: the pointer hidden and its motion read as how far it moved, not
 * where it is — and let go while anything wants the pointer (a screen, a menu, a cutscene, Alt held, another window).
 * See {@link ControlScheme.Context#holdMouse}.
 */
final class MouseCapture {

    private static final float[] STILL = {0f, 0f};

    private boolean wanted;
    private boolean captured;
    private boolean fresh;

    /** Whether the game wants the mouse held while nothing frees it. */
    void want(boolean held) {
        this.wanted = held;
    }

    boolean wanted() {
        return wanted;
    }

    /** This frame: held, or let go because {@code freed}; whether it is held now. */
    boolean update(boolean freed) {
        boolean now = wanted && !freed;
        if (now && !captured) {
            fresh = true; // held again: the first motion is none, not the jump from where the pointer was left
        }
        captured = now;
        return captured;
    }

    boolean captured() {
        return captured;
    }

    /** How far the mouse moved this frame, while held: {@code dx}, {@code dy} — none on the frame it is first held. */
    float[] motion(float dx, float dy) {
        if (!captured) {
            return STILL;
        }
        if (fresh) {
            fresh = false;
            return STILL;
        }
        return new float[] {dx, dy};
    }
}
