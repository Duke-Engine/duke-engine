package uz.dukeengine.client3d;

import com.jme3.input.KeyInput;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The keys the client's own controls are on — which the game says, as it says everything else a player meets.
 *
 * <p>The client owned its keys: WASD and the arrows panned, H halted, P paused, the digits built. A game made
 * after the reference cannot live with that, because its players already know where everything is — in the
 * one this was measured in, 96 bindings, W selecting every aircraft and S stopping, the digits being control
 * groups, F1 to F8 camera bookmarks. So a game hands over a map ({@link Hotkeys#controls}), and a control it
 * binds to nothing has no key at all. {@link #standard()} is the keys the client always had.
 *
 * <p>A key is written with the modifiers that must be held with it: {@code Key.ctrl(KeyInput.KEY_1)} is Ctrl+1
 * and not 1. The three <em>held</em> controls — {@link Control#FORCE_ATTACK}, {@link Control#QUEUE_WAYPOINTS}
 * and {@link Control#ADD_TO_SELECTION} — are keys held down while something else is done, usually the
 * modifier keys themselves.
 */
public final class KeyMap {

    /** A key, and which of Ctrl, Shift and Alt must be held with it — jME's {@link KeyInput} codes. */
    public record Key(int code, boolean ctrl, boolean shift, boolean alt) {

        public static Key of(int code) {
            return new Key(code, false, false, false);
        }

        public static Key ctrl(int code) {
            return new Key(code, true, false, false);
        }

        public static Key shift(int code) {
            return new Key(code, false, true, false);
        }

        public static Key alt(int code) {
            return new Key(code, false, false, true);
        }
    }

    /** The client's own controls. */
    public enum Control {
        PAN_UP, PAN_LEFT, PAN_DOWN, PAN_RIGHT,
        TURN_LEFT, TURN_RIGHT, ZOOM_IN, ZOOM_OUT,
        /** The camera back to how it started: unturned, at its first distance. */
        RESET_VIEW,
        STOP,
        /** The selection spreads out, each away from the middle of them by four times its own size. */
        SCATTER,
        /** Held: an order given while it is, is an attack — on a friend as on a foe. */
        FORCE_ATTACK,
        /** Held: a move given while it is, waits for the one before it. */
        QUEUE_WAYPOINTS,
        /** Held: what is picked is added to the selection rather than replacing it. */
        ADD_TO_SELECTION,
        /** Every one of the player's units, but his buildings and the kinds the game leaves out. */
        SELECT_ALL,
        /** Every one of the player's units of the kind the game names — every aircraft, say. */
        SELECT_ALL_OF_KIND,
        /** Every unit on screen of a type selected; pressed again soon after, every one on the map. */
        SELECT_SAME_TYPE,
        NEXT_UNIT, PREVIOUS_UNIT,
        /** The camera to his base: the first of his things of the kind the game names. */
        CENTRE_ON_BASE,
        /** The camera to where the latest alert was raised. */
        LATEST_ALERT,
        OPTIONS, TOGGLE_COMMAND_BAR, CHAT_ALL, CHAT_ALLIES, SCREENSHOT, PAUSE, FULLSCREEN;

        /** Whether it is held down rather than pressed: it lasts as long as its key does. */
        public boolean isHeld() {
            return switch (this) {
                case PAN_UP, PAN_LEFT, PAN_DOWN, PAN_RIGHT, TURN_LEFT, TURN_RIGHT, ZOOM_IN, ZOOM_OUT, FORCE_ATTACK,
                        QUEUE_WAYPOINTS, ADD_TO_SELECTION -> true;
                default -> false;
            };
        }
    }

    /**
     * A control group's keys — the reference's {@code CREATE_TEAM}, {@code SELECT_TEAM}, {@code ADD_TEAM} and
     * {@code VIEW_TEAM}: make the selection the group; select the group (pressed twice quickly, and the camera
     * goes to it); add the group to the selection; and put the camera on it without selecting it. Any of them
     * may be null.
     */
    public record GroupKeys(Key create, Key select, Key add, Key centre) {
    }

    /** A camera bookmark's keys: keep the view as it is now, and go back to it. */
    public record BookmarkKeys(Key save, Key recall) {
    }

    /** SelectionXlat's double tap: the same group again within 20 of the game's frames, two thirds of a second. */
    static final float DOUBLE_PRESS_SECONDS = 20f / 30f;

    private static final int[] DIGITS = {KeyInput.KEY_0, KeyInput.KEY_1, KeyInput.KEY_2, KeyInput.KEY_3,
        KeyInput.KEY_4, KeyInput.KEY_5, KeyInput.KEY_6, KeyInput.KEY_7, KeyInput.KEY_8, KeyInput.KEY_9};

    private static final int[] FUNCTION_KEYS = {KeyInput.KEY_F1, KeyInput.KEY_F2, KeyInput.KEY_F3, KeyInput.KEY_F4,
        KeyInput.KEY_F5, KeyInput.KEY_F6, KeyInput.KEY_F7, KeyInput.KEY_F8, KeyInput.KEY_F9, KeyInput.KEY_F10,
        KeyInput.KEY_F11, KeyInput.KEY_F12};

    private final Map<Control, List<Key>> keys = new EnumMap<>(Control.class);
    private final Map<Integer, GroupKeys> groups = new TreeMap<>();
    private final Map<Integer, BookmarkKeys> bookmarks = new TreeMap<>();
    private String baseKind;
    private String kindToSelect;
    private final Set<String> leftOutOfSelectAll = new LinkedHashSet<>();
    private float doublePressSeconds = DOUBLE_PRESS_SECONDS;

    private KeyMap() {
    }

    /** No control on any key: a game that binds each one it wants. */
    public static KeyMap empty() {
        return new KeyMap();
    }

    /**
     * The keys the client has always had: WASD and the arrows pan, H stops, P pauses, F11 is fullscreen, Escape
     * the menu, Enter and Space bring the camera back to the player's own, Shift adds to a selection. The
     * digits, with no group on them, still build.
     */
    public static KeyMap standard() {
        return new KeyMap()
                .bind(Control.PAN_UP, Key.of(KeyInput.KEY_W), Key.of(KeyInput.KEY_UP))
                .bind(Control.PAN_LEFT, Key.of(KeyInput.KEY_A), Key.of(KeyInput.KEY_LEFT))
                .bind(Control.PAN_DOWN, Key.of(KeyInput.KEY_S), Key.of(KeyInput.KEY_DOWN))
                .bind(Control.PAN_RIGHT, Key.of(KeyInput.KEY_D), Key.of(KeyInput.KEY_RIGHT))
                .bind(Control.STOP, Key.of(KeyInput.KEY_H))
                .bind(Control.PAUSE, Key.of(KeyInput.KEY_P))
                .bind(Control.FULLSCREEN, Key.of(KeyInput.KEY_F11))
                .bind(Control.OPTIONS, Key.of(KeyInput.KEY_ESCAPE))
                .bind(Control.CENTRE_ON_BASE, Key.of(KeyInput.KEY_RETURN), Key.of(KeyInput.KEY_NUMPADENTER),
                        Key.of(KeyInput.KEY_SPACE))
                .bind(Control.ADD_TO_SELECTION, Key.of(KeyInput.KEY_LSHIFT), Key.of(KeyInput.KEY_RSHIFT));
    }

    /** Put a control on these keys, instead of any it had; none leaves it on no key at all. */
    public KeyMap bind(Control control, Key... on) {
        keys.put(control, List.of(on));
        return this;
    }

    /** Take a control off every key. */
    public KeyMap unbind(Control control) {
        return bind(control);
    }

    /** Control group {@code number}'s keys; a group with no keys is no group. */
    public KeyMap group(int number, GroupKeys on) {
        groups.put(number, on);
        return this;
    }

    /**
     * Groups 0 to 9 on the digits as most of the genre has them: Ctrl and the digit makes one, the digit selects
     * it, Shift and it adds it to the selection, Alt and it goes to it. Digits that are groups build nothing.
     */
    public KeyMap groupsOnDigits() {
        for (int number = 0; number < DIGITS.length; number++) {
            int digit = DIGITS[number];
            group(number, new GroupKeys(Key.ctrl(digit), Key.of(digit), Key.shift(digit), Key.alt(digit)));
        }
        return this;
    }

    /** Camera bookmark {@code number}'s keys. */
    public KeyMap bookmark(int number, BookmarkKeys on) {
        bookmarks.put(number, on);
        return this;
    }

    /** {@code count} bookmarks on F1 onwards: Ctrl and the key keeps the view, the key goes back to it. */
    public KeyMap bookmarksOnFunctionKeys(int count) {
        for (int number = 0; number < Math.min(count, FUNCTION_KEYS.length); number++) {
            bookmark(number, new BookmarkKeys(Key.ctrl(FUNCTION_KEYS[number]), Key.of(FUNCTION_KEYS[number])));
        }
        return this;
    }

    /** The kind {@link Control#CENTRE_ON_BASE} looks for; none puts the camera on any of the player's own. */
    public KeyMap baseKind(String kind) {
        this.baseKind = kind;
        return this;
    }

    /** The kind {@link Control#SELECT_ALL_OF_KIND} selects: {@code AIRCRAFT}, say. */
    public KeyMap kindToSelect(String kind) {
        this.kindToSelect = kind;
        return this;
    }

    /** Kinds {@link Control#SELECT_ALL} leaves out, as the reference leaves out its dozers and harvesters. */
    public KeyMap leftOutOfSelectAll(Collection<String> kinds) {
        leftOutOfSelectAll.clear();
        leftOutOfSelectAll.addAll(kinds);
        return this;
    }

    /** How soon a second press counts as a double press: two thirds of a second, as the reference's groups. */
    public KeyMap doublePress(float seconds) {
        this.doublePressSeconds = Math.max(0f, seconds);
        return this;
    }

    // ---- reading it ----

    /** The keys a control is on, or none. */
    public List<Key> keysOf(Control control) {
        return keys.getOrDefault(control, List.of());
    }

    Map<Integer, GroupKeys> groups() {
        return groups;
    }

    Map<Integer, BookmarkKeys> bookmarks() {
        return bookmarks;
    }

    String baseKind() {
        return baseKind;
    }

    String kindToSelect() {
        return kindToSelect;
    }

    Set<String> leftOutOfSelectAll() {
        return leftOutOfSelectAll;
    }

    float doublePressSeconds() {
        return doublePressSeconds;
    }

    /** Every key code anything here is on, for the client to listen to. */
    Set<Integer> codes() {
        var codes = new LinkedHashSet<Integer>();
        keys.values().forEach(on -> on.forEach(key -> codes.add(key.code())));
        for (var group : groups.values()) {
            for (var key : new Key[] {group.create(), group.select(), group.add(), group.centre()}) {
                if (key != null) {
                    codes.add(key.code());
                }
            }
        }
        for (var bookmark : bookmarks.values()) {
            for (var key : new Key[] {bookmark.save(), bookmark.recall()}) {
                if (key != null) {
                    codes.add(key.code());
                }
            }
        }
        return codes;
    }

    /** Whether the map puts anything on a digit — a control group, most often — which stops the digits building. */
    boolean takesDigits() {
        var codes = codes();
        for (int digit : DIGITS) {
            if (codes.contains(digit)) {
                return true;
            }
        }
        return false;
    }
}
