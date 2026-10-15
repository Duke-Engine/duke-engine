package uz.dukeengine.client3d;

import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import java.util.function.BooleanSupplier;

/**
 * Every key and button the player presses going to nothing while a title card that takes the input shows, or the
 * credits roll — Esc or a click told as a skip.
 */
final class CardInput implements RawInputListener {

    private final BooleanSupplier taking;
    private final Runnable skipped;

    CardInput(BooleanSupplier taking) {
        this(taking, () -> { });
    }

    CardInput(BooleanSupplier taking, Runnable skipped) {
        this.taking = taking;
        this.skipped = skipped;
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
    public void onMouseMotionEvent(MouseMotionEvent event) {
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {
        if (taking.getAsBoolean()) {
            event.setConsumed();
            if (event.isPressed()) {
                skipped.run();
            }
        }
    }

    @Override
    public void onKeyEvent(KeyInputEvent event) {
        if (taking.getAsBoolean()) {
            event.setConsumed();
            if (event.isPressed() && event.getKeyCode() == com.jme3.input.KeyInput.KEY_ESCAPE) {
                skipped.run();
            }
        }
    }

    @Override
    public void onTouchEvent(TouchEvent event) {
    }
}
