package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.jme3.input.event.MouseMotionEvent;
import org.junit.jupiter.api.Test;

/** The held mouse's motion: summed from jME's events, with the turning's own direction of {@code dy}. */
class HeldMotionTest {

    @Test
    void theEventsOfAFrameAreSummedAndDyIsCountedDownTheScreen() {
        var motion = new HeldMotion();
        motion.onMouseMotionEvent(new MouseMotionEvent(10, 10, 3, 4, 0, 0)); // right, and up in jME's numbers
        motion.onMouseMotionEvent(new MouseMotionEvent(12, 7, 2, -7, 0, 0)); // right, and down
        assertArrayEquals(new float[] {5f, 3f}, motion.take());
    }

    @Test
    void takingStartsTheSumAgain() {
        var motion = new HeldMotion();
        motion.onMouseMotionEvent(new MouseMotionEvent(1, 1, 1, 1, 0, 0));
        motion.take();
        assertArrayEquals(new float[] {0f, 0f}, motion.take());
    }
}
