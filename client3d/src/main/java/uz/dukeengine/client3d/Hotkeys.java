package uz.dukeengine.client3d;

import com.jme3.input.KeyInput;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.game.DukeGame;

/**
 * Keys the game claims for itself, and what it does when one is pressed.
 *
 * <p>The client owns the standard controls — select, order, pan, halt — because
 * they are the same in every game it draws; which keys they are on is the game's
 * to say ({@link #controls}). What a game adds on top it cannot
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
        OPEN_GROUND,

        /**
         * A creature if the click found one, and the floor if it did not.
         *
         * <p>For an order that means two related things depending on what is under
         * the cursor — attack THAT, or go and fight your way THERE. One key rather
         * than two because it is one intention, and because a player mid-fight
         * should not have to decide which of two buttons he wants before he knows
         * whether his click will land on anything.
         */
        UNIT_OR_GROUND;

        /**
         * Whether this leaves the player something to point at.
         *
         * <p>Which is not the same question as "is a key armed". A skill that goes
         * off around the man who casts it is held only so that its reach can be
         * looked at before it is spent — there is nowhere to put it, and the click
         * that ends the holding means the same thing wherever it lands. Everything
         * that asks the player WHERE is one of the others.
         */
        public boolean needsPointing() {
            return this != NOW;
        }
    }

    /** What the player pointed at — one of the two is filled in, per the aim. */
    record Aimed(int unitId, Coord3D point) {
    }

    record Binding(Aim aim, BiConsumer<DukeGame, Aimed> run) {
    }

    /** In declaration order, so a game's own listing order is what gets bound. */
    private final Map<Character, Binding> bindings = new LinkedHashMap<>();
    /** What each letter does, in the game's words, for the Settings screen's list of keys. */
    private final Map<Character, String> words = new LinkedHashMap<>();

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

    /** The same, saying what the letter does in the game's words — shown on the Settings screen's list of keys. */
    public Hotkeys on(char key, String word, Consumer<DukeGame> action) {
        describe(key, word);
        return on(key, action);
    }

    /**
     * Say what a letter does in the game's words, for the Settings screen's list of keys — a letter bound any way, or
     * one the game takes on its own canvas and never binds here.
     */
    public Hotkeys describe(char key, String word) {
        words.put(Character.toUpperCase(key), word == null ? "" : word);
        return this;
    }

    /** What the game says a letter does, or "" for nothing said. */
    public String wordOf(char key) {
        return words.getOrDefault(Character.toUpperCase(key), "");
    }

    /** The letters the game has said something about, bound or not, in the order it said them. */
    public java.util.Set<Character> describedKeys() {
        return java.util.Collections.unmodifiableSet(words.keySet());
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
     * The same again, for a letter that takes either — a creature if the click
     * found one, the floor if it did not, and a different order for each.
     *
     * <p>Two pieces of work rather than one taking both, because what arrives is
     * one or the other and never both, and a single callback would have to be
     * handed the client's own {@code Aimed} to find out which. That type is the
     * client's business: a game says what it wants done with a creature and what
     * it wants done with a spot, and is told neither how the player chose nor how
     * the choosing is drawn.
     */
    public Hotkeys onUnitOrGround(char key, java.util.function.ObjIntConsumer<DukeGame> atUnit,
            BiConsumer<DukeGame, Coord3D> atGround) {
        return bind(key, Aim.UNIT_OR_GROUND, (game, aimed) -> {
            if (aimed.point() == null) {
                atUnit.accept(game, aimed.unitId());
            } else {
                atGround.accept(game, aimed.point());
            }
        });
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

    // ---- the client's own controls ----

    private KeyMap keyMap = KeyMap.standard();
    private BiConsumer<DukeGame, Boolean> chat;
    private DirectionKeys directionKeys;
    private BiConsumer<DukeGame, Integer> directionChanged;

    /**
     * Walk by held keys: the client reads {@code keys} each frame, turns what is held — up the screen, left, down,
     * right — into a direction on the ground as the camera is turned ({@link DirectionKeys#angleOf}, 256ths of a turn,
     * or {@link DirectionKeys#NONE}), and tells {@code changed} only when it changes: a key pressed or let go, the
     * camera turned. While a menu, a screen of the game's that holds the keys ({@link CanvasInput#holdsKeys}) or
     * another window has them, it is told {@code NONE} once. The game sends it on as {@code CombatOrder.MoveAlong}.
     */
    public Hotkeys onDirection(DirectionKeys keys, BiConsumer<DukeGame, Integer> changed) {
        this.directionKeys = keys == null ? DirectionKeys.WASD : keys;
        this.directionChanged = changed;
        return this;
    }

    DirectionKeys directionKeys() {
        return directionKeys;
    }

    BiConsumer<DukeGame, Integer> directionChanged() {
        return directionChanged;
    }

    /**
     * The keys the client's own controls are on — see {@link KeyMap}. Left alone, {@link KeyMap#standard()}: the
     * keys the client has always had. The game's own letters above still come first: a letter bound here and
     * claimed with {@link #on} is the game's.
     */
    public Hotkeys controls(KeyMap keys) {
        this.keyMap = keys == null ? KeyMap.standard() : keys;
        return this;
    }

    public KeyMap keyMap() {
        return keyMap;
    }

    /**
     * What to do when the player asks to talk: to everyone, or — {@code false} — to his allies only. The engine
     * has no chat of its own to open; a game that has one opens it here. None by default, and the keys do
     * nothing.
     */
    public Hotkeys onChat(BiConsumer<DukeGame, Boolean> toEveryone) {
        this.chat = toEveryone;
        return this;
    }

    /** The player asked to talk, to everyone or to his allies. */
    public void chat(DukeGame game, boolean toEveryone) {
        if (chat != null) {
            chat.accept(game, toEveryone);
        }
    }

    /**
     * Do what a letter does, for a letter that needs nothing pointed at.
     *
     * <p>The road every such key travels, the client's own press included, so
     * there is one of them rather than two that have to be kept in step.
     *
     * <p><b>Public because otherwise nobody can ask what a key really sends.</b>
     * Three separate faults in this game have had the same shape — a key that was
     * bound, drawn, listed and lit, and sent nothing — and every one of them was
     * invisible to a test that could see the binding but not press it. A game can
     * check its own controls with {@link #claimedKeys()} and {@link #aimOf}; this
     * is how it checks what they DO.
     *
     * @return whether there was such a key to press
     */
    public boolean pressNow(DukeGame game, char key) {
        var binding = bindings.get(Character.toUpperCase(key));
        if (binding == null || binding.aim() != Aim.NOW) {
            return false;
        }
        binding.run().accept(game, null);
        return true;
    }

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
        return actionOn(keyCode) != 0;
    }

    /** Each binding the player put on a key of his own, by the letter the game bound it to. */
    private final Map<Character, Integer> rebound = new LinkedHashMap<>();

    /**
     * Put the game's binding {@code action} — the letter it was bound to — on the key {@code code}: pressing it does
     * what the letter did. A binding already on that key goes to the action's old key, so the two change places.
     */
    public void rebind(char action, int code) {
        char key = Character.toUpperCase(action);
        if (!bindings.containsKey(key) || code < 0) {
            return;
        }
        char clash = actionOn(code);
        int was = codeFor(key);
        if (clash != 0 && clash != key) {
            putOn(clash, was);
        }
        putOn(key, code);
    }

    private void putOn(char action, int code) {
        if (code == codeOf(action)) {
            rebound.remove(action);
        } else {
            rebound.put(action, code);
        }
    }

    /** The key the binding {@code action} is on now: the player's, or its own letter's. */
    public int codeFor(char action) {
        char key = Character.toUpperCase(action);
        return rebound.getOrDefault(key, codeOf(key));
    }

    /** The binding on the key {@code code} — the letter the game bound it to — or 0 for none. */
    public char actionOn(int code) {
        for (var key : bindings.keySet()) {
            if (codeFor(key) == code) {
                return key;
            }
        }
        return 0;
    }

    /** The bindings put on keys of the player's own, by their letters. */
    public Map<Character, Integer> rebound() {
        return java.util.Collections.unmodifiableMap(rebound);
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

    /** What a key code is called on a keyboard, for a list of keys: the letter, the digit, F5, Esc, Space. */
    public static String nameOf(int code) {
        for (int at = 0; at < LETTERS.length; at++) {
            if (LETTERS[at] == code) {
                return String.valueOf((char) ('A' + at));
            }
        }
        if (code >= KeyInput.KEY_1 && code <= KeyInput.KEY_9) {
            return String.valueOf(code - KeyInput.KEY_1 + 1);
        }
        if (code >= KeyInput.KEY_F1 && code <= KeyInput.KEY_F10) {
            return "F" + (code - KeyInput.KEY_F1 + 1);
        }
        return switch (code) {
            case KeyInput.KEY_0 -> "0";
            case KeyInput.KEY_F11 -> "F11";
            case KeyInput.KEY_F12 -> "F12";
            case KeyInput.KEY_ESCAPE -> "Esc";
            case KeyInput.KEY_RETURN -> "Enter";
            case KeyInput.KEY_SPACE -> "Space";
            case KeyInput.KEY_TAB -> "Tab";
            case KeyInput.KEY_BACK -> "Backspace";
            case KeyInput.KEY_DELETE -> "Delete";
            case KeyInput.KEY_HOME -> "Home";
            case KeyInput.KEY_END -> "End";
            case KeyInput.KEY_UP -> "Up";
            case KeyInput.KEY_DOWN -> "Down";
            case KeyInput.KEY_LEFT -> "Left";
            case KeyInput.KEY_RIGHT -> "Right";
            case KeyInput.KEY_PGUP -> "PageUp";
            case KeyInput.KEY_PGDN -> "PageDown";
            default -> "#" + code;
        };
    }

    /** The jME key code for a letter or a digit — what a {@link CanvasInput.Key} carries — or -1 for anything else. */
    public static int codeOf(char key) {
        if (key == '0') {
            return KeyInput.KEY_0;
        }
        if (key == ' ') {
            return KeyInput.KEY_SPACE; // the one key past letters and digits a game binds: a dodge's
        }
        if (key >= '1' && key <= '9') {
            return KeyInput.KEY_1 + (key - '1'); // the row of digits is in order on the keyboard, 0 after 9
        }
        char letter = Character.toUpperCase(key);
        return letter < 'A' || letter > 'Z' ? -1 : LETTERS[letter - 'A'];
    }
}
