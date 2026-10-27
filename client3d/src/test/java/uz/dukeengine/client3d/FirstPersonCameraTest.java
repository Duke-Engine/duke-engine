package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The player's own eyes: a walk kept within the walls, a look kept short of straight up, a reach. */
class FirstPersonCameraTest {

    private static final FirstPerson ROOM = FirstPerson.within(-4f, -3f, 4f, 3f);

    @Test
    void aSecondForwardFacingAlongMinusZWalksThreeMetres() {
        var to = FirstPersonCamera.step(new Vector3f(0f, 1.65f, 0f), 0f, true, false, false, false, 3f, 1f, ROOM);

        assertEquals(0f, to.x, 1e-5f);
        assertEquals(-3f, to.z, 1e-5f);
    }

    @Test
    void theWallStopsIt() {
        var to = FirstPersonCamera.step(new Vector3f(3.9f, 1.65f, 0f), 0f, false, false, false, true, 3f, 1f, ROOM);

        assertEquals(4f, to.x, 1e-5f);
    }

    @Test
    void moveToPutsTheEyeAndItsLookWhereTheGameSaysAndStillClampsThePitch() {
        var camera = new FirstPersonCamera(ROOM);

        camera.moveTo(new Vector3f(-2.4f, 1.15f, 1.9f), FastMath.PI, 2f);

        assertEquals(new Vector3f(-2.4f, 1.15f, 1.9f), camera.eye());
        assertEquals(FastMath.PI, camera.yaw(), 1e-6f);
        assertEquals(89f * FastMath.DEG_TO_RAD, camera.pitch(), 1e-6f);
    }

    @Test
    void itLooksNoHigherThanEightyNineDegrees() {
        assertEquals(89f * FastMath.DEG_TO_RAD, FirstPersonCamera.clampPitch(2f), 1e-6f);
    }

    @Test
    void aBoxWithinReachIsPointedAtAndOneBeyondItIsNot() {
        var camera = new FirstPersonCamera(ROOM);
        var near = new Geometry("near", new Box(0.25f, 0.25f, 0.25f));
        near.setLocalTranslation(0f, 1.65f, -2f);
        var far = new Geometry("far", new Box(0.25f, 0.25f, 0.25f));
        far.setLocalTranslation(0f, 1.65f, -3f);
        var nearScene = new Node();
        nearScene.attachChild(near);
        var farScene = new Node();
        farScene.attachChild(far);
        nearScene.updateGeometricState();
        farScene.updateGeometricState();

        assertSame(near, camera.pointed(nearScene).orElseThrow());
        assertTrue(camera.pointed(farScene).isEmpty());
    }

    @Test
    void frozenItNeitherWalksNorLooks() {
        var camera = new FirstPersonCamera(ROOM);
        camera.setFrozen(true);

        camera.move(true, false, false, true, 40f, 30f, 1f);

        assertEquals(new Vector3f(0f, 1.65f, 0f), camera.eye());
        assertEquals(0f, camera.yaw());
        assertEquals(0f, camera.pitch());
    }

    /** A bench 1 m deep across x 1..2, from z -1 to 1. */
    private static final FirstPerson BENCHED = ROOM.blocking(List.of(new FirstPerson.Footprint(1f, -1f, 2f, 1f)));

    @Test
    void walkingStraightAtAFootprintStopsARadiusShortOfIt() {
        // Facing +x (yaw -90°) from x 0: a 3 m walk would cross the whole bench.
        var to = FirstPersonCamera.step(new Vector3f(0f, 1.65f, 0f), -FastMath.HALF_PI, true, false, false, false, 3f,
                1f, BENCHED);

        assertEquals(0.7f, to.x, 1e-5f);
        assertEquals(0f, to.z, 1e-5f);
    }

    @Test
    void aSlantingWalkSlidesAlongTheFootprint() {
        var to = FirstPersonCamera.step(new Vector3f(0.7f, 1.65f, 0f), -FastMath.QUARTER_PI, true, false, false,
                false, 1f, 1f, BENCHED);

        assertEquals(0.7f, to.x, 1e-5f);
        assertEquals(-FastMath.sqrt(0.5f), to.z, 1e-5f);
    }

    @Test
    void aGapNarrowerThanTwoRadiiIsNotWalkedThrough() {
        // Two benches 0.4 m apart along x; the gap runs along z.
        var gap = ROOM.blocking(List.of(new FirstPerson.Footprint(-1f, -1f, -0.2f, 1f),
                new FirstPerson.Footprint(0.2f, -1f, 1f, 1f)));

        var to = FirstPersonCamera.step(new Vector3f(0f, 1.65f, 2f), 0f, true, false, false, false, 3f, 1f, gap);

        assertEquals(1.3f, to.z, 1e-5f);
    }

    @Test
    void startingInsideAFootprintWalksOut() {
        var to = FirstPersonCamera.step(new Vector3f(1.5f, 1.65f, 0f), -FastMath.HALF_PI, true, false, false, false,
                1f, 1f, BENCHED);

        assertEquals(2.5f, to.x, 1e-5f);
    }

    @Test
    void footprintsSetLaterBlockTheWalkAndClearedFreeIt() {
        var camera = new FirstPersonCamera(ROOM);
        camera.moveTo(new Vector3f(0f, 1.65f, 0f), -FastMath.HALF_PI, 0f);
        camera.setBlocked(BENCHED.blocked());
        camera.move(true, false, false, false, 0f, 0f, 1f);
        assertEquals(0.7f, camera.eye().x, 1e-5f);

        camera.setBlocked(List.of());
        camera.move(true, false, false, false, 0f, 0f, 1f);
        assertEquals(3.7f, camera.eye().x, 1e-5f);
    }

    @Test
    void noFootprintsWalkAsBefore() {
        assertEquals(List.of(), ROOM.blocked());
        var to = FirstPersonCamera.step(new Vector3f(0f, 1.65f, 0f), -FastMath.HALF_PI, true, false, false, false, 3f,
                1f, ROOM);

        assertEquals(3f, to.x, 1e-5f);
    }
}
