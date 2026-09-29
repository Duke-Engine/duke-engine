package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which sizes a monitor shows a window at, the rest refused and the window left as it was; and a new player's. */
class DisplayModesTest {

    private static final List<int[]> MODES = List.of(new int[] {800, 600}, new int[] {1024, 768},
            new int[] {1920, 1080});
    private static final int[] DESKTOP = {1920, 1080};

    @TempDir
    Path folder;

    @Test
    void aWindowOf800By600And1024By768IsShownAndOneLargerThanTheMonitorIsNot() {
        assertTrue(DisplayModes.shows(800, 600, false, MODES, DESKTOP));
        assertTrue(DisplayModes.shows(1024, 768, false, MODES, DESKTOP));
        assertTrue(DisplayModes.shows(1000, 700, false, MODES, DESKTOP), "any window that fits");
        assertFalse(DisplayModes.shows(2560, 1440, false, MODES, DESKTOP), "larger than the monitor stands");
        assertFalse(DisplayModes.shows(0, 600, false, MODES, DESKTOP));
    }

    @Test
    void fillingTheScreenAtASizeTheMonitorDoesNotShowIsRefused() {
        assertTrue(DisplayModes.shows(1024, 768, true, MODES, DESKTOP), "one of its modes");
        assertFalse(DisplayModes.shows(1000, 700, true, MODES, DESKTOP), "none of them: refused");
    }

    @Test
    void aNewPlayerOfAGameThatNamesNoSizeOpensAtTheMonitorsOwnModeFillingTheScreenWrittenDown() {
        var file = folder.resolve("settings.properties");
        DisplayModes.firstWindow(new GameSettings(file), 0, 0, new int[] {2560, 1600});

        var again = new GameSettings(file);
        assertEquals(2560, again.number("width", 0));
        assertEquals(1600, again.number("height", 0));
        assertTrue(again.flag("fullscreen", false), "filling the screen, as a game opens");
    }

    @Test
    void aSizeHeChoseAGamesOwnSizeOrNoMonitorLeavesEverythingAsItWas() {
        var chose = new GameSettings(folder.resolve("chose.properties"));
        chose.set("width", 800);
        chose.set("height", 600);
        chose.save();
        DisplayModes.firstWindow(chose, 0, 0, new int[] {2560, 1600});
        assertEquals(800, chose.number("width", 0), "what he chose wins");
        assertFalse(chose.flag("fullscreen", false));

        var named = new GameSettings(folder.resolve("named.properties"));
        DisplayModes.firstWindow(named, 800, 600, new int[] {2560, 1600});
        assertEquals(0, named.number("width", 0), "the game's own size stands for him");

        var headless = new GameSettings(folder.resolve("headless.properties"));
        DisplayModes.firstWindow(headless, 0, 0, null);
        assertEquals(0, headless.number("width", 0), "no monitor to ask: the caller's fallback");
    }

    @Test
    void aWindowedChoiceSavedWithoutASizeStaysWindowedAtTheMonitorsSize() {
        var older = new GameSettings(folder.resolve("older.properties"));
        older.set("fullscreen", false);
        older.save();
        DisplayModes.firstWindow(older, 0, 0, new int[] {2560, 1600});
        assertEquals(2560, older.number("width", 0));
        assertFalse(older.flag("fullscreen", true), "his windowed choice kept");
    }
}
