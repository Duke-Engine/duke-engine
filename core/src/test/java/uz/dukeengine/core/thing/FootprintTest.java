package uz.dukeengine.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/**
 * The gap between two boxes, exactly.
 *
 * <p>Two boxes were compared by their bounding circles, which over-report contact at every corner. In an
 * RTS most vehicles are boxes and a builder is a vehicle, so the builder against its own site is a box
 * against a box: a dozer 11.9 units from a barracks read as overlapping it.
 */
class FootprintTest {

    private static Footprint box(float major, float minor, float x, float y, float degrees) {
        return new Footprint(new Geometry.Box(major, minor, 4f), new Coord3D(x, y, 0f),
                (float) StrictMath.toRadians(degrees));
    }

    /** Two long thin boxes lying side by side, turned: two units apart, where their circles overlap by 14. */
    @Test
    void twoTurnedBoxesAFewUnitsApartHaveAPositiveGapWhereTheirCirclesOverlap() {
        float turn = 30f;
        float across = (float) StrictMath.toRadians(turn + 90f);
        var one = box(10f, 2f, 0f, 0f, turn);
        var two = box(10f, 2f, 6f * (float) StrictMath.cos(across), 6f * (float) StrictMath.sin(across), turn);

        float circles = 6f - one.shape().footprintRadius() - two.shape().footprintRadius();
        assertTrue(circles < -14f, "the bounding circles overlap deeply: " + circles);
        assertEquals(2f, one.separation(two), 1e-3f, "the boxes themselves are two apart");
        assertEquals(one.separation(two), two.separation(one), 1e-5f, "and it reads the same either way");
        assertFalse(one.overlaps(two));
    }

    /**
     * Apart corner to corner, the gap is the diagonal between the corners — which the separating axes alone
     * would under-report as the one-unit gap along either axis.
     */
    @Test
    void twoBoxesFacingCornerToCornerAreTheDiagonalApart() {
        var one = box(1f, 1f, 0f, 0f, 0f);
        var two = box(1f, 1f, 3f, 3f, 0f);

        assertEquals((float) Math.sqrt(2.0), one.separation(two), 1e-5f);
    }

    /** Overlapping, the gap is how deep they are in each other along the shallowest way out. */
    @Test
    void twoOverlappingBoxesReportTheShallowestDepth() {
        var one = box(5f, 5f, 0f, 0f, 0f);
        var two = box(5f, 5f, 8f, 1f, 0f);

        assertEquals(-2f, one.separation(two), 1e-5f, "two deep along x, nine along y: two is the way out");
        assertTrue(one.overlaps(two));
    }

    /** A box against a circle was already exact, and still is. */
    @Test
    void aBoxAgainstACircleIsUnchanged() {
        var box = box(5f, 5f, 0f, 0f, 0f);
        var round = new Footprint(new Geometry.Cylinder(2f, 4f), new Coord3D(10f, 0f, 0f), 0f);

        assertEquals(3f, box.separation(round), 1e-5f);
        assertEquals(3f, round.separation(box), 1e-5f);
    }

    /** A thing's facing worked out once as it turns, to the bit what a footprint works out for itself. */
    @Test
    void aTurnedThingsFootprintCarriesTheFacingItWouldWorkOutItself() {
        var thing = new GameObject(new ObjectId(1), ThingTemplate.named("Tank").build());
        for (float turn : new float[] {0.7f, -0f, 3.1f, 0f}) {
            thing.setOrientation(turn);
            var carried = Footprint.of(thing);
            var worked = new Footprint(carried.shape(), carried.center(), turn);
            assertEquals(Float.floatToRawIntBits(worked.cos()), Float.floatToRawIntBits(carried.cos()));
            assertEquals(Float.floatToRawIntBits(worked.sin()), Float.floatToRawIntBits(carried.sin()));
        }
    }
}
