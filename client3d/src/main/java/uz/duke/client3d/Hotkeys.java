package uz.duke.client3d;

import com.jme3.input.KeyInput;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import uz.duke.game.DukeGame;

/**
 * Keys the game claims for itself, and what it does when one is pressed.
 *
 * <p>The client owns the standard controls — select, order, pan, halt — because
 * they are the same in every game it draws. What a game adds on top it cannot
 * expect the client to know: a dungeon's Q is a skill, and the client has never
 * heard of skills and should not have to.
 *
 * <p>So the game hands over a key and a piece of work. The work is deliberately
 * "post a command", not "do the thing": it runs on the render thread, where
 * touching the simulation would be a race, and the only safe thing to do with a
 * keypress there is put it in the queue. What comes of it happens on a frame
 * boundary, in the simulation, like every other order.
 */
public final class Hotkeys {

    /** jME's key codes follow a keyboard's scan order, so letters need a table. */
    private static final int[] LETTERS = {
        KeyInput.KEY_A, KeyInput.KEY_B, KeyInput.KEY_C, KeyInput.KEY_D, KeyInput.KEY_E,
        KeyInput.KEY_F, KeyInput.KEY_G, KeyInput.KEY_H, KeyInput.KEY_I, KeyInput.KEY_J,
        KeyInput.KEY_K, KeyInput.KEY_L, KeyInput.KEY_M, KeyInput.KEY_N, KeyInput.KEY_O,
        KeyInput.KEY_P, KeyInput.KEY_Q, KeyInput.KEY_R, KeyInput.KEY_S, KeyInput.KEY_T,
        KeyInput.KEY_U, KeyInput.KEY_V, KeyInput.KEY_W, KeyInput.KEY_X, KeyInput.KEY_Y,
        KeyInput.KEY_Z,
    };

    /** In declaration order, so a game's own listing order is what gets bound. */
    private final Map<Character, Consumer<DukeGame>> bindings = new LinkedHashMap<>();

    private Hotkeys() {
    }

    public static Hotkeys create() {
        return new Hotkeys();
    }

    /** A game that adds no keys of its own. */
    public static Hotkeys none() {
        return new Hotkeys();
    }

    /**
     * Bind a letter. Later bindings of the same letter replace earlier ones, and a
     * letter the client already uses is simply taken over by the game — its own
     * controls are the ones it knows it needs.
     */
    public Hotkeys on(char key, Consumer<DukeGame> action) {
        bindings.put(Character.toUpperCase(key), action);
        return this;
    }

    Map<Character, Consumer<DukeGame>> all() {
        return bindings;
    }

    /** The jME key code for a letter, or -1 for anything that is not one. */
    static int codeOf(char key) {
        char letter = Character.toUpperCase(key);
        return letter < 'A' || letter > 'Z' ? -1 : LETTERS[letter - 'A'];
    }
}
