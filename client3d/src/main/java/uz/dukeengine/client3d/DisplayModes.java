package uz.dukeengine.client3d;

import java.util.List;

/**
 * Whether a monitor shows a window of a size — the reference's {@code W3DDisplay::setDisplayMode}, where a mode the
 * card refuses leaves the old one: filling the screen, only a size among the monitor's own modes; in a window, one no
 * larger than the monitor stands now.
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
}
