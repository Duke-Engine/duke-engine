package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The mouse held for a control scheme: its motion read, let go while anything wants the pointer (E90). */
class MouseCaptureTest {

    @Test
    void aHeldMouseReportsMotionAndIsLetGoWhileAnythingWantsThePointer() {
        var capture = new MouseCapture();
        capture.want(true);
        assertTrue(capture.update(false), "held");
        assertArrayEquals(new float[] {0f, 0f}, capture.motion(40f, 3f), "the first motion is none, not a jump");
        assertArrayEquals(new float[] {5f, -2f}, capture.motion(5f, -2f));

        assertFalse(capture.update(true), "a screen, a menu or Alt held: let go");
        assertArrayEquals(new float[] {0f, 0f}, capture.motion(9f, 9f), "and no motion read");
        assertTrue(capture.update(false), "held again once it closes");
        assertArrayEquals(new float[] {0f, 0f}, capture.motion(300f, 0f), "no jump from where it was left");
        assertTrue(KeyMap.standard().keysOf(KeyMap.Control.FREE_POINTER)
                .contains(KeyMap.Key.alt(com.jme3.input.KeyInput.KEY_LMENU)), "Alt held frees it");
        assertTrue(KeyMap.Control.FREE_POINTER.isHeld());
    }

}
