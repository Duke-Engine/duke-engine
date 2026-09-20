package uz.dukeengine.core.thing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

class GeometryTest {

    private static Footprint at(Geometry shape, float x, float y) {
        return new Footprint(shape, new Coord3D(x, y, 0f), 0f);
    }

    private static Footprint facing(Geometry shape, float x, float y, double degrees) {
        return new Footprint(shape, new Coord3D(x, y, 0f), (float) Math.toRadians(degrees));
    }

    @Test
    void roundShapesMeasureCentreToCentre() {
        var a = at(new Geometry.Cylinder(3f, 8f), 0f, 0f);

        assertEquals(4f, a.separation(at(new Geometry.Sphere(3f), 10f, 0f)), 1e-4f);
        assertTrue(a.overlaps(at(new Geometry.Sphere(3f), 5f, 0f)));
        assertFalse(a.overlaps(at(new Geometry.Sphere(3f), 7f, 0f)));
    }

    @Test
    void touchingCountsAsOverlapping() {
        var a = at(new Geometry.Sphere(5f), 0f, 0f);
        var b = at(new Geometry.Sphere(5f), 10f, 0f);

        assertEquals(0f, a.separation(b), 1e-4f);
        assertTrue(a.overlaps(b), "surfaces in contact block movement");
    }

    @Test
    void pointsCollideWithNothing() {
        var point = at(Geometry.POINT, 0f, 0f);
        var solid = at(new Geometry.Sphere(50f), 0f, 0f);

        assertTrue(Geometry.POINT.isPoint());
        assertFalse(point.overlaps(solid), "a shapeless object passes through everything");
        assertFalse(solid.overlaps(point));
    }

    @Test
    void boxMeasuresToItsNearestEdgeNotItsCentre() {
        // 40 long (x) by 20 wide (y), centred at the origin, facing +x.
        var box = at(new Geometry.Box(20f, 10f, 5f), 0f, 0f);
        var unit = new Geometry.Sphere(2f);

        assertEquals(1f, box.separation(at(unit, 23f, 0f)), 1e-4f, "beyond the long end");
        assertEquals(3f, box.separation(at(unit, 0f, 15f)), 1e-4f, "beyond the flat side");
        assertTrue(box.overlaps(at(unit, 21f, 0f)), "just inside the long end");
        assertFalse(box.overlaps(at(unit, 0f, 13f)), "just clear of the flat side");
    }

    @Test
    void boxTurnsWithTheObject() {
        var unit = new Geometry.Sphere(2f);
        var lyingFlat = at(new Geometry.Box(20f, 10f, 5f), 0f, 0f);
        var turned = facing(new Geometry.Box(20f, 10f, 5f), 0f, 0f, 90);

        // The same spot is deep inside the unturned box and just clear of the turned one.
        assertTrue(lyingFlat.overlaps(at(unit, 12f, 0f)));
        assertEquals(0f, turned.separation(at(unit, 12f, 0f)), 1e-4f);
        // ...and the long axis now points along +y.
        assertEquals(3f, turned.separation(at(unit, 0f, 25f)), 1e-4f);
    }

    @Test
    void boxCornersAreRoundedByDistanceNotBySquares() {
        var box = at(new Geometry.Box(20f, 10f, 5f), 0f, 0f);
        // 3 past the end and 4 past the side: the diagonal gap is 5, not 4.
        assertEquals(5f, box.separation(at(Geometry.POINT, 23f, 14f)), 1e-4f);
    }

    @Test
    void containsAsksAboutASinglePoint() {
        var box = facing(new Geometry.Box(20f, 10f, 5f), 100f, 100f, 0);

        assertTrue(box.contains(new Coord3D(115f, 105f, 0f)));
        assertFalse(box.contains(new Coord3D(115f, 115f, 0f)));
    }

    @Test
    void boundingRadiusEnclosesTheShape() {
        assertEquals(7f, new Geometry.Sphere(7f).footprintRadius(), 1e-4f);
        assertEquals(7f, new Geometry.Cylinder(7f, 30f).footprintRadius(), 1e-4f);
        assertEquals(5f, new Geometry.Box(3f, 4f, 2f).footprintRadius(), 1e-4f);
    }

    @Test
    void negativeDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Geometry.Sphere(-1f));
        assertThrows(IllegalArgumentException.class, () -> new Geometry.Cylinder(1f, -1f));
        assertThrows(IllegalArgumentException.class, () -> new Geometry.Box(1f, -1f, 1f));
    }
}
