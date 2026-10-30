package uz.dukeengine.client3d.touch;

import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.font.BitmapText;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
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
 *
 * <p>While a building is armed and its place pressed ({@link Context#placing}), a finger drag carries the ghost (the
 * aim) and the camera stays; lifting leaves the ghost and puts up Confirm ({@link Context#putDownArmed}) and Cancel
 * ({@link Context#cancelArmed}) under it, and a tap elsewhere moves the ghost there.
 */
public final class TouchRts implements ControlScheme {

    /** The confirm and cancel buttons, pixels at the design's density: a finger's size and their gap from the ghost. */
    private static final float BUTTON_W = 160f;
    private static final float BUTTON_H = 64f;
    private static final float GAP = 24f;

    private Context context;
    private boolean onWorld;
    private float[] heldGround;
    /** Where a finger put the armed building, the world's x and y; null before it has. */
    private float[] aimAt;
    /** Whether the confirm and cancel buttons are up: the finger has lifted off a placement. */
    private boolean confirming;
    /** Where the ghost is on the screen: the buttons sit under it. */
    private Vector3f anchor = new Vector3f();
    private BitmapText confirmLabel;
    private BitmapText cancelLabel;
    private final TouchGestures gestures = new TouchGestures(new TouchGestures.Listener() {
        @Override
        public void tap(float x, float y) {
            if (context.placing()) {
                if (confirming && hits(x, y, 1)) {
                    context.putDownArmed();
                } else if (confirming && hits(x, y, -1)) {
                    context.cancelArmed();
                } else {
                    aim(x, y); // the ghost goes where the finger lifted
                    confirming = true;
                }
                return;
            }
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
            heldGround = onWorld && !context.placing() ? context.groundAt(x, y) : null;
        }

        @Override
        public void pan(float x, float y) {
            if (context.placing()) {
                confirming = false;
                aim(x, y); // the finger carries the ghost, the camera stays
                return;
            }
            var now = heldGround == null ? null : context.groundAt(x, y);
            if (now != null) {
                var look = context.lookingAt();
                context.lookAt(look[0] + heldGround[0] - now[0], look[1] + heldGround[1] - now[1]);
            }
        }

        @Override
        public void panEnd() {
            confirming |= context.placing() && aimAt != null; // lifted: the buttons come up
        }

        @Override
        public void boxBegin(float x, float y) {
            if (onWorld && !context.placing()) {
                context.beginBox();
            }
        }

        @Override
        public void boxEnd(float x, float y) {
            if (context.placing()) {
                aim(x, y);
                confirming = true;
            } else if (onWorld) {
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
        gestures.scale(context.uiScale());
        var input = context.input();
        if (input != null) {
            input.addRawInputListener(new Fingers());
        }
        var gui = context.gui();
        if (gui != null && context.font() != null) {
            confirmLabel = label("Confirm", ColorRGBA.Green);
            cancelLabel = label("Cancel", ColorRGBA.Red);
        }
    }

    @Override
    public void frame(float tpf) {
        gestures.update(context.time());
        if (!gestures.touching()) {
            onWorld = false;
        }
        if (!context.placing()) {
            aimAt = null;
            confirming = false;
        } else if (aimAt != null) {
            var at = context.screenOf(aimAt[0], context.floorHeight(aimAt[0], aimAt[1]), aimAt[1]);
            anchor = at == null ? anchor : at;
        }
        if (confirmLabel != null) {
            lay(confirmLabel, 1);
            lay(cancelLabel, -1);
        }
    }

    /** The build is held until a confirm: what is put down is the ghost, where the finger left it. */
    @Override
    public boolean holdsPlacement() {
        return context.placing();
    }

    @Override
    public boolean aiming() {
        return aimAt != null && context.placing();
    }

    @Override
    public float[] aimPoint() {
        return aimAt;
    }

    private void aim(float x, float y) {
        var ground = context.groundAt(x, y);
        if (ground != null) {
            aimAt = new float[] {ground[0], ground[1]};
        }
    }

    /** Whether a point is on the confirm ({@code side} 1, right of the ghost) or cancel ({@code side} -1, left) button. */
    private boolean hits(float x, float y, int side) {
        float left = side > 0 ? anchor.x + gap() : anchor.x - gap() - buttonW();
        float top = anchor.y - gap();
        return x >= left && x <= left + buttonW() && y <= top && y >= top - buttonH();
    }

    /** The buttons' size and gap on this screen, pixels. */
    private float buttonW() {
        return BUTTON_W * context.uiScale();
    }

    private float buttonH() {
        return BUTTON_H * context.uiScale();
    }

    private float gap() {
        return GAP * context.uiScale();
    }

    private BitmapText label(String text, ColorRGBA colour) {
        var label = new BitmapText(context.font());
        label.setText(text);
        label.setSize(buttonH() / 2f);
        label.setColor(colour);
        label.setCullHint(Spatial.CullHint.Always);
        context.gui().attachChild(label);
        return label;
    }

    private void lay(BitmapText label, int side) {
        label.setLocalTranslation(side > 0 ? anchor.x + gap() : anchor.x - gap() - buttonW(), anchor.y - gap(), 0f);
        label.setCullHint(confirming ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
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
