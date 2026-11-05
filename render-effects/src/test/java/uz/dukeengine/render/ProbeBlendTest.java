package uz.dukeengine.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

/** The box projection and the blend of light probes (the shader's arithmetic, in Java): where a reflection lands and who lights a pixel. */
class ProbeBlendTest {

    private static final Quaternion STRAIGHT = Quaternion.IDENTITY;
    private static final Vector3f MIDDLE = new Vector3f(10f, 0f, 0f);
    private static final Vector3f HALF = new Vector3f(4f, 2f, 3f);

    private static Vector3f aimed(Vector3f point, Vector3f direction) {
        return ProbeBlend.parallax(STRAIGHT, MIDDLE, HALF, point, direction);
    }

    private static void assertVector(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1e-4f, "x of " + actual);
        assertEquals(expected.y, actual.y, 1e-4f, "y of " + actual);
        assertEquals(expected.z, actual.z, 1e-4f, "z of " + actual);
    }

    @Test
    void fromTheMiddleAReflectionLandsWhereItPointsAndAtTheWall() {
        assertVector(new Vector3f(0f, 2f, 0f), aimed(MIDDLE, Vector3f.UNIT_Y));
        assertVector(new Vector3f(4f, 0f, 0f), aimed(MIDDLE, Vector3f.UNIT_X));
        assertVector(new Vector3f(0f, 0f, -3f), aimed(MIDDLE, new Vector3f(0f, 0f, -0.1f)));
    }

    @Test
    void offCentreTheSameDirectionLandsOnTheWallItReallyMeets() {
        // 2 right of the middle, straight up: the ceiling 2 above, at 2 right of the middle, not straight above the centre.
        assertVector(new Vector3f(2f, 2f, 0f), aimed(MIDDLE.add(2f, 0f, 0f), Vector3f.UNIT_Y));
        // At the floor, aiming at the far wall: the wall's own place.
        assertVector(new Vector3f(4f, 2f, 0f), aimed(MIDDLE.add(0f, -2f, 0f), new Vector3f(1f, 1f, 0f)));
    }

    @Test
    void theLengthOfTheDirectionDoesNotMatter() {
        assertVector(aimed(MIDDLE.add(1f, 1f, 1f), new Vector3f(0.3f, 0.5f, -0.2f)), aimed(MIDDLE.add(1f, 1f, 1f), new Vector3f(3f, 5f, -2f)));
    }

    @Test
    void aRayAlongAFaceDoesNotDivideByZero() {
        var landing = aimed(MIDDLE.add(0f, -2f, 0f), Vector3f.UNIT_X);
        assertTrue(Float.isFinite(landing.x + landing.y + landing.z), landing.toString());
        assertEquals(4f, landing.x, 1e-3f);
    }

    @Test
    void aTurnedBoxProjectsOntoItsOwnWalls() {
        // Turned a quarter about the up axis, the box's 4 along its x is along the world's -z (or z), its 3 along z is along x.
        var turned = new Quaternion().fromAngleAxis(FastMath.HALF_PI, Vector3f.UNIT_Y);
        var hit = ProbeBlend.parallax(turned, MIDDLE, HALF, MIDDLE, Vector3f.UNIT_X);
        assertEquals(3f, Math.abs(hit.x), 1e-4f);
        assertEquals(0f, hit.z, 1e-4f);
        hit = ProbeBlend.parallax(turned, MIDDLE, HALF, MIDDLE, Vector3f.UNIT_Z);
        assertEquals(4f, Math.abs(hit.z), 1e-4f);
        assertEquals(1.5f, ProbeBlend.depth(turned, MIDDLE, HALF, MIDDLE.add(0f, 0f, 6f)), 1e-4f);
    }

    @Test
    void aPointBeyondTheBoxIsTakenAtItsNearestPoint() {
        // 3 past the +x wall, looking along +x: the wall is where it is, the ray leaves the box at once.
        assertVector(new Vector3f(4f, 0f, 0f), aimed(MIDDLE.add(7f, 0f, 0f), Vector3f.UNIT_X));
        assertVector(aimed(MIDDLE.add(4f, 0f, 0f), Vector3f.UNIT_Y), aimed(MIDDLE.add(7f, 0f, 0f), Vector3f.UNIT_Y));
    }

    @Test
    void depthIsZeroAtTheMiddleOneOnTheFacesAndMoreBeyond() {
        assertEquals(0f, ProbeBlend.depth(STRAIGHT, MIDDLE, HALF, MIDDLE), 0f);
        assertEquals(1f, ProbeBlend.depth(STRAIGHT, MIDDLE, HALF, MIDDLE.add(4f, 0f, 0f)), 1e-6f);
        assertEquals(1f, ProbeBlend.depth(STRAIGHT, MIDDLE, HALF, MIDDLE.add(0f, -2f, 1f)), 1e-6f);
        assertEquals(1.5f, ProbeBlend.depth(STRAIGHT, MIDDLE, HALF, MIDDLE.add(0f, 0f, 4.5f)), 1e-6f);
        // A sphere probe's area is its radius: its depth there is where the influence ends.
        assertEquals(0.5f * (1f + ProbeBlend.FEATHER), ProbeBlend.depth(MIDDLE, 8f, MIDDLE.add(0f, 4f, 0f)), 1e-6f);
        assertEquals(1f + ProbeBlend.FEATHER, ProbeBlend.depth(MIDDLE, 8f, MIDDLE.add(8f, 0f, 0f)), 1e-6f);
    }

    @Test
    void aProbeLightsWholeWithinItsBoundsAndFadesOverTheFeather() {
        assertEquals(1f, ProbeBlend.influence(0f), 0f);
        // The floor and the walls stand on the faces: they are the probe's.
        assertEquals(1f, ProbeBlend.influence(1f), 0f);
        assertEquals(0.5f, ProbeBlend.influence(1f + ProbeBlend.FEATHER / 2f), 1e-6f);
        assertEquals(0f, ProbeBlend.influence(1f + ProbeBlend.FEATHER), 0f);
        assertEquals(0f, ProbeBlend.influence(3f), 0f);
        float last = 1f;
        for (float depth = 1f; depth <= 1.2f; depth += 0.01f) {
            assertTrue(ProbeBlend.influence(depth) <= last, "falls with depth at " + depth);
            last = ProbeBlend.influence(depth);
        }
    }

    @Test
    void aRoomInASkyIsTheRoomWithinAndTheSkyWithout() {
        assertArrayEquals(new float[] {1f}, ProbeBlend.weights(0.3f), 0f);
        assertArrayEquals(new float[] {1f, 0f}, ProbeBlend.weights(0.3f, 0.001f), 1e-6f);
        assertArrayEquals(new float[] {0f, 1f}, ProbeBlend.weights(2f, 0.001f), 1e-6f);
        var edge = ProbeBlend.weights(1f + ProbeBlend.FEATHER / 2f, 0.001f);
        assertEquals(0.5f, edge[0], 1e-6f);
        assertEquals(0.5f, edge[1], 1e-6f);
    }

    @Test
    void weightsAlwaysSumToOneAndNeverGoBelowZero() {
        for (float a = 0f; a < 2f; a += 0.07f) {
            for (float b = 0f; b < 2f; b += 0.07f) {
                float sum = 0f;
                for (float weight : ProbeBlend.weights(a, b, 0.001f)) {
                    assertTrue(weight >= -1e-6f, "weight " + weight + " at " + a + ", " + b);
                    sum += weight;
                }
                assertEquals(1f, sum, 1e-5f);
            }
        }
    }

    @Test
    void theNarrowerOfTwoRoomsComesFirstAndTheSkyTakesTheRest() {
        // In both rooms (a closet in a hall): the closet whole, the hall nothing of it, the sky nothing.
        assertArrayEquals(new float[] {1f, 0f, 0f}, ProbeBlend.weights(0.4f, 0.6f, 0.001f), 1e-6f);
        // Out of the closet, in the hall: the hall whole.
        assertArrayEquals(new float[] {0f, 1f, 0f}, ProbeBlend.weights(1.5f, 0.6f, 0.001f), 1e-6f);
        // On the closet's feather and out of the hall: shared with the sky.
        var weights = ProbeBlend.weights(1.075f, 1.5f, 0.001f);
        assertEquals(0.5f, weights[0], 1e-6f);
        assertEquals(0f, weights[1], 1e-6f);
        assertEquals(0.5f, weights[2], 1e-6f);
    }
}
