package uz.dukeengine.client3d.touch;

import com.jme3.font.BitmapText;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Spatial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import uz.dukeengine.client3d.ControlScheme;
import uz.dukeengine.client3d.SkillRange;
import uz.dukeengine.game.DukeGame;

/**
 * A game played from one hero's shoulder, by thumbs — the touch twin of {@code ActionControls}, which it does not
 * depend on. A finger down on the left half of the screen is a floating stick: where it lands is its centre, dragging
 * walks the hero that way (the camera's turn counted) and lifting stops him. A finger dragging on the right half turns
 * the camera. Buttons stand at its bottom right: <b>Hit</b> strikes along the camera's forward while held, and a button
 * per skill key presses that skill ({@link Context#pressSkill}); a skill that is aimed is aimed from the hero along the
 * camera's forward, and cast when the finger lifts — the other thumb can turn the camera meanwhile. Every finger is
 * followed by its id, so the stick, a button and the camera work at once.
 *
 * <p>What the game does with a walk or a strike is the game's: it comes through {@link #walk} and {@link #strike} as
 * callbacks (the game sends {@code ActionOrders.along} and {@code toward}), as {@code ActionControls.walk} has it.
 * Installed like any scheme: {@code Duke3D.controls(new TouchHero().walk(...).strike(...).skills('Q', 'W'))}.
 */
public final class TouchHero implements ControlScheme {

    /** What a strike is told: the hero's camera forward, a world angle in radians, and whether the button is held. */
    @FunctionalInterface
    public interface Strike {
        void strike(DukeGame game, float forward, boolean held);
    }

    /** A button's size, pixels: a thumb's. */
    static final float BUTTON = 96f;
    /** The gap between buttons and from the screen's edge. */
    static final float GAP = 24f;
    /** Radians of camera turn per pixel of a finger dragged across the right half. */
    static final float TURN_PER_PIXEL = 0.006f;
    /** The button of {@link #strike}; the skill buttons follow it. */
    private static final int STRIKE = 0;

    private Context context;
    private BiConsumer<DukeGame, Integer> walked;
    private Strike struck;
    private final List<Character> skills = new ArrayList<>();

    private final VirtualStick stick = new VirtualStick();
    private int stickFinger = -1;
    private int lookFinger = -1;
    private float lookX;
    /** The finger on each button held now, by button: 0 is the strike, 1.. the skills. */
    private final Map<Integer, Integer> buttonOfFinger = new HashMap<>();
    /** The direction last told, so it is told only when it changes. */
    private int walking = VirtualStick.NONE;
    /** The strike's forward last told, in 256ths of a turn; -1 while it is not held. */
    private int struckAt = -1;

    private boolean aimArmed;
    private float aimReach;
    private boolean aimDownALane;
    /** Whether the skill button that armed it is held: lifting that finger casts. */
    private int aimingButton = -1;

    private BitmapText ring;
    private BitmapText knob;
    private final List<BitmapText> labels = new ArrayList<>();

    /**
     * Walk by the stick: told the direction on the ground as the camera is turned, in 256ths of a turn (the same as
     * {@code ActionControls.walk}), only when it changes, and {@code -1} once when the stick lets go or the keys are
     * someone else's. The game sends it on as {@code ActionOrders.along}.
     */
    public TouchHero walk(BiConsumer<DukeGame, Integer> changed) {
        this.walked = changed;
        return this;
    }

    /**
     * The Hit button: told the camera's forward when pressed, when it turns while held and with {@code held} false
     * when let go. The game sends it on as the strike {@code ActionOrders.toward} gives.
     */
    public TouchHero strike(Strike strike) {
        this.struck = strike;
        return this;
    }

    /** A button for each skill key, in a row from the right; the game's own keys for the skills. */
    public TouchHero skills(char... keys) {
        for (char key : keys) {
            skills.add(Character.toUpperCase(key));
        }
        return this;
    }

    @Override
    public void attach(Context context) {
        this.context = context;
        var input = context.input();
        if (input != null) {
            input.addRawInputListener(new Fingers());
        }
        if (context.gui() != null && context.font() != null) {
            ring = text("O", VirtualStick.RADIUS);
            knob = text("o", BUTTON / 2f);
            labels.add(text("Hit", BUTTON / 2f));
            for (char key : skills) {
                labels.add(text(String.valueOf(key), BUTTON / 2f));
            }
        }
    }

    // ---- each frame ----

    @Override
    public void frame(float tpf) {
        tellTheDirection();
        followTheStrike();
        draw();
    }

    private void tellTheDirection() {
        if (walked == null) {
            return;
        }
        int now = context.playing() ? stick.angle(context.yaw()) : VirtualStick.NONE;
        if (now != walking) {
            walking = now;
            walked.accept(context.game(), now);
        }
    }

    /** A held strike told again as the camera turns its forward, and let go where the keys are someone else's. */
    private void followTheStrike() {
        if (struckAt < 0) {
            return;
        }
        if (!context.playing()) {
            releaseStrike();
            return;
        }
        int now = quantized(forward());
        if (now != struckAt) {
            struckAt = now;
            struck.strike(context.game(), forward(), true);
        }
    }

    private void releaseStrike() {
        if (struckAt >= 0) {
            struckAt = -1;
            struck.strike(context.game(), forward(), false);
        }
    }

    /** The world's angle of the camera's forward — up the screen, as the stick's up walks. */
    private float forward() {
        float yaw = context.yaw();
        return (float) Math.atan2(-Math.cos(yaw), -Math.sin(yaw));
    }

    private static int quantized(float radians) {
        return (int) Math.round(radians / (2.0 * Math.PI) * 256.0) & 0xFF;
    }

    // ---- fingers ----

    private void down(int id, float x, float y) {
        int button = buttonAt(x, y);
        if (button >= 0) {
            buttonOfFinger.put(id, button);
            press(button);
            return;
        }
        float[] screen = context.screenSize();
        boolean leftHalf = screen == null || x < screen[0] / 2f;
        if (leftHalf && stickFinger < 0) {
            stickFinger = id;
            stick.down(x, y);
        } else if (!leftHalf && lookFinger < 0) {
            lookFinger = id;
            lookX = x;
        }
    }

    private void move(int id, float x, float y) {
        if (id == stickFinger) {
            stick.move(x, y);
        } else if (id == lookFinger) {
            context.turnBy(-(x - lookX) * TURN_PER_PIXEL); // a finger dragged right turns the view as the mouse does
            lookX = x;
        }
    }

    private void up(int id) {
        if (id == stickFinger) {
            stickFinger = -1;
            stick.up();
        } else if (id == lookFinger) {
            lookFinger = -1;
        } else {
            var button = buttonOfFinger.remove(id);
            if (button != null) {
                lift(button);
            }
        }
    }

    private void press(int button) {
        if (button == STRIKE) {
            if (struck != null && context.playing()) {
                struckAt = quantized(forward());
                struck.strike(context.game(), forward(), true);
            }
            return;
        }
        context.pressSkill(skills.get(button - 1));
        if (aimArmed) {
            aimingButton = button; // armed by this press: lifting the finger casts
        }
    }

    private void lift(int button) {
        if (button == STRIKE) {
            releaseStrike();
        } else if (button == aimingButton) {
            aimingButton = -1;
            if (aimArmed) {
                context.castArmed();
            }
        }
    }

    // ---- the buttons ----

    /** The button under a point, or -1; the buttons are in a row along the bottom right, the strike nearest the edge. */
    private int buttonAt(float x, float y) {
        float[] screen = context.screenSize();
        if (screen == null) {
            return -1;
        }
        for (int button = 0; button <= skills.size(); button++) {
            float half = BUTTON / 2f;
            if (Math.abs(x - centreX(screen, button)) <= half && Math.abs(y - centreY()) <= half) {
                return button;
            }
        }
        return -1;
    }

    private static float centreX(float[] screen, int button) {
        return screen[0] - GAP - BUTTON / 2f - button * (BUTTON + GAP);
    }

    private static float centreY() {
        return GAP + BUTTON / 2f;
    }

    // ---- aiming a skill ----

    /** Every skill is aimed from the hero along the camera's forward — the pointer is under a thumb, not a mouse. */
    @Override
    public boolean armed(char key, boolean byMouse, SkillRange range) {
        aimArmed = true;
        aimReach = range == null ? 0f : Math.max(0f, range.reach());
        aimDownALane = range != null && range.shape() == SkillRange.Shape.DOWN_A_LANE;
        return true;
    }

    @Override
    public float[] aimPoint() {
        var hero = context.selected();
        var at = !aimArmed || hero.isEmpty() ? null : context.drawnAt(hero.getFirst().value());
        if (at == null) {
            return null;
        }
        float yaw = context.yaw();
        float distance = aimDownALane ? aimReach : aimReach * 2f / 3f;
        return new float[] {at[0] - (float) Math.sin(yaw) * distance, at[1] - (float) Math.cos(yaw) * distance};
    }

    @Override
    public boolean aiming() {
        return aimArmed;
    }

    @Override
    public void disarmed() {
        aimArmed = false;
        aimingButton = -1;
    }

    /** The press that reached the world is the scheme's; a drag by itself starts no box. */
    @Override
    public boolean left(boolean pressed) {
        return context.input() != null;
    }

    // ---- drawing ----

    private BitmapText text(String text, float size) {
        var label = new BitmapText(context.font());
        label.setText(text);
        label.setSize(size);
        label.setColor(ColorRGBA.White);
        label.setCullHint(Spatial.CullHint.Always);
        context.gui().attachChild(label);
        return label;
    }

    /** The stick under the thumb that holds it, and the buttons lit while a finger is on them. */
    private void draw() {
        if (ring == null) {
            return;
        }
        float[] screen = context.screenSize();
        place(ring, stick.centreX(), stick.centreY(), stick.active());
        place(knob, stick.knobX(), stick.knobY(), stick.active());
        for (int button = 0; button < labels.size() && screen != null; button++) {
            var label = labels.get(button);
            place(label, centreX(screen, button), centreY(), true);
            label.setColor(buttonOfFinger.containsValue(button) ? ColorRGBA.Yellow : ColorRGBA.White);
        }
    }

    private static void place(BitmapText label, float x, float y, boolean shown) {
        label.setLocalTranslation(x - label.getLineWidth() / 2f, y + label.getLineHeight() / 2f, 0f);
        label.setCullHint(shown ? Spatial.CullHint.Inherit : Spatial.CullHint.Always);
    }

    /** For a test: the fingers as the input would give them. */
    void touch(TouchEvent.Type type, int id, float x, float y) {
        switch (type) {
            case DOWN -> down(id, x, y);
            case MOVE -> move(id, x, y);
            case UP -> up(id);
            default -> {
            }
        }
    }

    private final class Fingers implements RawInputListener {
        @Override
        public void onTouchEvent(TouchEvent e) {
            touch(e.getType(), e.getPointerId(), e.getX(), e.getY());
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
