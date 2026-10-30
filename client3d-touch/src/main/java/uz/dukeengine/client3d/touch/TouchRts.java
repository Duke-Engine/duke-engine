package uz.dukeengine.client3d.touch;

import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import uz.dukeengine.client3d.ControlScheme;

/**
 * An RTS played by finger. A tap on a unit selects it; a tap on the ground orders what is selected there (and with
 * nothing selected, chooses nothing). One finger dragging pans the camera, the ground staying under it; a finger held
 * still ({@link TouchGestures#LONG_PRESS}) and then dragged marks a box, chosen when it lifts. Two fingers pinching
 * zoom and, twisting, turn the camera. The client's own taps on its HUD never reach it: {@link #left} runs only on the
 * world.
 *
 * <p>Select, box and order act at the pointer, so where the platform simulates the mouse from touch — jME's Android
 * does — the pointer is under the finger. The recognizer itself reads {@link TouchEvent}s and needs no mouse.
 */
public final class TouchRts implements ControlScheme {

    private Context context;
    private boolean onWorld;
    private float[] heldGround;
    private final TouchGestures gestures = new TouchGestures(new TouchGestures.Listener() {
        @Override
        public void tap(float x, float y) {
            if (!onWorld) {
                return;
            }
            if (context.pointedUnit() != null || context.selected().isEmpty()) {
                context.selectPointed(false);
            } else {
                context.orderPointed();
            }
        }

        @Override
        public void panBegin(float x, float y) {
            heldGround = onWorld ? context.groundAt(x, y) : null;
        }

        @Override
        public void pan(float x, float y) {
            var now = heldGround == null ? null : context.groundAt(x, y);
            if (now != null) {
                var look = context.lookingAt();
                context.lookAt(look[0] + heldGround[0] - now[0], look[1] + heldGround[1] - now[1]);
            }
        }

        @Override
        public void boxBegin(float x, float y) {
            if (onWorld) {
                context.beginBox();
            }
        }

        @Override
        public void boxEnd(float x, float y) {
            if (onWorld) {
                context.endBox(false);
            }
        }

        @Override
        public void pinch(float factor) {
            context.zoomBy(1f / factor); // fingers apart come nearer
        }

        @Override
        public void twist(float radians) {
            context.turnBy(-radians); // the ground turns with the fingers, so the eye turns the other way
        }
    });

    @Override
    public void attach(Context context) {
        this.context = context;
        var input = context.input();
        if (input != null) {
            input.addRawInputListener(new Fingers());
        }
    }

    @Override
    public void frame(float tpf) {
        gestures.update(context.time());
        if (!gestures.touching()) {
            onWorld = false;
        }
    }

    /** The press that reached the world is the scheme's; a drag by itself starts no box. */
    @Override
    public boolean left(boolean pressed) {
        if (context.input() == null) {
            return false;
        }
        onWorld |= pressed;
        return true;
    }

    /** For a test: the recognizer, fed as the fingers would. */
    TouchGestures gestures() {
        return gestures;
    }

    /** For a test: as if the press had reached the world. */
    void pressedWorld() {
        onWorld = true;
    }

    private final class Fingers implements RawInputListener {
        @Override
        public void onTouchEvent(TouchEvent e) {
            float now = context.time();
            switch (e.getType()) {
                case DOWN -> gestures.down(e.getPointerId(), e.getX(), e.getY(), now);
                case MOVE -> gestures.move(e.getPointerId(), e.getX(), e.getY());
                case UP -> gestures.up(e.getPointerId(), e.getX(), e.getY(), now);
                default -> {
                }
            }
        }

        @Override
        public void beginInput() {
        }

        @Override
        public void endInput() {
        }

        @Override
        public void onJoyAxisEvent(JoyAxisEvent e) {
        }

        @Override
        public void onJoyButtonEvent(JoyButtonEvent e) {
        }

        @Override
        public void onMouseMotionEvent(MouseMotionEvent e) {
        }

        @Override
        public void onMouseButtonEvent(MouseButtonEvent e) {
        }

        @Override
        public void onKeyEvent(KeyInputEvent e) {
        }
    }
}
