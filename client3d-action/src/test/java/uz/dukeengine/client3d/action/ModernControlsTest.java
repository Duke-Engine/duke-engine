package uz.dukeengine.client3d.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Held keys to a direction told on change (E75), and a camera that follows its hero (E77). */
class ModernControlsTest {

    private static final float QUARTER = (float) (Math.PI / 2);

    @Test
    void heldKeysTurnWithTheCameraAndAreToldOnlyWhenTheyChange() {
        assertEquals(192, DirectionKeys.angleOf(true, false, false, false, 0f), "W, unturned: up the screen, -y");
        assertEquals(128, DirectionKeys.angleOf(true, false, false, false, QUARTER), "W at a camera turned 90: -x");
        assertEquals(160, DirectionKeys.angleOf(true, false, false, true, QUARTER), "W+D its diagonal");
        assertEquals(DirectionKeys.NONE, DirectionKeys.angleOf(true, false, true, false, QUARTER), "they cancel");

        var held = new DirectionKeys.Held();
        int told = 0;
        for (int frame = 0; frame < 60; frame++) {
            if (held.next(true, false, false, false, QUARTER, false) != null) {
                told++;
            }
        }
        assertEquals(1, told, "held for sixty frames: told once");
        assertEquals(Integer.valueOf(DirectionKeys.NONE), held.next(false, false, false, false, QUARTER, false),
                "let go: none");
        held.next(true, false, false, false, QUARTER, false);
        assertEquals(Integer.valueOf(192), held.next(true, false, false, false, 0f, false), "the camera turned");
        assertEquals(Integer.valueOf(DirectionKeys.NONE), held.next(true, false, false, false, 0f, true),
                "a menu has the keys: none, once");
        assertNull(held.next(true, false, false, false, 0f, true));
    }

    @Test
    void aFollowedUnitIsLookedAtAheadOfItAndNoneLeavesTheCameraBe() {
        var camera = new TestWindow();
        var eye = new CameraFollow.Eye();
        var look = CameraFollow.of(null);
        float now = 0f;
        float x = 0f;
        for (int frame = 0; frame < 30; frame++) {
            x += 1f; // thirty units in thirty frames, along +x
            now += 1f / 30f;
            eye.step(look, camera, x, 0f, 0f, true, false, now, 1f / 30f);
        }
        for (int frame = 0; frame < 60; frame++) {
            now += 1f / 30f;
            eye.step(look, camera, x, 0f, 0f, true, false, now, 1f / 30f);
        }
        assertEquals(30f + 8f, camera.lookingAt()[0], 1f, "on him and eight ahead");
        assertEquals(0f, camera.lookingAt()[1], 1e-3f);

        float before = camera.lookingAt()[0];
        eye.step(look, camera, x, 0f, 0f, true, true, now, 1f / 30f);
        eye.step(look, camera, x + 50f, 0f, 0f, true, false, now + 0.5f, 1f / 30f);
        assertEquals(before, camera.lookingAt()[0], 1e-4f, "looked away: left where he panned it");
        assertTrue(!CameraFollow.NONE.follows(), "none follows nothing");
    }
}
