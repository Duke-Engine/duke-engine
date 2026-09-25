package uz.dukeengine.client3d;

import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import java.util.function.IntSupplier;

/**
 * The game's first look at the raw input: each event turned into a {@link CanvasInput.Event} and offered to the game,
 * and marked consumed when the game takes it — which is what keeps it from every control of the client's own, since
 * the input manager hands a consumed event to nothing after the raw listeners.
 *
 * <p>The pointer is counted from the top of the screen here, as the canvas draws, where the input manager counts it
 * from the bottom. A typed character arrives from the window apart from its key, as a key with no code carrying the
 * character; it becomes a {@link CanvasInput.Typed}, and a character beyond the first 65536 that arrives in halves is
 * put back together first.
 */
final class CanvasInputs implements RawInputListener {

    /** How close in time two presses of one button are a double click: Windows' default, 500 ms. */
    static final long DOUBLE_CLICK_NANOS = 500_000_000L;
    /** And how close on the screen: Windows' default double-click rectangle, four pixels. */
    static final int DOUBLE_CLICK_PIXELS = 4;

    private final CanvasInput game;
    private final IntSupplier screenHeight;
    private CanvasInput.Mouse lastDown;
    private long lastDownAt;
    private int lastDownX;
    private int lastDownY;
    private char highHalf;
    private boolean pointerTaken;

    CanvasInputs(CanvasInput game, IntSupplier screenHeight) {
        this.game = game;
        this.screenHeight = screenHeight;
    }

    @Override
    public void beginInput() {
    }

    @Override
    public void endInput() {
    }

    @Override
    public void onMouseMotionEvent(MouseMotionEvent event) {
        int x = event.getX();
        int y = screenHeight.getAsInt() - event.getY();
        boolean taken = false;
        if (event.getDeltaWheel() != 0) {
            taken = game.take(new CanvasInput.Wheel(x, y, event.getDeltaWheel()));
        }
        if (event.getDX() != 0 || event.getDY() != 0) {
            pointerTaken = game.take(new CanvasInput.Pointer(x, y));
            taken |= pointerTaken;
        }
        if (taken) {
            event.setConsumed();
        }
    }

    /** Whether the game took the pointer where it last moved to: it is over a window of the game's own. */
    boolean pointerTaken() {
        return pointerTaken;
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent event) {
        var button = switch (event.getButtonIndex()) {
            case MouseInput.BUTTON_LEFT -> CanvasInput.Mouse.LEFT;
            case MouseInput.BUTTON_RIGHT -> CanvasInput.Mouse.RIGHT;
            case MouseInput.BUTTON_MIDDLE -> CanvasInput.Mouse.MIDDLE;
            default -> null;
        };
        if (button == null) {
            return; // a fourth button is nobody's yet
        }
        int x = event.getX();
        int y = screenHeight.getAsInt() - event.getY();
        boolean twice = false;
        if (event.isPressed()) {
            twice = button == lastDown && event.getTime() - lastDownAt <= DOUBLE_CLICK_NANOS
                    && Math.abs(x - lastDownX) <= DOUBLE_CLICK_PIXELS && Math.abs(y - lastDownY) <= DOUBLE_CLICK_PIXELS;
            // A third press close behind is a fresh first, not a second double click.
            lastDown = twice ? null : button;
            lastDownAt = event.getTime();
            lastDownX = x;
            lastDownY = y;
        }
        if (game.take(new CanvasInput.Button(x, y, button, event.isPressed(), twice))) {
            event.setConsumed();
        }
    }

    @Override
    public void onKeyEvent(KeyInputEvent event) {
        if (event.getKeyCode() != KeyInput.KEY_UNKNOWN) {
            if (game.take(new CanvasInput.Key(event.getKeyCode(), event.isPressed(), event.isRepeating()))) {
                event.setConsumed();
            }
            return;
        }
        char typed = event.getKeyChar();
        if (!event.isPressed() || typed == 0) {
            return; // the window sends each character down and up; it is typed once
        }
        if (Character.isHighSurrogate(typed)) {
            highHalf = typed;
            event.setConsumed();
            return;
        }
        int codePoint = Character.isLowSurrogate(typed) && highHalf != 0
                ? Character.toCodePoint(highHalf, typed) : typed;
        highHalf = 0;
        if (game.take(new CanvasInput.Typed(codePoint))) {
            event.setConsumed();
        }
    }

    @Override
    public void onJoyAxisEvent(JoyAxisEvent event) {
    }

    @Override
    public void onJoyButtonEvent(JoyButtonEvent event) {
    }

    @Override
    public void onTouchEvent(TouchEvent event) {
    }
}
