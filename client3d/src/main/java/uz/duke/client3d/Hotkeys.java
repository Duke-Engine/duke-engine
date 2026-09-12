package uz.duke.client3d;

import com.jme3.input.KeyInput;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import uz.duke.core.math.Coord3D;
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

    /**
     * What a key needs before it can do anything.
     *
     * <p>Some orders are not complete without a thing to aim them at, and the only
     * one who knows what that is is the player. So the client keeps the two-step:
     * press the key, then click; press it again, click elsewhere, or press escape
     * to think better of it. What to do once it is aimed stays the game's.
     */
    public enum Aim {
        /** Nothing: the key acts the moment it is pressed. */
        NOW,
        /** A creature, chosen by clicking one. */
        UNIT,
        /** A spot on the floor, chosen by clicking it. */
        GROUND,
        /**
         * The same, but only somewhere the player could stand and has already
         * seen -- not into stone, and not into the dark.
         *
         * <p>The two halves are one idea. A leap that ends inside a wall is not a
         * leap, and a leap into ground nobody has walked is a guess: the player
         * cannot see whether it is a room, a corridor or the middle of the rock,
         * so letting him aim there is offering him a coin toss dressed as a
         * decision. Ground he lit once and has since forgotten stays fair game --
         * he knows what is there, even if he cannot see it now.
         */
        OPEN_GROUND
    }

    /** What the player pointed at — one of the two is filled in, per the aim. */
    record Aimed(int unitId, Coord3D point) {
    }

    record Binding(Aim aim, BiConsumer<DukeGame, Aimed> run) {
    }

    /** In declaration order, so a game's own listing order is what gets bound. */
    private final Map<Character, Binding> bindings = new LinkedHashMap<>();

    /** What to do when the player picks one of the choices the game put on screen. */
    private java.util.function.ObjIntConsumer<DukeGame> chosen;

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
        return bind(key, Aim.NOW, (game, aimed) -> action.accept(game));
    }

    /**
     * Bind a letter that first asks the player to click a creature.
     *
     * <p>The press arms it and nothing is sent; the next left-click on a creature
     * sends the order with that creature's id. Clicking anywhere else, pressing the
     * key again, or pressing escape drops it.
     */
    public Hotkeys onUnit(char key, java.util.function.ObjIntConsumer<DukeGame> action) {
        return bind(key, Aim.UNIT, (game, aimed) -> action.accept(game, aimed.unitId()));
    }

    /** The same, for a letter that first asks the player to click the floor. */
    public Hotkeys onGround(char key, BiConsumer<DukeGame, Coord3D> action) {
        return bind(key, Aim.GROUND, (game, aimed) -> action.accept(game, aimed.point()));
    }

    /**
     * The same again, for a letter that may only be pointed at open ground the
     * player has already seen -- see {@link Aim#OPEN_GROUND}.
     *
     * <p>The client enforces it, because the client is the one holding what the
     * player has seen. A click on stone or on the dark drops the aim and sends
     * nothing, exactly as a click on nothing does.
     */
    public Hotkeys onOpenGround(char key, BiConsumer<DukeGame, Coord3D> action) {
        return bind(key, Aim.OPEN_GROUND, (game, aimed) -> action.accept(game, aimed.point()));
    }

    /**
     * What to do when the player spends a level on a slot.
     *
     * <p>Its own door rather than a letter, because it is not one: it is a click
     * on the little badge over a socket, and the letter beside it already means
     * "cast this". A game that never registers one simply has no such button --
     * the panel draws a badge only when the game says a point may go there, and
     * a game that says nothing says it about every slot.
     */
    public Hotkeys onRaiseSkill(BiConsumer<DukeGame, Character> action) {
        this.raiseSkill = action;
        return this;
    }

    /** Spend a level on a slot, if this game has anything to spend it on. */
    public void raiseSkill(DukeGame game, char key) {
        if (raiseSkill != null) {
            raiseSkill.accept(game, key);
        }
    }

    private BiConsumer<DukeGame, Character> raiseSkill;

    private Hotkeys bind(char key, Aim aim, BiConsumer<DukeGame, Aimed> run) {
        bindings.put(Character.toUpperCase(key), new Binding(aim, run));
        return this;
    }

    Map<Character, Binding> all() {
        return bindings;
    }

    /**
     * The letters the game has claimed, in the order it claimed them.
     *
     * <p>Public because a game may reasonably want to check its own work — that
     * every skill in its data file got a key, and that each one asks for what its
     * effect needs pointing at. What the key <em>does</em> stays private: that is
     * the client's business, and it is the same work whether the letter was
     * pressed or the slot on the bar was clicked.
     */
    public java.util.Set<Character> claimedKeys() {
        return java.util.Collections.unmodifiableSet(bindings.keySet());
    }

    /** What that key needs pointed at before it can act, or {@code null} for none. */
    public Aim aimOf(char key) {
        var binding = bindings.get(Character.toUpperCase(key));
        return binding == null ? null : binding.aim();
    }

    /**
     * What to do when the player takes one of the offered choices.
     *
     * <p>A game may put a set of choices on screen through the status channel —
     * what a level-up is worth, which of three doors — and the client draws them
     * and reports which was clicked, by its position in the list. What that means
     * is the game's, and like every other binding here the work is "post a
     * command": the render thread has no business in the simulation.
     *
     * <p>A game that never offers anything never binds this and nothing changes.
     */
    public Hotkeys onChoose(java.util.function.ObjIntConsumer<DukeGame> action) {
        this.chosen = action;
        return this;
    }

    /** Tell the game a choice was taken. Silently ignored if it offers none. */
    void choose(DukeGame game, int index) {
        if (chosen != null) {
            chosen.accept(game, index);
        }
    }

    private java.util.function.ObjIntConsumer<DukeGame> watched;

    /**
     * What to do when the player picks out one unit to look at.
     *
     * <p>Selection is the client's — it is a thing about this screen, and the
     * simulation neither has one nor should. But <em>what a creature is worth</em>
     * is the simulation's and nothing else can answer it: how hard it hits is its
     * template times whatever this floor multiplies by, and the client has never
     * seen a template. So the two meet here, the same way they meet over a
     * level-up card: the client says which one, the game says what to make of it.
     *
     * <p>Reported when the selection changes rather than every frame, and only
     * when it is a single unit — a panel describing one creature cannot describe
     * nine. {@code -1} means he has let go of everything.
     *
     * <p>A game that never binds this gets whatever status line it writes on its
     * own, which is what every game had.
     */
    public Hotkeys onWatch(java.util.function.ObjIntConsumer<DukeGame> action) {
        this.watched = action;
        return this;
    }

    /** Tell the game what the player is looking at. Ignored if it does not care. */
    void watch(DukeGame game, int unitId) {
        if (watched != null) {
            watched.accept(game, unitId);
        }
    }

    /** Whether the game wants to be told what is selected at all. */
    boolean watches() {
        return watched != null;
    }

    /** Whether the game has taken this key, leaving the client without it. */
    boolean claims(int keyCode) {
        for (var key : bindings.keySet()) {
            if (codeOf(key) == keyCode) {
                return true;
            }
        }
        return false;
    }

    /**
     * Of the keys a client control would like, the ones still free.
     *
     * <p>Two mappings on one key both fire — an input manager adds bindings, it
     * never replaces them — so a dungeon whose W casts a skill was panning the
     * camera with it as well. The client cannot fix that by binding later; it has
     * to not bind the key at all.
     *
     * <p>Only the taken key goes. A control with a second key keeps it, which is
     * why a game may claim WASD and the camera still pans on the arrows.
     */
    int[] unclaimed(int... codes) {
        int free = 0;
        for (int code : codes) {
            if (!claims(code)) {
                free++;
            }
        }
        var left = new int[free];
        int at = 0;
        for (int code : codes) {
            if (!claims(code)) {
                left[at++] = code;
            }
        }
        return left;
    }

    /** The jME key code for a letter, or -1 for anything that is not one. */
    static int codeOf(char key) {
        char letter = Character.toUpperCase(key);
        return letter < 'A' || letter > 'Z' ? -1 : LETTERS[letter - 'A'];
    }
}
