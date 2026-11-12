package uz.dukeengine.client3d.web;

import com.jme3.app.state.ScreenshotAppState;
import com.jme3.system.JmeContext;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int32Array;
import uz.dukeengine.client3d.platform.ClientPlatform;
import uz.dukeengine.client3d.platform.GlyphRasterizer;
import uz.dukeengine.client3d.platform.HeldInput;
import uz.dukeengine.client3d.platform.ImageDecoder;
import uz.dukeengine.client3d.platform.SettingsStore;
import uz.dukeengine.client3d.platform.TextPrompt;
import uz.dukeengine.client3d.platform.Window;

/**
 * The client's platform in a browser page: glyphs and pictures off a canvas, settings in the page's local storage, the
 * browser's own prompt, the page as the window and what is held down in it.
 */
public final class WebPlatform implements ClientPlatform {

    private static final String SETTINGS = "duke-engine.settings";

    private final GlyphRasterizer glyphs = new CanvasGlyphs();
    private final ImageDecoder images = new CanvasImages();

    @JSBody(script = "return window.devicePixelRatio || 1;")
    private static native float pixelRatio();

    @JSBody(params = "key", script = "return localStorage.getItem(key);")
    private static native String stored(String key);

    @JSBody(params = {"key", "value"}, script = "localStorage.setItem(key, value);")
    private static native void store(String key, String value);

    @JSBody(params = {"prompt", "initial"}, script = "return window.prompt(prompt, initial || '');")
    private static native String ask(String prompt, String initial);

    @JSBody(script = "return document.fullscreenElement != null;")
    private static native boolean filling();

    @JSBody(params = "fill", script = "if (fill) { if (!document.fullscreenElement)"
            + " document.documentElement.requestFullscreen().catch(function() {}); }"
            + " else if (document.fullscreenElement) document.exitFullscreen();")
    private static native void fill(boolean fill);

    @JSBody(script = "var r = window.devicePixelRatio || 1;"
            + " return new Int32Array([Math.round(screen.width * r), Math.round(screen.height * r)]);")
    private static native Int32Array screenSize();

    /** Keeps what is held down from the page's own events, under a global of its own; once. */
    @JSBody(script = "if (globalThis.dukeHeld) return; var h = globalThis.dukeHeld"
            + " = {inside: false, buttons: {}, keys: {}};"
            + " var c = document.getElementById('duke');"
            + " c.addEventListener('mouseenter', function() { h.inside = true; });"
            + " c.addEventListener('mouseleave', function() { h.inside = false; });"
            + " window.addEventListener('mousedown', function(e) { h.buttons[e.button] = true; });"
            + " window.addEventListener('mouseup', function(e) { h.buttons[e.button] = false; });"
            + " function key(e) { var k = e.code; return k.indexOf('Key') === 0 || k.indexOf('Digit') === 0"
            + "   ? k.charAt(k.length - 1) : k; }"
            + " window.addEventListener('keydown', function(e) { h.keys[key(e)] = true; });"
            + " window.addEventListener('keyup', function(e) { h.keys[key(e)] = false; });"
            + " window.addEventListener('blur', function() { h.buttons = {}; h.keys = {}; });")
    private static native void listen();

    @JSBody(script = "return globalThis.dukeHeld.inside;")
    private static native boolean inside();

    @JSBody(script = "return document.hasFocus();")
    private static native boolean hasFocus();

    @JSBody(params = "button", script = "return globalThis.dukeHeld.buttons[button] === true;")
    private static native boolean button(int button);

    @JSBody(params = "key", script = "return globalThis.dukeHeld.keys[key] === true;")
    private static native boolean key(String key);

    @Override
    public float uiScale() {
        return pixelRatio();
    }

    @Override
    public GlyphRasterizer glyphs() {
        return glyphs;
    }

    @Override
    public ImageDecoder images() {
        return images;
    }

    /** The settings file as text, its bytes one character each, so any bytes come back as they went. */
    @Override
    public SettingsStore settings() {
        return new SettingsStore() {
            @Override
            public byte[] read() {
                var kept = stored(SETTINGS);
                if (kept == null) {
                    return null;
                }
                var bytes = new byte[kept.length()];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (byte) kept.charAt(i);
                }
                return bytes;
            }

            /** By hand: a browser's Java has no ISO-8859-1 charset. */
            @Override
            public void write(byte[] settings) {
                var chars = new char[settings.length];
                for (int i = 0; i < chars.length; i++) {
                    chars[i] = (char) (settings[i] & 0xFF);
                }
                store(SETTINGS, new String(chars));
            }
        };
    }

    @Override
    public TextPrompt prompt() {
        return WebPlatform::ask;
    }

    /** The page: open while it is, and filling the screen only as the browser lets a page (on a click). */
    @Override
    public Window window(Supplier<JmeContext> context) {
        return new Window() {
            @Override
            public boolean open() {
                return true;
            }

            @Override
            public boolean fullscreen() {
                return filling();
            }

            @Override
            public boolean fillTheScreen(boolean fill) {
                fill(fill);
                return true;
            }

            @Override
            public boolean resize(int w, int h, boolean fullscreen) {
                fill(fullscreen);
                return true; // the canvas follows the page; a size of its own it cannot have
            }

            @Override
            public List<int[]> modes() {
                return List.of(desktopSize());
            }

            @Override
            public int[] desktopSize() {
                return screenSize().copyToJavaArray();
            }
        };
    }

    @Override
    public HeldInput held(Supplier<JmeContext> context) {
        listen();
        return new HeldInput() {
            @Override
            public boolean pointerInside() {
                return inside();
            }

            @Override
            public boolean focused() {
                return hasFocus();
            }

            /** jME's (and GLFW's) right is 1 and middle 2; the page's are the other way round. */
            @Override
            public boolean buttonHeld(int button) {
                return button(button == 1 ? 2 : button == 2 ? 1 : button);
            }

            @Override
            public boolean keyHeld(char key) {
                return key(String.valueOf(Character.toUpperCase(key)));
            }
        };
    }

    /** No files to save a picture of the screen to: the key does nothing. */
    @Override
    public ScreenshotAppState screenshots(String folder, String pattern, Consumer<String> saved) {
        return new ScreenshotAppState(folder, ClientPlatform.checkedScreenshotName(pattern)) {
            @Override
            public void takeScreenshot() {
            }
        };
    }
}
