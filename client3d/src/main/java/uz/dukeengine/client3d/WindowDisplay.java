package uz.dukeengine.client3d;

import com.jme3.system.AppSettings;

import java.util.function.Supplier;

import uz.dukeengine.client3d.platform.Window;

/**
 * The window itself, asked and moved directly where jME keeps no answer: filling the screen and leaving it, and the
 * sizes the monitor really shows. The window is the platform's — see {@link Window}.
 */
final class WindowDisplay {

    private final Window window;
    private final Supplier<AppSettings> settings;
    /** What the player set, kept in a file beside him — see {@link GameSettings}. */
    private final GameSettings preferences;
    private final MissingAssets missing;

    WindowDisplay(Window window, Supplier<AppSettings> settings, GameSettings preferences, MissingAssets missing) {
        this.window = window;
        this.settings = settings;
        this.preferences = preferences;
        this.missing = missing;
    }

    /** Whether the window fills the screen: the window's own monitor, or the settings' word where there is no window. */
    boolean fullscreen() {
        if (window.open()) {
            return window.fullscreen();
        }
        var set = settings.get();
        return set != null && set.isFullscreen();
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
     * <p>Asked of the window's monitor rather than written down here, because a
     * list written down is a list that offers somebody a resolution his monitor
     * refuses — and what that looks like is a black screen and a game that has to
     * be killed. Deduplicated by shape, since a mode exists per refresh rate and
     * a player is not choosing a refresh rate.
     */
    java.util.List<Size> screenSizes() {
        try {
            return sizesOf(window.modes(), window.desktopSize());
        } catch (RuntimeException _) {
            missing.warn("display modes", "screen");
            return sizesOf(java.util.List.of(), null);
        }
    }

    /** The modes ({width, height}) as sizes: none below 1024 by 576, largest first, one of a shape, the desktop's marked. */
    static java.util.List<Size> sizesOf(java.util.List<int[]> modes, int[] desktop) {
        var found = new java.util.LinkedHashSet<Size>();
        var sorted = new java.util.ArrayList<>(modes);
        sorted.sort(java.util.Comparator.<int[]>comparingInt(mode -> mode[0]).thenComparingInt(mode -> mode[1])
                .reversed());
        for (var mode : sorted) {
            if (mode[0] >= 1024 && mode[1] >= 576) {
                found.add(new Size(mode[0], mode[1],
                        desktop != null && mode[0] == desktop[0] && mode[1] == desktop[1]));
            }
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
     * <p>So it is said to the window directly. The window moves to the monitor and
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
        return window.fillTheScreen(fullscreen);
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
        return window.open() && DisplayModes.shows(width, height, fullscreen, window.modes(), window.desktopSize())
                && window.resize(width, height, fullscreen);
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
