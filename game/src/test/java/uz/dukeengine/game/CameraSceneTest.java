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

    @Test
    void aSceneSavedHalfwayGoesOnAsItWouldHave() {
        var played = new GameCamera();
        var unit = new ObjectId(4);
        played.along(java.util.List.of(new GameCamera.Key(0f, 0f, 0f, 0.6f, 300f),
                new GameCamera.Key(200f, 50f, 1f, 0.8f, 200f), new GameCamera.Key(400f, 0f, 2f, 0.7f, 250f)), 90,
                GameCamera.Ease.IN_OUT);
        played.fade(0x102030, 0f, 1f, 60);
        played.letterbox(true, 40);
        for (int frame = 0; frame < 30; frame++) {
            played.step(id -> null);
        }
        var out = new uz.dukeengine.core.save.SaveOut();
        played.save(out);
        var loaded = new GameCamera();
        var in = new uz.dukeengine.core.save.SaveIn(out.text(), null, "camera");
        loaded.load(in);
        in.expectEnd();
        for (int frame = 0; frame < 120; frame++) {
            if (frame == 70) {
                played.follow(unit, 5);
                loaded.follow(unit, 5);
            }
            float x = 100f + frame;
            played.step(id -> new Coord3D(x, 50f, 0f));
            loaded.step(id -> new Coord3D(x, 50f, 0f));
            assertEquals(played.current(), loaded.current(), "frame " + frame);
            assertEquals(played.screen(), loaded.screen(), "frame " + frame);
            assertEquals(played.isDriven(), loaded.isDriven());
        }
    }
}
