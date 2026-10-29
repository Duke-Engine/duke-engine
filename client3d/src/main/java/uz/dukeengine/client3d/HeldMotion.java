package uz.dukeengine.client3d;

import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;

/**
 * How far the mouse moved, summed from jME's events until someone takes the sum — the held mouse's motion, with no
 * window to ask where the pointer stands. jME counts {@code dy} up the screen; the window's own coordinates, which
 * the turning was written in, count it down, so {@link #take} gives {@code dy} down.
 */
final class HeldMotion implements RawInputListener {

    private float dx;
    private float dy;

    /** {@code dx} across and {@code dy} down since the last call, and the sum starts again. */
    float[] take() {
        var moved = new float[] {dx, 0f - dy};
        dx = 0f;
        dy = 0f;
        return moved;
    }

    @Override
    public void onMouseMotionEvent(MouseMotionEvent event) {
        dx += event.getDX();
        dy += event.getDY();
    }

    @Override
    public void beginInput() {
    }

    @Override
    public void endInput() {
    }

    @Override
    public void onJoyAxisEvent(JoyAxisEvent event) {
    }

    @Override
    public void onJoyButtonEvent(JoyButtonEvent event) {
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {
    }

    @Override
    public void onKeyEvent(KeyInputEvent event) {
    }

    @Override
    public void onTouchEvent(TouchEvent event) {
    }
}
