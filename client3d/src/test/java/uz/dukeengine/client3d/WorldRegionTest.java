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

    /** Where a point 25 degrees across from the line of sight falls, counted from the window's left and top. */
    private static float[] twentyFiveAcross(WorldRegion region) {
        var camera = new Camera(800, 600);
        camera.setFrustumPerspective(45f, 800f / 600f, 1f, 1000f);
        camera.setLocation(Vector3f.ZERO);
        camera.lookAtDirection(new Vector3f(0f, 0f, -1f), Vector3f.UNIT_Y);
        region.applyTo(camera, 50f);
        camera.update();
        var at = camera.getScreenCoordinates(new Vector3f(100f * (float) Math.tan(Math.toRadians(25)), 0f, -100f));
        return new float[] {at.x, camera.getHeight() - at.y};
    }

    @Test
    void aFiftyDegreeFieldPutsAPoint25DegreesAcrossOnTheWindowsEdgeWholeOrInItsTop80Percent() {
        var whole = twentyFiveAcross(WorldRegion.WHOLE);
        assertEquals(800f, whole[0], 0.01f, "on the right edge of the whole window");
        assertEquals(300f, whole[1], 0.01f);

        var top = twentyFiveAcross(new WorldRegion(0f, 0f, 1f, 0.8f));
        assertEquals(800f, top[0], 0.01f, "and of its top 80%: the height follows, the width is kept");
        assertEquals(240f, top[1], 0.01f);
    }
}
