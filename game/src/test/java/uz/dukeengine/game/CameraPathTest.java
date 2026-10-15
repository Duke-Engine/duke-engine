package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** A camera flown through three keys: through the middle one at its frame, and still between none (E85). */
class CameraPathTest {

    @Test
    void itPassesTheMiddleKeyAtItsFrameAndNeverStandsStillBetween() {
        var camera = new GameCamera();
        camera.along(List.of(new GameCamera.Key(0f, 0f, 0f, 0.8f, 300f), new GameCamera.Key(100f, 50f, 1f, 0.6f, 200f),
                new GameCamera.Key(200f, 0f, 2f, 0.5f, 150f)), 60, GameCamera.Ease.LINEAR);
        float lastX = camera.current().x();
        for (int frame = 1; frame <= 60; frame++) {
            camera.step();
            var now = camera.current();
            assertTrue(now.x() > lastX, "moving at frame " + frame);
            lastX = now.x();
            if (frame == 30) {
                assertEquals(100f, now.x(), 1e-3f, "through the middle key's point at its frame");
                assertEquals(50f, now.y(), 1e-3f);
                assertEquals(200f, now.zoom(), 1e-3f);
            }
        }
        assertEquals(200f, camera.current().x(), 1e-3f, "at the last key");
        assertTrue(!camera.isMoving());
    }
}
