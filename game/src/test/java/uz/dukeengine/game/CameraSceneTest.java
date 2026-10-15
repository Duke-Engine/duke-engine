package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;

/** A scripted scene's fade, bars, held input and a camera that keeps a moving thing in view. */
class CameraSceneTest {

    @Test
    void aFadeOutOverThirtyFramesCoversTheWorldOnTheLast() {
        var camera = new GameCamera();
        camera.fade(0x000000, 0f, 1f, 30);
        for (int frame = 0; frame < 15; frame++) {
            camera.step(id -> null);
        }
        assertEquals(0.5f, camera.screen().fade(), 1e-4f);
        for (int frame = 0; frame < 15; frame++) {
            camera.step(id -> null);
        }
        assertEquals(1f, camera.screen().fade());

        camera.letterbox(true, 0);
        camera.lockInput(true);
        assertEquals(1f, camera.screen().letterbox());
        assertTrue(camera.screen().inputLocked());
        camera.lockInput(false);
        assertFalse(camera.screen().inputLocked());
    }

    @Test
    void aFollowedThingIsKeptInViewAsItMoves() {
        var camera = new GameCamera();
        var unit = new ObjectId(4);
        float[] x = {100f};
        camera.follow(unit, 5);
        for (int frame = 0; frame < 120; frame++) {
            x[0] += 2f; // a convoy at 60 a second
            camera.step(id -> id.equals(unit) ? new Coord3D(x[0], 50f, 0f) : null);
        }
        assertEquals(x[0], camera.current().x(), 10f, "within a cell");
        assertEquals(50f, camera.current().y(), 1f);
    }
}
