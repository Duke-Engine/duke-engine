package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The player's mouse on the camera, worked out with no window. */
class SteeringTest {

    @Test
    void aMiddleDragTurnsTheViewByItsAnglePerPixelAndIsNoClick() {
        var steering = new Steering();
        steering.middleDown(400f, 300f, 10f);

        assertEquals(0.5f, steering.turn(450f, 0.01f), 1e-6f, "fifty pixels at the reference's 0.01");
        assertEquals(-0.2f, steering.turn(430f, 0.01f), 1e-6f, "and back twenty from there");

        assertFalse(steering.middleUp(430f, 300f, 10.5f), "a drag, not a click");
        assertEquals(0f, steering.turn(500f, 0.01f), "let go, nothing turns");
    }

    @Test
    void aMiddleClickHardlyMovesAndIsSoonLetGo() {
        var steering = new Steering();
        steering.middleDown(400f, 300f, 10f);
        assertTrue(steering.middleUp(403f, 305f, 10.1f), "five pixels, a tenth of a second: a click");

        steering.middleDown(400f, 300f, 10f);
        assertFalse(steering.middleUp(406f, 300f, 10.1f), "six pixels is a drag");

        steering.middleDown(400f, 300f, 10f);
        assertFalse(steering.middleUp(400f, 300f, 10.2f), "held past five frames at thirty a second is not a click");

        assertFalse(steering.middleUp(400f, 300f, 10.2f), "and nothing let go that never went down");
    }
}
