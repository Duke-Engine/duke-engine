package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.FastMath;
import com.jme3.math.Matrix4f;
import com.jme3.math.Plane;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.shadow.ShadowUtil;
import org.junit.jupiter.api.Test;

/** Where the sun's maps lie over the world, and why a moving camera does not make them shimmer. */
class SunCascadesTest {

    private static final int SIZE = 2048;

    private static Camera camera(float fov, float near, float far) {
        var camera = new Camera(1920, 1080);
        camera.setFrustumPerspective(fov, 1920f / 1080f, near, far);
        camera.setLocation(new Vector3f(3f, 30f, 44f));
        camera.lookAt(new Vector3f(1f, 0f, 2f), Vector3f.UNIT_Y);
        camera.update();
        return camera;
    }

    /** The light's rotation, as the shadow camera has it: from straight down the light, with no memory. */
    private static Matrix4f lightView(Vector3f direction) {
        var light = new Camera(SIZE, SIZE);
        light.setParallelProjection(true);
        light.lookAtDirection(direction, Vector3f.UNIT_Y);
        light.update();
        var view = light.getViewMatrix().clone();
        view.m20 = -view.m20;
        view.m21 = -view.m21;
        view.m22 = -view.m22;
        view.m23 = -view.m23;
        return view;
    }

    /** Where a fixed point of the world falls on the map placed for {@code camera}, in texels of that map. */
    private static Vector3f texelOf(Vector3f world, Camera camera, Matrix4f light, float near, float far) {
        var centre = new Vector3f();
        float radius = SunCascades.sphere(camera, near, far, centre);
        var at = light.mult(centre);
        float texel = 2f * radius / SIZE;
        var map = SunCascades.ortho(SunCascades.snap(at.x, texel), SunCascades.snap(at.y, texel), radius, at.z - radius, at.z + radius);
        var inLight = light.mult(world);
        var clip = map.mult(new Vector3f(inLight.x, inLight.y, -inLight.z));
        return new Vector3f((clip.x * 0.5f + 0.5f) * SIZE, (clip.y * 0.5f + 0.5f) * SIZE, clip.z);
    }

    @Test
    void aSmallCameraMoveLeavesAFixedPointOnTheSameSpotOfItsTexel() {
        var camera = camera(35f, 1f, 400f);
        var light = lightView(new Vector3f(-0.45f, -0.75f, -0.48f).normalizeLocal());
        var point = new Vector3f(2f, 0f, 5f);
        var first = texelOf(point, camera, light, 20f, 60f);
        var moved = false;
        for (int step = 1; step <= 40; step++) {
            camera.setLocation(camera.getLocation().add(0.037f, 0.011f, -0.023f));
            camera.update();
            var now = texelOf(point, camera, light, 20f, 60f);
            moved |= Math.abs(now.x - first.x) > 0.5f;
            assertEquals(0f, fromWhole(now.x - first.x), 0.02f, "x, step " + step);
            assertEquals(0f, fromWhole(now.y - first.y), 0.02f, "y, step " + step);
        }
        assertTrue(moved, "the map did follow the camera");
    }

    private static float fromWhole(float value) {
        return value - Math.round(value);
    }

    @Test
    void anUnsnappedMapWouldSlideThroughTheTexel() {
        float texel = 0.05f;
        assertEquals(0.05f, SunCascades.snap(0.0374f, texel), 1e-6f);
        assertEquals(-0.1f, SunCascades.snap(-0.09f, texel), 1e-6f);
        assertEquals(0f, fromWhole(SunCascades.snap(123.4567f, texel) / texel), 1e-3f);
    }

    @Test
    void turningOrMovingTheCameraLeavesTheMapsTexelsTheSameSizeToTheLastBit() {
        var camera = camera(70f, 0.05f, 200f);
        float radius = SunCascades.sphere(camera, 2f, 11f, new Vector3f());
        camera.setRotation(new Quaternion().fromAngleAxis(1.1f, new Vector3f(0.3f, 0.9f, 0.2f).normalizeLocal()));
        camera.setLocation(new Vector3f(-81f, 3f, 17f));
        camera.update();
        assertEquals(radius, SunCascades.sphere(camera, 2f, 11f, new Vector3f()));
    }

    @Test
    void theSphereHoldsItsSliceOfTheViewAndHardlyMore() {
        for (float[] slice : new float[][] {{1f, 5f}, {5f, 40f}, {40f, 400f}, {0.05f, 3f}}) {
            var camera = camera(60f, 0.05f, 400f);
            var centre = new Vector3f();
            float radius = SunCascades.sphere(camera, slice[0], slice[1], centre);
            var corners = new Vector3f[8];
            for (int i = 0; i < 8; i++) {
                corners[i] = new Vector3f();
            }
            ShadowUtil.updateFrustumPoints(camera, slice[0], slice[1], 1f, corners);
            float furthest = 0f;
            for (var corner : corners) {
                furthest = Math.max(furthest, corner.distance(centre));
            }
            assertEquals(radius, furthest, radius * 1e-3f, "slice " + slice[0] + ".." + slice[1]);
        }
    }

    @Test
    void aMapsDepthRunsFromNearestToFurthestAlongTheLight() {
        var map = SunCascades.ortho(0f, 0f, 10f, 20f, 60f);
        // A view matrix's z is minus the distance along the light.
        assertEquals(-1f, map.mult(new Vector3f(0f, 0f, -20f)).z, 1e-5f);
        assertEquals(1f, map.mult(new Vector3f(0f, 0f, -60f)).z, 1e-5f);
    }

    @Test
    void slicesStartAtTheNearEndAtTheFarAndEachEqualShareOfTheLog() {
        var splits = new float[5];
        SunCascades.splits(1f, 10000f, 1f, splits);
        assertEquals(1f, splits[0]);
        assertEquals(10f, splits[1], 1e-3f);
        assertEquals(100f, splits[2], 1e-2f);
        assertEquals(1000f, splits[3], 1e-1f);
        assertEquals(10000f, splits[4]);
        SunCascades.splits(10f, 50f, 0f, splits);
        assertEquals(20f, splits[1], 1e-3f);
        assertEquals(30f, splits[2], 1e-3f);
        assertEquals(40f, splits[3], 1e-3f);
    }

    @Test
    void aCameraHighOverAFieldSpendsItsSlicesOnTheFieldAndNotTheAir() {
        var camera = new Camera(1920, 1080);
        camera.setFrustumPerspective(45f, 1920f / 1080f, 1f, 1000f);
        camera.setLocation(new Vector3f(0f, 100f, 100f));
        camera.lookAt(new Vector3f(0f, 0f, 0f), Vector3f.UNIT_Y);
        camera.update();
        var field = new BoundingBox(new Vector3f(0f, -0.5f, 0f), 500f, 0.5f, 500f);
        var corners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3f();
        }
        var range = SunCascades.seenBetween(camera, field, 1f, 1000f, corners);
        // Straight ahead the ground is 141 away; the frustum's edges meet it from a little under 100 to about 300.
        assertTrue(range[0] > 80f && range[0] < 110f, "starts at " + range[0]);
        assertTrue(range[1] > 200f && range[1] < 340f, "ends at " + range[1]);
    }

    @Test
    void aCasterIsDrawnOnlyIntoTheSlicesItsShadowCanReach() {
        // A slice from z = -40 to z = -60 of a view along -z; the sun shines down and toward -z, on a ground at y = 0.
        var slice = new Plane[] {new Plane(new Vector3f(0f, 0f, -1f), 40f), new Plane(new Vector3f(0f, 0f, 1f), -60f)};
        var light = new Vector3f(0f, -1f, -1f).normalizeLocal();
        var ground = new BoundingBox(new Vector3f(0f, -0.5f, -100f), 200f, 0.5f, 200f);

        assertTrue(SunCascades.reaches(new BoundingBox(new Vector3f(0f, 2f, -50f), 1f, 1f, 1f), light, ground, slice, 0f),
                "stands in it");
        assertTrue(SunCascades.reaches(new BoundingBox(new Vector3f(0f, 5f, -35f), 1f, 1f, 1f), light, ground, slice, 0f),
                "stands short of it, its shadow falling into it");
        assertFalse(SunCascades.reaches(new BoundingBox(new Vector3f(0f, 1f, -30f), 1f, 1f, 1f), light, ground, slice, 0f),
                "stands short of it, too low for its shadow to get there");
        assertFalse(SunCascades.reaches(new BoundingBox(new Vector3f(0f, 5f, -70f), 1f, 1f, 1f), light, ground, slice, 0f),
                "stands past it, its shadow falling further away");
    }

    @Test
    void aSmallWalkLeavesTheStretchAsItWas() {
        var camera = new Camera(1920, 1080);
        camera.setFrustumPerspective(45f, 1920f / 1080f, 1f, 1000f);
        camera.lookAt(new Vector3f(0f, 0f, -1f), Vector3f.UNIT_Y);
        var room = new BoundingBox(new Vector3f(0f, 0f, -30f), 500f, 500f, 100f);
        var corners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3f();
        }
        camera.update();
        var before = SunCascades.seenBetween(camera, room, 1f, 1000f, corners);
        camera.setLocation(new Vector3f(0f, 0f, -0.3f));
        camera.update();
        var after = SunCascades.seenBetween(camera, room, 1f, 1000f, corners);
        assertEquals(before[0], after[0]);
        assertEquals(before[1], after[1]);
    }

    @Test
    void noBoxToNarrowItByLeavesTheWholeView() {
        var camera = camera(45f, 1f, 1000f);
        var corners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3f();
        }
        assertEquals(1000f, SunCascades.seenBetween(camera, null, 1f, 1000f, corners)[1]);
        var endless = new BoundingBox(Vector3f.ZERO, Float.POSITIVE_INFINITY, FastMath.PI, 1f);
        assertEquals(1f, SunCascades.seenBetween(camera, endless, 1f, 1000f, corners)[0]);
    }
}
