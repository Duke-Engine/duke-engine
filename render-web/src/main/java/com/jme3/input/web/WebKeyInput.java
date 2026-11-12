package com.jme3.input.web;

import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.KeyInputEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * The keyboard while the canvas has focus (it is made focusable). Keys are read by their place on the keyboard,
 * {@code KeyboardEvent.code}, as jME's codes are; the character is the one the key typed.
 */
public class WebKeyInput implements KeyInput {
    private static final Map<String, Integer> CODES = new HashMap<>();

    static {
        String letters = "QWERTYUIOPASDFGHJKLZXCVBNM";
        int[] letterKeys = {KEY_Q, KEY_W, KEY_E, KEY_R, KEY_T, KEY_Y, KEY_U, KEY_I, KEY_O, KEY_P, KEY_A, KEY_S, KEY_D,
            KEY_F, KEY_G, KEY_H, KEY_J, KEY_K, KEY_L, KEY_Z, KEY_X, KEY_C, KEY_V, KEY_B, KEY_N, KEY_M};
        for (int i = 0; i < letters.length(); i++) {
            CODES.put("Key" + letters.charAt(i), letterKeys[i]);
        }
        int[] digits = {KEY_0, KEY_1, KEY_2, KEY_3, KEY_4, KEY_5, KEY_6, KEY_7, KEY_8, KEY_9};
        int[] numpad = {KEY_NUMPAD0, KEY_NUMPAD1, KEY_NUMPAD2, KEY_NUMPAD3, KEY_NUMPAD4, KEY_NUMPAD5, KEY_NUMPAD6,
            KEY_NUMPAD7, KEY_NUMPAD8, KEY_NUMPAD9};
        for (int i = 0; i < 10; i++) {
            CODES.put("Digit" + i, digits[i]);
            CODES.put("Numpad" + i, numpad[i]);
        }
        int[] f = {KEY_F1, KEY_F2, KEY_F3, KEY_F4, KEY_F5, KEY_F6, KEY_F7, KEY_F8, KEY_F9, KEY_F10, KEY_F11, KEY_F12};
        for (int i = 0; i < f.length; i++) {
            CODES.put("F" + (i + 1), f[i]);
        }
        Object[] named = {"Escape", KEY_ESCAPE, "Minus", KEY_MINUS, "Equal", KEY_EQUALS, "Backspace", KEY_BACK,
            "Tab", KEY_TAB, "BracketLeft", KEY_LBRACKET, "BracketRight", KEY_RBRACKET, "Enter", KEY_RETURN,
            "ControlLeft", KEY_LCONTROL, "ControlRight", KEY_RCONTROL, "Semicolon", KEY_SEMICOLON,
            "Quote", KEY_APOSTROPHE, "Backquote", KEY_GRAVE, "ShiftLeft", KEY_LSHIFT, "ShiftRight", KEY_RSHIFT,
            "Backslash", KEY_BACKSLASH, "Comma", KEY_COMMA, "Period", KEY_PERIOD, "Slash", KEY_SLASH,
            "AltLeft", KEY_LMENU, "AltRight", KEY_RMENU, "Space", KEY_SPACE, "CapsLock", KEY_CAPITAL,
            "NumLock", KEY_NUMLOCK, "ScrollLock", KEY_SCROLL, "NumpadSubtract", KEY_SUBTRACT, "NumpadAdd", KEY_ADD,
            "NumpadDecimal", KEY_DECIMAL, "NumpadEnter", KEY_NUMPADENTER, "NumpadMultiply", KEY_MULTIPLY,
            "NumpadDivide", KEY_DIVIDE, "Pause", KEY_PAUSE, "Home", KEY_HOME, "End", KEY_END, "PageUp", KEY_PGUP,
            "PageDown", KEY_PGDN, "Insert", KEY_INSERT, "Delete", KEY_DELETE, "ArrowUp", KEY_UP,
            "ArrowDown", KEY_DOWN, "ArrowLeft", KEY_LEFT, "ArrowRight", KEY_RIGHT, "MetaLeft", KEY_LMETA,
            "MetaRight", KEY_RMETA, "ContextMenu", KEY_APPS, "PrintScreen", KEY_PRTSCR};
        for (int i = 0; i < named.length; i += 2) {
            CODES.put((String) named[i], (Integer) named[i + 1]);
        }
    }

    private final JSObject canvas;
    private final List<KeyInputEvent> events = new ArrayList<>();
    private RawInputListener listener;
    private boolean initialized;

    public WebKeyInput(JSObject canvas) {
        this.canvas = canvas;
    }

    @JSFunctor
    interface Handler extends JSObject {
        void handle(boolean pressed, String code, String key, boolean repeat);
    }

    // Keys the page would act on itself (scrolling, focus moves, the context menu) are kept for the game.
    @JSBody(params = {"c", "h"}, script = ""
            + "if (!c.hasAttribute('tabindex')) c.tabIndex = 0;"
            + "c.addEventListener('mousedown', function() { c.focus(); });"
            + "function on(p) { return function(e) {"
            + "  if (!(e.ctrlKey || e.metaKey) || e.code.indexOf('Key') !== 0) e.preventDefault();"
            + "  h(p, e.code, e.key.length === 1 ? e.key : '', e.repeat); }; }"
            + "c.addEventListener('keydown', on(true)); c.addEventListener('keyup', on(false));")
    private static native void listen(JSObject canvas, Handler handler);

    @Override
    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        listen(canvas, this::handle);
    }

    private void handle(boolean pressed, String code, String key, boolean repeat) {
        Integer jme = CODES.get(code);
        events.add(new KeyInputEvent(jme == null ? KEY_UNKNOWN : jme, key.isEmpty() ? '\0' : key.charAt(0),
                pressed, repeat));
    }

    @Override
    public void update() {
        if (listener != null) {
            for (KeyInputEvent event : events) {
                listener.onKeyEvent(event);
            }
        }
        events.clear();
    }

    @Override
    public void destroy() {
        initialized = false;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void setInputListener(RawInputListener listener) {
        this.listener = listener;
    }

    @Override
    public long getInputTimeNanos() {
        return System.nanoTime();
    }

    @Override
    public String getKeyName(int key) {
        for (Map.Entry<String, Integer> e : CODES.entrySet()) {
            if (e.getValue() == key) {
                return e.getKey();
            }
        }
        return null;
    }
}
