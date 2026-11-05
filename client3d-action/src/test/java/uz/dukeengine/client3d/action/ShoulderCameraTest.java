package uz.dukeengine.client3d.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import org.junit.jupiter.api.Test;

/** The camera behind a hero's right shoulder: its framing, its pitch limits, how a wall pulls it in and how it eases out. */
class ShoulderCameraTest {

    private static final float DEG = (float) Math.PI / 180f;

    /** 24 back, 3.5 to the right, looking at 10 up; pitch 14 degrees to begin, -12 to 70; 0.12s to follow, 0.5s out. */
    private static final ShoulderCamera LOOK = new ShoulderCamera(24f, 3.5f, 10f, 14f * DEG, -12f * DEG, 70f * DEG,
            0.25f * DEG, 0.12f, 0.5f, 12f);

    @Test
    void thePitchStaysBetweenUnderTheGroundAndStraightOverhead() {
        assertEquals(70f * DEG, LOOK.tilted(60f * DEG, 100000f), 1e-6f, "dragged down for good: not overhead");
        assertEquals(-12f * DEG, LOOK.tilted(0f, -100000f), 1e-6f, "dragged up for good: not under the ground");
        assertEquals(14.25f * DEG, LOOK.tilted(14f * DEG, 1f), 1e-6f, "a pixel down looks a quarter degree further down");
        assertEquals(70f * DEG, LOOK.clamped(2f), 1e-6f);
        assertThrows(IllegalArgumentException.class, () -> new ShoulderCamera(24f, 3.5f, 10f, 0f, -0.2f, 1.6f,
                0.004f, 0.1f, 0.5f, 12f), "overhead is not a pitch it can have");
    }

    @Test
    void itStandsBehindAndOverTheRightShoulderLookingPastHim() {
        var hero = new Vector3f(100f, 10f, 100f);
        var level = new ShoulderCamera(24f, 3.5f, 10f, 0f, -0.2f, 1f, 0.004f, 0.1f, 0.5f, 12f);

        // Unturned the camera looks along -z (up the map), so behind him is +z and his right is +x.
        var pose = level.pose(hero, 0f, 0f);
        assertEquals(103.5f, pose.eye().x, 1e-4f, "to the right of him");
        assertEquals(10f, pose.eye().y, 1e-4f, "level with the point it looks at");
        assertEquals(124f, pose.eye().z, 1e-4f, "three and a half metres of 24 units behind");
        assertEquals(-1f, pose.gaze().z, 1e-5f);
        assertEquals(0f, pose.gaze().x, 1e-5f, "straight, so he is on the left of the picture");

        var high = LOOK.pose(hero, 0f, 14f * DEG);
        assertEquals(10f + 24f * (float) Math.sin(14f * DEG), high.eye().y, 1e-3f, "tilted down, it stands higher");
        assertEquals(1f, high.gaze().length(), 1e-5f);
        assertTrue(high.gaze().y < 0f, "looking down at him");

        var turned = level.pose(hero, (float) Math.PI / 2f, 0f); // a quarter turn left: behind him is now +x
        assertEquals(124f, turned.eye().x, 1e-3f);
        assertEquals(96.5f, turned.eye().z, 1e-3f, "and his right is -z");
        assertEquals(1f, pose.right().cross(pose.gaze()).dot(Vector3f.UNIT_Y), 1e-5f, "up is up");
    }

    @Test
    void aWallBetweenHimAndTheEyePullsItInShortOfTheWall() {
        var hero = new Vector3f(100f, 10f, 100f);
        var pose = LOOK.pose(hero, 0f, 14f * DEG);
        float full = pose.reach(hero);

        float clear = LOOK.roomFor(hero, pose, 1f, 0.7f, 0.4f, (from, way, length, min) -> length);
        assertEquals(full, clear, 1e-4f, "nothing there: all of it");

        float walled = LOOK.roomFor(hero, pose, 1f, 0.7f, 0.4f, (from, way, length, min) -> Math.min(length, 9f));
        assertEquals(9f + 1f - ShoulderCamera.CLEARANCE, walled, 1e-4f,
                "the wall at 9: the eye stands a near plane's depth past it, less the clearance, so the plane stops short");

        // A wall that only a corner of the near plane meets still counts: it is the corner that would go into it.
        float cornered = LOOK.roomFor(hero, pose, 1f, 0.7f, 0.4f, (from, way, length, min) ->
                from.distance(hero) > 0.5f && from.subtract(hero).dot(pose.right()) > 0.5f ? Math.min(length, 5f) : length);
        assertEquals(5f + 1f - ShoulderCamera.CLEARANCE, cornered, 1e-4f);

        var asked = new float[1];
        LOOK.roomFor(hero, pose, 1f, 0.7f, 0.4f, (from, way, length, min) -> {
            asked[0] = min;
            return length;
        });
        assertEquals(12f, asked[0], "only things as high as the data says are in the way");
    }

    @Test
    void theBoomComesInAtOnceAndEasesBackOut() {
        assertEquals(6f, LOOK.boomAfter(24f, 6f, 1f / 60f), "in at once: never inside the wall for a frame");
        float step = LOOK.boomAfter(6f, 24f, 1f / 60f);
        assertTrue(step > 6f && step < 6f + 18f * 0.1f, "out by a share of the way: " + step);
        float later = 6f;
        for (int frame = 0; frame < 60; frame++) {
            later = LOOK.boomAfter(later, 24f, 1f / 60f);
        }
        assertEquals(24f - 18f * (float) Math.exp(-1f / 0.5f), later, 0.05f, "a second on, the time constant's share of the way");
        assertEquals(24f, LOOK.boomAfter(23.99f, 24f, 10f), 0.01f, "given long enough it is all out");
    }

    @Test
    void theGroundStopsTheEyeWhereItWouldGoUnderIt() {
        // A line 10 long that starts 4 over the ground and sinks a unit for every 2 along.
        float run = ShoulderCamera.groundRun(10f, along -> 4f - along / 2f);
        assertEquals(8f, run, 1e-3f, "worked out between the samples, not at a sample");
        assertEquals(10f, ShoulderCamera.groundRun(10f, along -> 4f), "level ground over: all of it");
        assertEquals(0f, ShoulderCamera.groundRun(10f, along -> -1f), "starting under it: none");
    }

    @Test
    void theHeightOfWhatItLooksAtEasesAndTheFirstFrameSetsIt() {
        var boom = new ShoulderCamera.Boom();
        assertEquals(10f, boom.heightAfter(LOOK, 10f, 1f / 60f), "the first frame is where it is");
        float next = boom.heightAfter(LOOK, 16f, 1f / 60f);
        assertTrue(next > 10f && next < 16f, "a stair's step is eased, not jumped: " + next);
        boom.forget();
        assertEquals(40f, boom.heightAfter(LOOK, 40f, 1f / 60f), "after the game's camera had the screen: at once");
    }

    @Test
    void thePlacedEyeIsPulledInByAWallAndKeepsItsGaze() {
        var window = new TestWindow();
        window.yaw = 0f;
        window.pitch = 14f * DEG;
        var controls = new ActionControls().shoulder(LOOK).holdMouse(true);
        controls.attach(window);
        window.lookAt(100f, 100f);
        var eye = new Camera(1280, 720);
        eye.setFrustumPerspective(45f, 16f / 9f, 1f, 1000f);

        controls.place(eye, Vector3f.ZERO, 1f / 60f);
        var open = eye.getLocation().clone();
        var gaze = eye.getDirection().clone();
        assertEquals(0f, open.distance(new Vector3f(103.5f, 10f + 24f * (float) Math.sin(14f * DEG),
                100f + 24f * (float) Math.cos(14f * DEG))), 1e-2f, "all of it out: the eye is at its pose");

        window.wallAt = 8f;
        window.now = 1f / 60f;
        controls.place(eye, Vector3f.ZERO, 1f / 60f);
        float toEye = eye.getLocation().distance(new Vector3f(100f, 10f, 100f));
        assertEquals(8f + 1f - ShoulderCamera.CLEARANCE, toEye, 1e-2f, "stands short of the wall at once");
        assertEquals(0f, eye.getDirection().distance(gaze), 1e-4f, "and looks where it looked");

        window.wallAt = Float.MAX_VALUE;
        window.now = 2f / 60f;
        controls.place(eye, Vector3f.ZERO, 1f / 60f);
        float out = eye.getLocation().distance(new Vector3f(100f, 10f, 100f));
        assertTrue(out > toEye && out < open.distance(new Vector3f(100f, 10f, 100f)), "eased out, not snapped: " + out);
    }

    @Test
    void theEyeStaysOverTheGroundWhenItWouldGoUnderIt() {
        var window = new TestWindow();
        window.pitch = -12f * DEG; // looking up at him from low down
        window.hill = 30f; // a hill behind him, over the eye's height, five units back
        var controls = new ActionControls().shoulder(LOOK);
        controls.attach(window);
        window.lookAt(100f, 100f);
        var eye = new Camera(1280, 720);
        eye.setFrustumPerspective(45f, 16f / 9f, 1f, 1000f);

        controls.place(eye, Vector3f.ZERO, 1f / 60f);

        float out = eye.getLocation().distance(new Vector3f(100f, 10f, 100f));
        assertTrue(out > 3f && out < 7f, "stopped short of the hill: " + out);
    }

    @Test
    void theCameraTakesTheShakeOfWhatLanded() {
        var window = new TestWindow();
        var controls = new ActionControls().shoulder(LOOK);
        controls.attach(window);
        window.lookAt(100f, 100f);
        var eye = new Camera(1280, 720);
        eye.setFrustumPerspective(45f, 16f / 9f, 1f, 1000f);
        controls.place(eye, Vector3f.ZERO, 1f / 60f);
        var still = eye.getLocation().clone();

        window.now = 1f / 60f;
        controls.place(eye, new Vector3f(0f, 0.5f, 0f), 1f / 60f);

        assertEquals(still.y + 0.5f, eye.getLocation().y, 1e-4f);
    }

    @Test
    void anOpenScreenFreezesTheCameraAndClosingItTurnsNoFurtherThanTheMouseDid() {
        var window = new TestWindow();
        var controls = new ActionControls().shoulder(LOOK).holdMouse(true);
        controls.attach(window);
        window.pitch = 30f * DEG;
        controls.frame(1f / 60f);
        window.moved = new float[] {40f, 20f};
        controls.frame(1f / 60f);
        float yaw = window.yaw;
        float pitch = window.pitch;
        assertTrue(yaw < 0f && pitch > 14f * DEG, "the held mouse turns and tilts it");

        window.held = false; // a screen, a menu or Alt has the mouse
        window.moved = new float[] {500f, -500f};
        controls.frame(1f / 60f);
        controls.frame(1f / 60f);
        assertEquals(yaw, window.yaw, 0f, "frozen: the mouse moves nothing");
        assertEquals(pitch, window.pitch, 0f);

        window.held = true; // closed, the client holds the mouse again and its first motion is none
        window.moved = new float[] {0f, 0f};
        controls.frame(1f / 60f);
        assertEquals(yaw, window.yaw, 0f, "recaptured with no jump");
        assertEquals(pitch, window.pitch, 0f);
    }

    @Test
    void theFirstFrameTakesTheLooksPitchAndTheMouseKeepsItWithinItsLimits() {
        var window = new TestWindow();
        window.pitch = 0.9f;
        var controls = new ActionControls().shoulder(LOOK).holdMouse(true);
        controls.attach(window);

        controls.frame(1f / 60f);
        assertEquals(14f * DEG, window.pitch, 1e-6f, "from the data, not the client's own slope");

        window.moved = new float[] {0f, 100000f};
        controls.frame(1f / 60f);
        assertEquals(70f * DEG, window.pitch, 1e-6f);
        window.moved = new float[] {0f, -200000f};
        controls.frame(1f / 60f);
        assertEquals(-12f * DEG, window.pitch, 1e-6f);
    }

    /** After a scene the game directed, the camera comes back looking the way he faces: behind him, whichever way that is. */
    @Test
    void theCameraTurnsBehindAHeroWhoFacesEastOrSouthTheShortWay() {
        assertEquals((float) -Math.PI / 2f, ActionControls.turnBehind(0f, 0f), 1e-5f, "east: the gaze (1, 0)");
        assertEquals((float) Math.PI, Math.abs(ActionControls.turnBehind((float) Math.PI / 2f, 0f)), 1e-5f,
                "south, from the camera looking north: half a turn");
        assertEquals(0f, ActionControls.turnBehind((float) -Math.PI / 2f, 0f), 1e-5f, "north: already behind him");
        assertEquals(0f, ActionControls.turnBehind(Float.NaN, 1f), "no facing known: no turn");
        assertEquals(0.2f, ActionControls.turnBehind((float) -Math.PI / 2f, -0.2f), 1e-5f, "the short way round");
    }
}
