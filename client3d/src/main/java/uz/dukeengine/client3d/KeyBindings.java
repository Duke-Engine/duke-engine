package uz.dukeengine.client3d;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.MouseButtonTrigger;

import java.util.ArrayList;
import java.util.List;

/**
 * Every control the client binds — the mouse's, every key anything is on, and the game's own letters — heard by one
 * listener; the keys the player put them on, kept and taken up again; and the keys page that lists them.
 */
final class KeyBindings {

    /**
     * Prefix of the input mapping for a key the game claimed.
     */
    static final String HOTKEY = "Hotkey";
    /** What a key's mapping is called: this and its code. */
    static final String KEY = "Key";

    private final InputManager inputManager;
    private final Hotkeys hotkeys;
    private final Controls controls;
    /** What the player set, kept in a file beside him — see {@link GameSettings}. */
    private final GameSettings preferences;
    /**
     * Every control this client has bound, so that one thing can be listened to.
     *
     * <p>★ Kept because forgetting is invisible. A mapping added and left out of
     * the listener's list is a key that is bound, reaches nothing and says
     * nothing about it: Ctrl shipped that way and simply did not exist, and there
     * is no error, no warning and nothing on screen to notice. The list is now
     * built by the binding rather than typed out beside it, so the two cannot
     * come apart.
     */
    private final List<String> bound = new ArrayList<>();
    /** What hears the game's keys, for a binding moved to another key. */
    private ActionListener hotkeyListener;
    /** What waits for the next key pressed to be its own — a game key, a walking key, a control — or null. */
    private Object capturing;

    KeyBindings(InputManager inputManager, Hotkeys hotkeys, Controls controls, GameSettings preferences) {
        this.inputManager = inputManager;
        this.hotkeys = hotkeys;
        this.controls = controls;
        this.preferences = preferences;
    }

    /**
     * Every control bound, all of them heard by {@code actions}: the mouse's buttons and wheel; every key anything is
     * on, one mapping each — what it does is worked out in one place, and never by two listeners racing to it — and
     * the game's own letters, on the keys the player last put them on.
     */
    void install(ActionListener actions) {
        bound.clear();
        hotkeyListener = null; // heard by the listener below, once
        map("Select", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        map("Order", new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        map("Turn", new MouseButtonTrigger(MouseInput.BUTTON_MIDDLE));
        map("ZoomIn", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        map("ZoomOut", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));
        takeTheRebindings(); // before the keys are counted: a control put on a key of his own is listened for
        for (int code : listenedKeys()) {
            map(KEY + code, new KeyTrigger(code));
        }
        mapTheHotkeys();
        // Everything that was bound, in one call. Not a list typed out here: see
        // the note on `bound`.
        inputManager.addListener(actions, bound.toArray(new String[0]));
        hotkeyListener = actions; // a binding put on another key later is heard by it too
    }

    /** Wait for the next key pressed to be this one's own: a game key's letter, or a control of the client's. */
    void await(Object action) {
        capturing = action;
    }

    /** Whether a key is waited for. */
    boolean capturing() {
        return capturing != null;
    }

    /** Each of the game's bindings on its key — its own letter's, or the one the player put it on. */
    private void mapTheHotkeys() {
        var names = new ArrayList<String>();
        for (var key : hotkeys.all().keySet()) {
            int code = hotkeys.codeFor(key);
            String mapping = HOTKEY + key;
            if (inputManager.hasMapping(mapping)) {
                inputManager.deleteMapping(mapping);
            }
            if (code < 0) {
                continue;
            }
            inputManager.addMapping(mapping, new KeyTrigger(code));
            names.add(mapping);
            if (!bound.contains(mapping)) {
                bound.add(mapping);
            }
        }
        if (hotkeyListener != null && !names.isEmpty()) {
            inputManager.addListener(hotkeyListener, names.toArray(new String[0])); // a mapping deleted lost its ear
        }
    }

    /**
     * Every key the client listens to: every one its controls, groups and bookmarks are on; the modifiers, which
     * change what the others mean; the keys a menu is walked with; every letter, for the command bar's buttons;
     * and — while nothing else has them — the digits, which build.
     */
    private java.util.Set<Integer> listenedKeys() {
        var codes = new java.util.LinkedHashSet<Integer>(controls.map().codes());
        codes.addAll(List.of(KeyInput.KEY_LSHIFT, KeyInput.KEY_RSHIFT, KeyInput.KEY_LCONTROL,
                KeyInput.KEY_RCONTROL, KeyInput.KEY_LMENU, KeyInput.KEY_RMENU, KeyInput.KEY_ESCAPE,
                KeyInput.KEY_RETURN, KeyInput.KEY_NUMPADENTER, KeyInput.KEY_SPACE, KeyInput.KEY_UP,
                KeyInput.KEY_DOWN, KeyInput.KEY_LEFT, KeyInput.KEY_RIGHT));
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            codes.add(Hotkeys.codeOf(letter));
        }
        if (!controls.map().takesDigits()) {
            codes.addAll(List.of(KeyInput.KEY_1, KeyInput.KEY_2, KeyInput.KEY_3, KeyInput.KEY_4, KeyInput.KEY_5,
                    KeyInput.KEY_6, KeyInput.KEY_7, KeyInput.KEY_8, KeyInput.KEY_9));
        }
        return codes;
    }

    /**
     * The next key pressed made the waiting key's own, and kept.
     *
     * @return whether it was, for the keys page to be shown again with it; false for Escape, which lets the asking go
     */
    boolean capture(KeyMap.Key pressed) {
        var waiting = capturing;
        capturing = null;
        int code = pressed.code();
        if (code == KeyInput.KEY_ESCAPE) {
            return false; // asked and let go
        }
        if (waiting instanceof KeyMap.Control control) {
            rebindControl(control, pressed);
            return true;
        }
        char action = (Character) waiting;
        hotkeys.rebind(action, code);
        for (var bound : hotkeys.all().keySet()) {
            preferences.set("key." + (int) bound, hotkeys.codeFor(bound));
        }
        mapTheHotkeys();
        return true;
    }

    /**
     * A control of the client's own put on {@code pressed} alone, kept as {@code ctl.<CONTROL>}; a control already on it
     * takes the moved control's old key, so the two change places.
     */
    private void rebindControl(KeyMap.Control control, KeyMap.Key pressed) {
        for (var touched : putOn(controls.map(), control, pressed)) {
            var keys = controls.map().keysOf(touched);
            preferences.set("ctl." + touched.name(), packed(keys.isEmpty() ? null : keys.getFirst()));
        }
        listenFor(pressed.code());
    }

    /**
     * {@code control} put on {@code pressed} alone; a control already on it given the moved one's old key in its place.
     *
     * @return the controls whose keys changed, the moved one first
     */
    static List<KeyMap.Control> putOn(KeyMap map, KeyMap.Control control, KeyMap.Key pressed) {
        var touched = new ArrayList<KeyMap.Control>(List.of(control));
        var was = map.keysOf(control);
        for (var other : KeyMap.Control.values()) {
            if (other != control && map.keysOf(other).contains(pressed)) {
                var theirs = new ArrayList<>(map.keysOf(other));
                theirs.remove(pressed);
                if (!was.isEmpty()) {
                    theirs.add(was.getFirst());
                }
                map.bind(other, theirs.toArray(new KeyMap.Key[0]));
                touched.add(other);
            }
        }
        map.bind(control, pressed);
        return touched;
    }

    /** A key with its modifiers in one number, as the settings file keeps it: -1 for none. */
    static int packed(KeyMap.Key key) {
        return key == null ? -1 : key.code() | (key.ctrl() ? 1 << 16 : 0) | (key.shift() ? 1 << 17 : 0)
                | (key.alt() ? 1 << 18 : 0);
    }

    /** The key a number kept by {@link #packed} names, or null for none. */
    static KeyMap.Key unpacked(int packed) {
        return packed < 0 ? null : new KeyMap.Key(packed & 0xFFFF, (packed & 1 << 16) != 0, (packed & 1 << 17) != 0,
                (packed & 1 << 18) != 0);
    }

    /** A key heard from now on, where nothing listened for it yet. */
    private void listenFor(int code) {
        String mapping = KEY + code;
        if (inputManager.hasMapping(mapping)) {
            return;
        }
        inputManager.addMapping(mapping, new KeyTrigger(code));
        bound.add(mapping);
        if (hotkeyListener != null) {
            inputManager.addListener(hotkeyListener, mapping);
        }
    }

    /** The keys he put the game's bindings and walking on, as he kept them last. */
    private void takeTheRebindings() {
        for (var control : KeyMap.Control.values()) {
            int kept = preferences.number("ctl." + control.name(), Integer.MIN_VALUE);
            if (kept != Integer.MIN_VALUE) {
                var key = unpacked(kept);
                controls.map().bind(control, key == null ? new KeyMap.Key[0] : new KeyMap.Key[] {key});
            }
        }
        for (var bound : java.util.List.copyOf(hotkeys.all().keySet())) {
            int code = preferences.number("key." + (int) bound, -1);
            if (code >= 0) {
                hotkeys.rebind(bound, code);
            }
        }
    }

    /** The letter a key is, or 0 for one that is not a letter. */
    static char letterOf(int code) {
        for (char letter = 'A'; letter <= 'Z'; letter++) {
            if (Hotkeys.codeOf(letter) == code) {
                return letter;
            }
        }
        return 0;
    }

    /**
     * Bind a control, and remember that it is one the listener has to hear.
     */
    private void map(String mapping, com.jme3.input.controls.Trigger... triggers) {
        inputManager.addMapping(mapping, triggers);
        bound.add(mapping);
    }

    /**
     * What each line of the keys page puts elsewhere, line for line with {@link #keyLines}: a game binding by its letter,
     * the four walking keys as {@code 1} to {@code 4}, nothing ({@code null}) for a letter the game only describes, and
     * each control of the client's own.
     */
    static List<Object> rebindable(Hotkeys hotkeys) {
        var actions = new ArrayList<Object>(new java.util.TreeSet<>(hotkeys.claimedKeys()));
        for (char letter : new java.util.TreeSet<Character>(hotkeys.describedKeys())) {
            if (!hotkeys.claimedKeys().contains(letter)) {
                actions.add(null);
            }
        }
        for (var control : KeyMap.Control.values()) {
            if (!hotkeys.keyMap().keysOf(control).isEmpty()) {
                actions.add(control);
            }
        }
        return actions;
    }

    /**
     * The lines of the Settings screen's list of keys: each letter the game claimed or said something of, with its
     * word; then each control of the client's own on its keys, called by its English name said the game's way.
     */
    static List<String> keyLines(Hotkeys hotkeys, java.util.function.UnaryOperator<String> say) {
        var lines = new ArrayList<String>();
        for (char letter : new java.util.TreeSet<Character>(hotkeys.claimedKeys())) {
            var word = hotkeys.wordOf(letter);
            var key = Hotkeys.nameOf(hotkeys.codeFor(letter));
            lines.add(word.isEmpty() ? key : key + "   " + word);
        }
        for (char letter : new java.util.TreeSet<Character>(hotkeys.describedKeys())) {
            if (!hotkeys.claimedKeys().contains(letter)) {
                var word = hotkeys.wordOf(letter);
                lines.add(word.isEmpty() ? String.valueOf(letter) : letter + "   " + word);
            }
        }
        var keyMap = hotkeys.keyMap();
        for (var control : KeyMap.Control.values()) {
            var on = keyMap.keysOf(control);
            if (on.isEmpty()) {
                continue;
            }
            var named = new ArrayList<String>();
            for (var key : on) {
                named.add((key.ctrl() ? "Ctrl+" : "") + (key.shift() ? "Shift+" : "") + (key.alt() ? "Alt+" : "")
                        + Hotkeys.nameOf(key.code()));
            }
            var english = control.name().charAt(0) + control.name().substring(1).toLowerCase(java.util.Locale.ROOT)
                    .replace('_', ' ');
            lines.add(String.join(", ", named) + "   " + say.apply(english));
        }
        return lines;
    }
}
