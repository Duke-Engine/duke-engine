package uz.dukeengine.client3d;

import com.jme3.font.BitmapFont;
import com.jme3.input.InputManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.List;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.core.view.WorldSnapshot;
import uz.dukeengine.game.DukeGame;

/**
 * Controls of a kind the client does not have itself — walking by held keys, a camera kept on the hero and turned by a
 * held mouse, the left button striking, skills aimed with no pointer — plugged in by a game ({@code Duke3D.controls}).
 * The client asks it at the few places its own controls decide, and it works through a {@link Context}. Left alone, the
 * client's own: every answer below is what the client does without one.
 */
public interface ControlScheme {

    /** The client's own controls, and nothing more. */
    ControlScheme CLASSIC = new ControlScheme() {
    };

    /** What a scheme reads of the window and works through; on the client's thread. */
    interface Context {

        DukeGame game();

        /** The picture now; null before the first. */
        WorldSnapshot snapshot();

        /**
         * The window's input, for a scheme that reads fingers itself: {@code addRawInputListener} gets every touch, with
         * its pointer id, which the one mouse the client reads cannot tell apart. Null where there is none.
         */
        default InputManager input() {
            return null;
        }

        /** Whether the player is playing: no menu, no screen of the game's holding the keys, the window focused. */
        boolean playing();

        /** His selected things, in the order chosen. */
        List<ObjectId> selected();

        /** Where a thing is drawn now, in the world's x and y; null for one not drawn. */
        float[] drawnAt(int id);

        /** How high a thing's model stands, world units; 0 for one not drawn. */
        float topOf(int id);

        /** The thing under the pointer, or null. */
        UnitView pointedUnit();

        /** The ground under the pointer, in the world's x, y and height; null off the world. */
        float[] pointedGround();

        /** The ground under a point of the window ({@code screenY} up from its bottom), as {@link #pointedGround}. */
        default float[] groundAt(float screenX, float screenY) {
            return null;
        }

        float floorHeight(float x, float y);

        /** Whether the letter or digit {@code key} is down now. */
        boolean keyHeld(char key);

        /** Whether the left button is down now. */
        boolean leftHeld();

        /** The client's clock, seconds. */
        float time();

        /** The camera's turn, radians: 0 looking up the map. */
        float yaw();

        float pitch();

        /** Where the camera looks, the world's x and y. */
        float[] lookingAt();

        void lookAt(float x, float y);

        void turnBy(float radians);

        /** Tilt by {@code radians}, kept within {@code band} of {@code base}. */
        void tilt(float radians, float base, float band);

        /** Hold the mouse — the pointer hidden and its motion read — while nothing wants the pointer; or let it go. */
        void holdMouse(boolean held);

        boolean mouseHeld();

        /** How far the held mouse moved this frame, pixels across and down; none while it is not held. */
        float[] mouseMoved();

        /** The window's width and height, pixels; null where unknown. */
        default float[] screenSize() {
            return null;
        }

        /**
         * How many pixels of this screen one pixel of the interface's design is ({@code ClientPlatform.uiScale}): a scheme
         * sizes what a finger must hit, and how far it may stray, by it. 1 where the screen is the design's density.
         */
        default float uiScale() {
            return 1f;
        }

        /** Where the interface is drawn, and its font. */
        Node gui();

        BitmapFont font();

        /** Where a point of the world (its x, height, y) falls on the screen; z over 1 behind the eye. */
        Vector3f screenOf(float x, float height, float y);

        /** Come in or out by {@code factor}: under 1 comes nearer, kept between the camera's nearest and furthest. */
        default void zoomBy(float factor) {
        }

        /** Begin a selecting box where the pointer is, which {@link #endBox} closes where it has got to. */
        default void beginBox() {
        }

        /** Close the box begun: what is inside it chosen, or, where it did not move, what is pointed at. */
        default void endBox(boolean add) {
        }

        /** Choose the thing pointed at, as the left click does; {@code add} to what is chosen already. */
        default void selectPointed(boolean add) {
        }

        /** Give the chosen things the order the right click gives at the pointer. */
        default void orderPointed() {
        }

        /** Press the skill on {@code key} as a click on its slot does, one the scheme aims ({@link ControlScheme#armed}) included. */
        default void pressSkill(char key) {
        }

        /** Put down the armed button's place that {@link ControlScheme#holdsPlacement} held, where the aim now is. */
        default void putDownArmed() {
        }

        /** Whether a press of the armed button's place is held now, to be put down by {@link #putDownArmed} or given up. */
        default boolean placing() {
            return false;
        }

        /** Give the armed button up, as Esc does: nothing sent. */
        default void cancelArmed() {
        }

        /** Cast what is armed, aimed where the scheme's {@link #aimPoint} says. */
        void castArmed();

        /** How far a thing's weapon reaches now, read off the world; 0 for none. */
        float reachOf(ObjectId id);
    }

    /** Given its context once, as the window opens. */
    default void attach(Context context) {
    }

    /** Each frame of the window, before the camera is placed. */
    default void frame(float tpf) {
    }

    /** Each frame the player steers the camera himself: {@code panned} whether he panned it this frame. */
    default void steer(boolean panned, float tpf) {
    }

    /** The left button on the world, nothing armed: true where the scheme took it, false for the client's selecting. */
    default boolean left(boolean pressed) {
        return false;
    }

    /**
     * Whether letting go of a press on an armed button's place leaves it where it is — the aim ({@link #aiming}, {@link
     * #aimPoint}) and the ghost follow, and the scheme puts it down with {@link Context#putDownArmed} — rather than
     * putting it down as the client does.
     */
    default boolean holdsPlacement() {
        return false;
    }

    /** Whether the right button orders: {@code worded} the game names a word for the click, {@code onAThing} on one. */
    default boolean rightOrders(boolean worded, boolean onAThing) {
        return true;
    }

    /**
     * A key armed, by the mouse or not: true where the scheme aims it itself ({@link #aimPoint}) — the key again, the
     * left button or, where {@link #castsOnRelease}, letting it go casting it.
     */
    default boolean armed(char key, boolean byMouse, SkillRange range) {
        return false;
    }

    /** Where what the scheme aims points now, the world's x and y; null for nowhere. */
    default float[] aimPoint() {
        return null;
    }

    /** Whether it aims what is armed now. */
    default boolean aiming() {
        return false;
    }

    /** Whether letting the key it aims go casts it. */
    default boolean castsOnRelease() {
        return false;
    }

    /** What was armed is cast or given up. */
    default void disarmed() {
    }

    /** Whether a key armed by its letter casts at once at the pointer, a creature within six of it counting. */
    default boolean castsAtOnce(boolean byMouse) {
        return false;
    }
}
