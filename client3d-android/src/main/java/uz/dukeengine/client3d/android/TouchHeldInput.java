package uz.dukeengine.client3d.android;

import android.app.Activity;
import android.view.MotionEvent;
import com.jme3.input.MouseInput;
import uz.dukeengine.client3d.platform.HeldInput;

/**
 * What a touch screen holds: the left button while a finger is down — jME makes the first finger the mouse — and no
 * other button and no key. The pointer is never "inside", so a finger left near an edge does not scroll the view.
 */
final class TouchHeldInput implements HeldInput {

    /** Told by {@link DukeActivity} as each touch passes. */
    private static volatile boolean fingerDown;

    private final Activity activity;

    TouchHeldInput(Activity activity) {
        this.activity = activity;
    }

    static void touched(int actionMasked) {
        fingerDown = actionMasked != MotionEvent.ACTION_UP && actionMasked != MotionEvent.ACTION_CANCEL;
    }

    @Override
    public boolean pointerInside() {
        return false;
    }

    @Override
    public boolean focused() {
        return activity.hasWindowFocus();
    }

    @Override
    public boolean buttonHeld(int button) {
        return button == MouseInput.BUTTON_LEFT && fingerDown;
    }

    @Override
    public boolean keyHeld(char key) {
        return false;
    }
}
