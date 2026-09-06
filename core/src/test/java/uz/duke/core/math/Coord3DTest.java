package uz.duke.core.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class Coord3DTest {

    @Test
    void lengthOf345Triple() {
        assertEquals(5.0f, new Coord3D(3, 4, 0).length(), 1e-6f);
    }

    @Test
    void addAndSubAreInverses() {
        var a = new Coord3D(1, 2, 3);
        var b = new Coord3D(4, 5, 6);
        assertEquals(a, a.add(b).sub(b));
    }

    @Test
    void crossProductOfAxes() {
        var x = new Coord3D(1, 0, 0);
        var y = new Coord3D(0, 1, 0);
        assertEquals(new Coord3D(0, 0, 1), x.cross(y));
    }

    @Test
    void normalizeYieldsUnitLength() {
        assertEquals(1.0f, new Coord3D(0, 3, 4).normalize().length(), 1e-6f);
    }

    @Test
    void normalizeZeroStaysZero() {
        assertEquals(Coord3D.ZERO, Coord3D.ZERO.normalize());
    }

    @Test
    void dotOfPerpendicularIsZero() {
        assertEquals(0.0f, new Coord3D(1, 0, 0).dot(new Coord3D(0, 1, 0)), 1e-6f);
    }
}
