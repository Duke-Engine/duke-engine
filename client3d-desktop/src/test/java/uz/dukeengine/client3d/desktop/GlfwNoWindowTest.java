package uz.dukeengine.client3d.desktop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.client3d.platform.ClientPlatform;

/** With no window, the desktop answers as the headless tests have always had it. */
class GlfwNoWindowTest {

    @Test
    void noWindowIsNotOpenNorFullscreenAndCannotBeMoved() {
        var window = ClientPlatform.get().window(() -> null);
        assertFalse(window.open());
        assertFalse(window.fullscreen());
        assertFalse(window.fillTheScreen(true));
        assertFalse(window.resize(800, 600, false));
    }

    @Test
    void noWindowHasAButtonHeldThePointerInsideAndNoFocus() {
        var held = ClientPlatform.get().held(() -> null);
        assertTrue(held.buttonHeld(0));
        assertTrue(held.pointerInside());
        assertFalse(held.focused());
        assertFalse(held.keyHeld('w'));
    }
}
