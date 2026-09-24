package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.junit.jupiter.api.Test;

/** The world drawn in part of the window: the top 80%, with the bar under it, as the reference draws it. */
class WorldRegionTest {

    /** An 800 by 600 window's camera, as the client makes it, looking down at the middle of the world. */
    private static Camera camera() {
        var camera = new Camera(800, 600);
        camera.setFrustumPerspective(45f, 800f / 600f, 1f, 1000f);
        camera.setLocation(new Vector3f(0f, 140f, 100f));
        camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
        camera.update();
        return camera;
    }

    /** Where the thing the camera looks at is on the screen, counted down from the top. */
    private static float[] middleOnScreen(Camera camera) {
        var at = camera.getScreenCoordinates(Vector3f.ZERO);
        return new float[] {at.x, camera.getHeight() - at.y};
    }

    @Test
    void inTheTop80PercentTheMiddleOfTheWorldIsAt400By240() {
        var camera = camera();
        assertEquals(300f, middleOnScreen(camera)[1], 0.01f, "the whole window first: its middle");

        new WorldRegion(0f, 0f, 1f, 0.8f).applyTo(camera);
        camera.update();

        var middle = middleOnScreen(camera);
        assertEquals(400f, middle[0], 0.01f);
        assertEquals(240f, middle[1], 0.01f, "the middle of the world's part, not of the window");
        assertEquals(800f / 480f, (camera.getFrustumRight() - camera.getFrustumLeft())
                / (camera.getFrustumTop() - camera.getFrustumBottom()), 1e-4f, "its shape the region's");
    }

    @Test
    void aClickUnderTheWorldsPartSelectsNothingInIt() {
        var region = new WorldRegion(0f, 0f, 1f, 0.8f);

        // The input manager counts up from the bottom: 550 from the top is 50 up.
        assertFalse(region.contains(400f, 50f, 800, 600), "y = 550 is under the world, on the bar");
        assertTrue(region.contains(400f, 300f, 800, 600), "y = 300 is on it");
    }

    @Test
    void theWholeWindowAgainIsTheOldProjection() {
        var camera = camera();
        float fovTop = camera.getFrustumTop();
        new WorldRegion(0f, 0f, 1f, 0.8f).applyTo(camera);
        camera.update();

        WorldRegion.WHOLE.applyTo(camera);
        camera.update();

        assertEquals(300f, middleOnScreen(camera)[1], 0.01f);
        assertEquals(fovTop, camera.getFrustumTop(), 1e-5f, "the vertical angle kept throughout");
        assertEquals(new Vector3f(0f, 140f, 100f), camera.getLocation(), "and the camera never moved");
    }
}
