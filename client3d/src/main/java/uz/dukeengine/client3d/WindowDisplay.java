package uz.dukeengine.client3d;

import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;

import java.util.function.Supplier;

/**
 * The window itself, asked and moved directly where jME keeps no answer: its handle, whether the pointer is in it and
 * which buttons and keys are down, filling the screen and leaving it, and the sizes the monitor really shows.
 */
final class WindowDisplay {

    private final Supplier<JmeContext> context;
    private final Supplier<AppSettings> settings;
    /** What the player set, kept in a file beside him — see {@link GameSettings}. */
    private final GameSettings preferences;
    private final MissingAssets missing;

    WindowDisplay(Supplier<JmeContext> context, Supplier<AppSettings> settings, GameSettings preferences,
            MissingAssets missing) {
        this.context = context;
        this.settings = settings;
        this.preferences = preferences;
        this.missing = missing;
    }

    /** The window's own handle, to ask it what jME does not keep; 0 where there is none. */
    long windowHandle() {
        return context.get() instanceof com.jme3.system.lwjgl.LwjglWindow display ? display.getWindowHandle() : 0L;
    }

    /** Whether the pointer is in the window at all: the window says so, where jME keeps only where it was last seen. */
    boolean pointerInTheWindow() {
        long window = windowHandle();
        return window == 0L || org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_HOVERED)
                == org.lwjgl.glfw.GLFW.GLFW_TRUE;
    }

    /** Whether a mouse button is held, as the window says: true where there is no window to ask. */
    boolean buttonHeld(int glfwButton) {
        long window = windowHandle();
        return window == 0L
                || org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, glfwButton) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /** Whether the window has the keyboard: false where there is no window. */
    boolean focused() {
        long window = windowHandle();
        return window != 0L
                && org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window, org.lwjgl.glfw.GLFW.GLFW_FOCUSED) != 0;
    }

    /** Whether the letter or digit {@code key} is down now: its GLFW code is the character itself. */
    boolean keyHeld(char key) {
        long window = windowHandle();
        return window != 0L && org.lwjgl.glfw.GLFW.glfwGetKey(window, Character.toUpperCase(key))
                == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    /**
     * One size a monitor will actually show.
     */
    record Size(int width, int height, boolean native_) {
        String shown() {
            return width + " × " + height + (native_ ? "   MONITOR" : "");
        }
    }

    /**
     * The sizes this machine can really show, largest first.
     *
     * <p>Asked of the graphics device rather than written down here, because a
     * list written down is a list that offers somebody a resolution his monitor
     * refuses — and what that looks like is a black screen and a game that has to
     * be killed. Deduplicated by shape, since a mode exists per refresh rate and
     * a player is not choosing a refresh rate.
     */
    java.util.List<Size> screenSizes() {
        var found = new java.util.LinkedHashSet<Size>();
        try {
            var device = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice();
            var current = device.getDisplayMode();
            var modes = new java.util.ArrayList<>(java.util.List.of(device.getDisplayModes()));
            modes.sort(java.util.Comparator
                    .comparingInt(java.awt.DisplayMode::getWidth)
                    .thenComparingInt(java.awt.DisplayMode::getHeight).reversed());
            for (var mode : modes) {
                if (mode.getWidth() >= 1024 && mode.getHeight() >= 576) {
                    found.add(new Size(mode.getWidth(), mode.getHeight(),
                            mode.getWidth() == current.getWidth()
                                    && mode.getHeight() == current.getHeight()));
                }
            }
        } catch (RuntimeException e) {
            missing.warn("display modes", "screen");
        }
        if (found.isEmpty()) {
            found.add(new Size(1280, 720, false));
        }
        return java.util.List.copyOf(found);
    }

    /**
     * Which of them is set, or the one nearest the monitor's own.
     */
    static int indexOfSize(java.util.List<Size> sizes, int width, int height) {
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i).width() == width && sizes.get(i).height() == height) {
                return i;
            }
        }
        for (int i = 0; i < sizes.size(); i++) {
            if (sizes.get(i).native_()) {
                return i;
            }
        }
        return 0;
    }


    /**
     * In and out of fullscreen, from anywhere, without going through the menu.
     *
     * <p>The same switch the settings menu throws, so the two cannot disagree and
     * the choice is remembered for the next launch either way. Everything that is
     * sized to the window — the hero's bar, the minimap in it, the menus — is laid
     * out again when the new size arrives.
     */
    void toggleFullscreen() {
        boolean wanted = !preferences.flag("fullscreen", false);
        if (fillTheScreen(wanted)) {
            preferences.set("fullscreen", wanted);
            preferences.save(); // F11 is not a settings screen; there is no Save to press
            settings.get().setFullscreen(wanted);
        }
    }

    /**
     * Where the window was before it filled the screen: x, y, width, height.
     */
    private int[] windowedBounds;

    /**
     * Move the window between filling the screen and sitting in it — without
     * rebuilding anything.
     *
     * <p>The obvious way is {@code restart()}, and it does not work here: it tears
     * the display context down and stands another one up, and what comes back is a
     * window that renders a frozen picture of a game that has stopped. It is also
     * more than was asked for — the pictures, the meshes and the shaders are all
     * still perfectly good, and the only thing that needs to change is which
     * monitor the window belongs to.
     *
     * <p>So it is said to the window directly. GLFW moves it to the monitor and
     * back, keeps the GL context, and reports the new size the way it reports any
     * other resize — which the HUD already follows.
     *
     * <p><b>At the size it already is</b>, which is the part that took finding
     * out. Filling the screen at the monitor's own resolution looks like the right
     * answer and half works: the world is drawn at full sharpness, and the HUD
     * disappears. Everything on it is in the scene, in the right place and
     * unculled — the engine's GUI simply does not survive its window changing size
     * underneath it, and rebuilding every piece of it does not help either. The
     * same game <em>started</em> fullscreen is perfect, because then nothing
     * changed size.
     *
     * <p>So nothing changes size here either. The window keeps the framebuffer it
     * has and only moves onto the monitor, which switches the screen to that mode
     * for as long as the game is up — the ordinary bargain of an exclusive
     * fullscreen game, and exactly what starting fullscreen already did.
     *
     * @return whether there was a window to say it to
     */
    boolean fillTheScreen(boolean fullscreen) {
        if (!(context.get() instanceof com.jme3.system.lwjgl.LwjglWindow display)) {
            return false;
        }
        long window = display.getWindowHandle();
        if (window == 0L) {
            return false;
        }
        if (fullscreen) {
            windowedBounds = boundsOf(window);
            long monitor = org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor();
            if (monitor == 0L) {
                return false;
            }
            org.lwjgl.glfw.GLFW.glfwSetWindowMonitor(window, monitor, 0, 0,
                    windowedBounds[2], windowedBounds[3],
                    org.lwjgl.glfw.GLFW.GLFW_DONT_CARE);
            return true;
        }
        var back = windowedBounds != null ? windowedBounds : startingBounds();
        org.lwjgl.glfw.GLFW.glfwSetWindowMonitor(window, 0L, back[0], back[1], back[2], back[3],
                org.lwjgl.glfw.GLFW.GLFW_DONT_CARE);
        return true;
    }

    /** The window made so, where the monitor shows it, and kept as the player's choice — see {@link Duke3D#display}. */
    void display(int width, int height, boolean fullscreen, java.util.function.Consumer<Boolean> told) {
        boolean taken = resizeTheWindow(width, height, fullscreen);
        if (taken) {
            preferences.set("width", width);
            preferences.set("height", height);
            preferences.set("fullscreen", fullscreen);
            preferences.save();
            settings.get().setResolution(width, height);
            settings.get().setFullscreen(fullscreen);
        }
        if (told != null) {
            told.accept(taken);
        }
    }

    /** The window at a new size, filling the screen or not — or left as it was where the monitor does not show it. */
    private boolean resizeTheWindow(int width, int height, boolean fullscreen) {
        if (!(context.get() instanceof com.jme3.system.lwjgl.LwjglWindow display) || display.getWindowHandle() == 0L) {
            return false;
        }
        long window = display.getWindowHandle();
        long monitor = org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0L) {
            return false;
        }
        var modes = new java.util.ArrayList<int[]>();
        var shown = org.lwjgl.glfw.GLFW.glfwGetVideoModes(monitor);
        if (shown != null) {
            for (int at = 0; at < shown.limit(); at++) {
                modes.add(new int[] {shown.get(at).width(), shown.get(at).height()});
            }
        }
        var now = org.lwjgl.glfw.GLFW.glfwGetVideoMode(monitor);
        int[] desktop = now == null ? null : new int[] {now.width(), now.height()};
        if (!DisplayModes.shows(width, height, fullscreen, modes, desktop)) {
            return false;
        }
        boolean filling = org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(window) != 0L;
        if (fullscreen) {
            if (!filling) {
                windowedBounds = boundsOf(window);
            }
            org.lwjgl.glfw.GLFW.glfwSetWindowMonitor(window, monitor, 0, 0, width, height,
                    org.lwjgl.glfw.GLFW.GLFW_DONT_CARE);
        } else {
            var at = filling && windowedBounds != null ? windowedBounds : boundsOf(window);
            org.lwjgl.glfw.GLFW.glfwSetWindowMonitor(window, 0L, at[0], at[1], width, height,
                    org.lwjgl.glfw.GLFW.GLFW_DONT_CARE);
        }
        return true;
    }

    private static int[] boundsOf(long window) {
        var x = new int[1];
        var y = new int[1];
        var width = new int[1];
        var height = new int[1];
        org.lwjgl.glfw.GLFW.glfwGetWindowPos(window, x, y);
        org.lwjgl.glfw.GLFW.glfwGetWindowSize(window, width, height);
        return new int[]{x[0], y[0], width[0], height[0]};
    }

    /**
     * Where a window that has never been anywhere else should go back to.
     */
    private int[] startingBounds() {
        return new int[]{60, 60, preferences.number("width", 1280),
                preferences.number("height", 720)};
    }

    /**
     * The chosen resolution, applied to the window as it is chosen and kept for the next launch.
     *
     * <p>It was once kept for the next launch alone, because a window that changed size took the HUD with it —
     * see {@link #fillTheScreen} — and a player whose saved size was far smaller than his monitor saw a blurred
     * picture until he restarted. Now the window takes it at once, by the same move {@link Duke3D#display} makes, and
     * everything sized to the window is laid out again when the new size arrives. A size
     * the monitor does not show leaves the window as it is.
     */
    void applyDisplaySettings() {
        int width = preferences.number("width", settings.get().getWidth());
        int height = preferences.number("height", settings.get().getHeight());
        boolean fullscreen = preferences.flag("fullscreen", settings.get().isFullscreen());
        settings.get().setResolution(width, height);
        settings.get().setFullscreen(fullscreen);
        resizeTheWindow(width, height, fullscreen);
    }
}
