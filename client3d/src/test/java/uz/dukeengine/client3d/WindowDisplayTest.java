package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.system.AppSettings;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.platform.Window;
import uz.dukeengine.client3d.desktop.FileSettingsStore;

/** The sizes a window offers and what it says of filling the screen, whichever platform's window it is. */
class WindowDisplayTest {

    private static final int[] DESKTOP = {1920, 1080};

    /** A window with the modes it is given, and no way to move. */
    private record Fake(boolean open, boolean filling, List<int[]> modes) implements Window {
        @Override
        public boolean fullscreen() {
            return filling;
        }

        @Override
        public boolean fillTheScreen(boolean fill) {
            return false;
        }

        @Override
        public boolean resize(int w, int h, boolean fullscreen) {
            return false;
        }

        @Override
        public int[] desktopSize() {
            return DESKTOP;
        }
    }

    private static WindowDisplay display(Window window, boolean settingsFullscreen) {
        var settings = new AppSettings(true);
        settings.setFullscreen(settingsFullscreen);
        return new WindowDisplay(window, () -> settings, new GameSettings(new FileSettingsStore(Path.of("no-such-dir", "unused.properties"))),
                new MissingAssets());
    }

    @Test
    void sizesAreLargestFirstOneOfAShapeNoneBelowTheFloorAndTheDesktopsMarked() {
        var sizes = WindowDisplay.sizesOf(List.of(new int[] {800, 600}, new int[] {1024, 768}, new int[] {1920, 1080},
                new int[] {1280, 720}, new int[] {1920, 1080}, new int[] {1024, 500}), DESKTOP);
        assertEquals(List.of(new WindowDisplay.Size(1920, 1080, true), new WindowDisplay.Size(1280, 720, false),
                new WindowDisplay.Size(1024, 768, false)), sizes);
    }

    @Test
    void aMonitorThatOffersNothingLargeEnoughGetsTheDefault() {
        assertEquals(List.of(new WindowDisplay.Size(1280, 720, false)),
                WindowDisplay.sizesOf(List.of(new int[] {640, 480}), null));
    }

    @Test
    void theSetSizeIsFoundOrElseTheMonitorsOwn() {
        var sizes = WindowDisplay.sizesOf(List.of(new int[] {1920, 1080}, new int[] {1280, 720}), new int[] {1280, 720});
        assertEquals(1, WindowDisplay.indexOfSize(sizes, 1280, 720));
        assertEquals(1, WindowDisplay.indexOfSize(sizes, 999, 999), "none set: the monitor's own");
        assertEquals(0, WindowDisplay.indexOfSize(WindowDisplay.sizesOf(List.of(), null), 5, 5));
    }

    @Test
    void screenSizesAskTheWindow() {
        var shown = display(new Fake(true, false, List.of(new int[] {1920, 1080}, new int[] {1366, 768})), false)
                .screenSizes();
        assertEquals(List.of(new WindowDisplay.Size(1920, 1080, true), new WindowDisplay.Size(1366, 768, false)), shown);
    }

    @Test
    void fullscreenIsTheWindowsWordWhereThereIsAWindowAndTheSettingsWhereThereIsNone() {
        assertTrue(display(new Fake(true, true, List.of()), false).fullscreen());
        assertFalse(display(new Fake(true, false, List.of()), true).fullscreen());
        assertTrue(display(new Fake(false, false, List.of()), true).fullscreen());
        assertFalse(display(new Fake(false, false, List.of()), false).fullscreen());
    }
}
