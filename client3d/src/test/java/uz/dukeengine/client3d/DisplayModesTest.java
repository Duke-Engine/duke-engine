package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Which sizes a monitor shows a window at, the rest refused and the window left as it was. */
class DisplayModesTest {

    private static final List<int[]> MODES = List.of(new int[] {800, 600}, new int[] {1024, 768},
            new int[] {1920, 1080});
    private static final int[] DESKTOP = {1920, 1080};

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
}
