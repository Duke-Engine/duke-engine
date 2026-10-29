package uz.dukeengine.client3d;

import java.util.List;

import uz.dukeengine.client3d.platform.ClientPlatform;

/**
 * Whether a monitor shows a window of a size — the reference's {@code W3DDisplay::setDisplayMode}, where a mode the
 * card refuses leaves the old one: filling the screen, only a size among the monitor's own modes; in a window, one no
 * larger than the monitor stands now. And the window a player who has never chosen one starts with.
 */
final class DisplayModes {

    private DisplayModes() {
    }

    /**
     * Whether {@code width} by {@code height}, filling the screen or not, is shown on a monitor whose modes are {@code
     * modes} — each {width, height} — standing now at {@code desktop}.
     */
    static boolean shows(int width, int height, boolean fullscreen, List<int[]> modes, int[] desktop) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (!fullscreen) {
            return desktop == null || width <= desktop[0] && height <= desktop[1];
        }
        for (var mode : modes) {
            if (mode[0] == width && mode[1] == height) {
                return true;
            }
        }
        return false;
    }

    /** The size the primary monitor stands at now, {width, height}, or null where there is no monitor to ask. */
    static int[] desktop() {
        return ClientPlatform.get().window(() -> null).desktopSize(); // before any window: the machine's own answer
    }

    /**
     * A player who has never chosen a window, of a game that names none, gets the monitor's own mode filling the
     * screen — written down as his choice, so the settings screen and the fullscreen key start from what he sees. A
     * size he chose, a game's own size, or no monitor to ask leaves everything as it was.
     */
    static void firstWindow(GameSettings chosen, int gameWidth, int gameHeight, int[] desktop) {
        if (chosen.number("width", 0) > 0 || gameWidth > 0 && gameHeight > 0 || desktop == null) {
            return;
        }
        chosen.set("width", desktop[0]);
        chosen.set("height", desktop[1]);
        chosen.set("fullscreen", chosen.flag("fullscreen", true));
        chosen.save();
    }
}
