package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Where a click lands, on a map with more than one storey to it.
 *
 * <p>This is arithmetic dressed as a rendering problem, and it goes wrong in a
 * way that looks like nothing at all: the order marker appears a pace beyond the
 * cursor, the hero walks there, and everything about it is consistent — the
 * marker really is where the game thinks the click was.
 *
 * <p>No camera and no window: a ray is a point and a direction, and how high the
 * floor is at a place is a function this hands in.
 */
class GroundPickTest {

    private static final float STOREY = 10f;

    /**
     * A camera above and behind, looking down the way this client's does — about
     * 55 degrees, so a ray drops roughly one unit for every unit it travels north.
     */
    private static final Vector3f DOWNWARD = new Vector3f(0f, -0.8f, -0.6f).normalizeLocal();

    /** Everything west of x = 100 is the ground floor; east of it stands a storey up. */
    private static float terraced(float x, float z) {
        return x >= 100f ? STOREY : 0f;
    }

    private static Vector3f pick(Vector3f from) {
        return DukeRtsApp.groundHit(from, DOWNWARD, STOREY, 1, GroundPickTest::terraced);
    }

    /** On a hill the click lands on the hill, where the ground under the cursor is — not on the level plane under it. */
    @Test
    void aClickOnASlopeLandsOnTheSlope() {
        var hit = DukeRtsApp.groundHit(new Vector3f(50f, 40f, 100f), DOWNWARD, STOREY, 0, (x, z) -> x * 0.08f);

        assertEquals(hit.x * 0.08f, hit.y, 0.02f, "standing on the ground at " + hit);
    }

    /** On flat ground the answer is the plain one: where the ray meets zero. */
    @Test
    void aClickOnTheGroundFloorLandsWhereTheRayMeetsIt() {
        var hit = pick(new Vector3f(50f, 40f, 100f));

        assertEquals(0f, hit.y, 0.001f, "it should be standing on the ground floor");
        assertTrue(hit.x < 100f, "and west of the step up: " + hit.x);
    }

    /**
     * A click on the raised floor lands on the raised floor — not on the ground
     * behind it.
     *
     * <p>The ray passes over the raised room and, carried on to the plane at zero,
     * comes down somewhere past it. That point's floor is at zero too, so the old
     * answer looked settled: the marker sat a pace beyond the cursor, every time,
     * and only upstairs.
     */
    @Test
    void aClickUpstairsLandsUpstairsRatherThanBehindIt() {
        var from = new Vector3f(150f, 40f, 100f);

        var hit = pick(from);
        var throughTheFloor = from.add(DOWNWARD.mult(-from.y / DOWNWARD.y));

        assertEquals(STOREY, hit.y, 0.001f, "it is the upper floor being pointed at");
        assertEquals(STOREY, terraced(hit.x, hit.z), 0.001f,
                "and the point really is standing on it");
        assertTrue(hit.z > throughTheFloor.z + 1f,
                "the old answer carried on under the room and landed " + throughTheFloor.z
                        + " where the true one is " + hit.z);
    }

    /** A stair is between two storeys, and a click on one lands on the slope. */
    @Test
    void aClickOnAStairLandsOnTheSlope() {
        var hit = DukeRtsApp.groundHit(new Vector3f(150f, 40f, 100f), DOWNWARD, STOREY, 1,
                (x, z) -> 4f); // a ramp, halfway up

        assertEquals(4f, hit.y, 0.001f, "on the step it is actually standing on");
    }

    /** A game with no height in it is answered exactly as it always was. */
    @Test
    void aFlatMapIsPickedAtZero() {
        var hit = DukeRtsApp.groundHit(new Vector3f(150f, 40f, 100f), DOWNWARD, 0f, 0,
                (x, z) -> 0f);

        assertEquals(0f, hit.y, 0.001f);
    }

    /**
     * A ray that never meets the ground is followed a long way instead of being
     * reported as nothing — the projection clamps it to the map, which is what
     * the player sees anyway.
     */
    @Test
    void aRayIntoTheSkyStillAnswers() {
        var hit = DukeRtsApp.groundHit(new Vector3f(50f, 40f, 100f),
                new Vector3f(0f, 0.8f, -0.6f).normalizeLocal(), STOREY, 1, (x, z) -> 0f);

        assertTrue(hit.length() > 1000f, "it should run off toward the edge of the world");
    }
}
