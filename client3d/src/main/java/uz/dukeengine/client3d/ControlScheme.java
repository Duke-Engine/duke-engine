package uz.dukeengine.client3d;

import com.jme3.font.BitmapFont;
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

        /** Where the interface is drawn, and its font. */
        Node gui();

        BitmapFont font();

        /** Where a point of the world (its x, height, y) falls on the screen; z over 1 behind the eye. */
        Vector3f screenOf(float x, float height, float y);

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
