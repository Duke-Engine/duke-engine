package uz.duke.core.thing;

import uz.duke.core.math.Coord3D;

/**
 * A {@link Geometry} placed in the world: where an object actually stands, and
 * which way it faces. This is what collision is asked about.
 *
 * <p>All tests are on the ground plane. RTS-style simulations resolve movement
 * in 2D — height is carried on the shape for the client and for future
 * air/ground layering, but two footprints that overlap on the ground overlap
 * here regardless of their heights.
 *
 * <p>Determinism: only {@link Math#sqrt} (correctly rounded) and
 * {@link StrictMath#cos}/{@link StrictMath#sin} (bit-identical on every JVM) are
 * used, so two peers always agree on what is blocked.
 *
 * @param shape       the object's physical shape
 * @param center      where it stands, in world units
 * @param orientation its facing in radians (0 = +x); ignored by round shapes
 */
public record Footprint(Geometry shape, Coord3D center, float orientation) {

    /** The footprint of {@code object} where it currently stands. */
    public static Footprint of(GameObject object) {
        return new Footprint(object.getTemplate().getGeometry(),
                object.getPosition(), object.getOrientation());
    }

    /** The footprint {@code object} would have if it stood at {@code position}. */
    public static Footprint of(GameObject object, Coord3D position) {
        return new Footprint(object.getTemplate().getGeometry(), position, object.getOrientation());
    }

    /** True when the two shapes share ground. Points never overlap anything. */
    public boolean overlaps(Footprint other) {
        if (shape.isPoint() || other.shape.isPoint()) {
            return false;
        }
        return separation(other) <= 0f;
    }

    /**
     * The shortest gap between the two shapes' outlines on the ground: negative
     * when they overlap, 0 when just touching, positive when apart.
     *
     * <p>Two boxes are compared by their bounding circles — the one approximation
     * here. It over-reports contact at a box's corners, which matters only when
     * two box-shaped objects are tested against each other (in practice, one
     * building against another). Every unit-against-anything test is exact.
     */
    public float separation(Footprint other) {
        return switch (shape) {
            case Geometry.Box box -> other.shape instanceof Geometry.Box
                    ? boundingGap(other)
                    : boxToCircle(box, center, orientation, other.center, other.shape.footprintRadius());
            case Geometry.Sphere sphere -> circleTo(sphere.radius(), other);
            case Geometry.Cylinder cylinder -> circleTo(cylinder.radius(), other);
        };
    }

    /** True when {@code point} lies inside this shape's ground outline. */
    public boolean contains(Coord3D point) {
        return separation(new Footprint(Geometry.POINT, point, 0f)) <= 0f;
    }

    private float circleTo(float radius, Footprint other) {
        if (other.shape instanceof Geometry.Box box) {
            return boxToCircle(box, other.center, other.orientation, center, radius);
        }
        return groundDistance(center, other.center) - radius - other.shape.footprintRadius();
    }

    private float boundingGap(Footprint other) {
        return groundDistance(center, other.center)
                - shape.footprintRadius() - other.shape.footprintRadius();
    }

    /**
     * Exact gap between an oriented box and a circle: rotate the circle's centre
     * into the box's own frame, where the box is axis-aligned and the nearest
     * point is a clamp away.
     */
    private static float boxToCircle(Geometry.Box box, Coord3D boxCenter, float boxOrientation,
                                     Coord3D circleCenter, float circleRadius) {
        float dx = circleCenter.x() - boxCenter.x();
        float dy = circleCenter.y() - boxCenter.y();
        float cos = (float) StrictMath.cos(boxOrientation);
        float sin = (float) StrictMath.sin(boxOrientation);
        float localX = dx * cos + dy * sin;   // rotate by -boxOrientation
        float localY = -dx * sin + dy * cos;

        float outX = Math.max(Math.abs(localX) - box.majorRadius(), 0f);
        float outY = Math.max(Math.abs(localY) - box.minorRadius(), 0f);
        return (float) Math.sqrt(outX * outX + outY * outY) - circleRadius;
    }

    private static float groundDistance(Coord3D a, Coord3D b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
