package uz.dukeengine.core.thing;

import uz.dukeengine.core.math.Coord3D;

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
        return new Footprint(Solid.of(object.getTemplate()),
                object.getPosition(), object.getOrientation());
    }

    /** The footprint {@code object} would have if it stood at {@code position}. */
    public static Footprint of(GameObject object, Coord3D position) {
        return new Footprint(Solid.of(object.getTemplate()), position, object.getOrientation());
    }

    /**
     * The point of this shape's ground outline nearest {@code point}, at {@code point}'s height — the point itself
     * where it lies within the shape, and the centre for a shape with no extent. Where a blast's edge meets a thing.
     */
    public Coord3D nearestTo(Coord3D point) {
        float dx = point.x() - center.x();
        float dy = point.y() - center.y();
        if (shape instanceof Geometry.Box box) {
            float cos = (float) StrictMath.cos(orientation);
            float sin = (float) StrictMath.sin(orientation);
            float localX = Math.clamp(dx * cos + dy * sin, -box.majorRadius(), box.majorRadius());
            float localY = Math.clamp(-dx * sin + dy * cos, -box.minorRadius(), box.minorRadius());
            return new Coord3D(center.x() + localX * cos - localY * sin, center.y() + localX * sin + localY * cos,
                    point.z());
        }
        float radius = shape.footprintRadius();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance <= radius) {
            return point;
        }
        return new Coord3D(center.x() + dx / distance * radius, center.y() + dy / distance * radius, point.z());
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
     * <p>Exact for every pair, two boxes included. Two boxes used to be compared by
     * their bounding circles, on the reasoning that only a building is tested
     * against a building — but in an RTS most vehicles are boxes, and a builder is a
     * vehicle, so the builder against its own site is a box against a box. Measured
     * there: a dozer 11.9 units from a barracks' wall read as overlapping it by
     * 0.16, and the order to build was given up. See {@link #boxToBox}.
     */
    public float separation(Footprint other) {
        return switch (shape) {
            case Geometry.Box box -> other.shape instanceof Geometry.Box otherBox
                    ? boxToBox(box, center, orientation, otherBox, other.center, other.orientation)
                    : boxToCircle(box, center, orientation, other.center, other.shape.footprintRadius());
            case Geometry.Sphere sphere -> circleTo(sphere.radius(), other);
            case Geometry.Cylinder cylinder -> circleTo(cylinder.radius(), other);
        };
    }

    /**
     * The gap from this shape's outline to a bare point: 0 on the outline,
     * negative inside, positive outside.
     */
    public float distanceTo(Coord3D point) {
        return separation(new Footprint(Geometry.POINT, point, 0f));
    }

    /** True when {@code point} lies inside this shape's ground outline. */
    public boolean contains(Coord3D point) {
        return distanceTo(point) <= 0f;
    }

    private float circleTo(float radius, Footprint other) {
        if (other.shape instanceof Geometry.Box box) {
            return boxToCircle(box, other.center, other.orientation, center, radius);
        }
        return groundDistance(center, other.center) - radius - other.shape.footprintRadius();
    }

    /**
     * The exact gap between two turned rectangles.
     *
     * <p>Overlapping, by separating axes: each rectangle's two edge directions are
     * the only axes a gap between two rectangles can open along, so the rectangles
     * overlap exactly when all four projections do, and the shallowest of those
     * overlaps is how deep they are in each other — returned negative.
     *
     * <p>Apart, by corners and edges: the nearest two points of two separate convex
     * shapes are a corner of one and a point on an edge of the other, so the gap is
     * the least of the sixteen corner-to-edge distances. The separating axes alone
     * would under-report it wherever two corners face each other diagonally.
     */
    private static float boxToBox(Geometry.Box a, Coord3D aCenter, float aTurn,
                                  Geometry.Box b, Coord3D bCenter, float bTurn) {
        float[] one = corners(a, aCenter, aTurn);
        float[] two = corners(b, bCenter, bTurn);
        float widest = -Float.MAX_VALUE;
        for (float turn : new float[] {aTurn, bTurn}) {
            float cos = (float) StrictMath.cos(turn);
            float sin = (float) StrictMath.sin(turn);
            widest = Math.max(widest, gapAlong(one, two, cos, sin));
            widest = Math.max(widest, gapAlong(one, two, -sin, cos));
        }
        if (widest <= 0f) {
            return widest; // overlapping, or touching: the shallowest way out
        }
        float nearest = Float.MAX_VALUE;
        for (int corner = 0; corner < 4; corner++) {
            for (int edge = 0; edge < 4; edge++) {
                nearest = Math.min(nearest, toSegment(one[corner * 2], one[corner * 2 + 1], two, edge));
                nearest = Math.min(nearest, toSegment(two[corner * 2], two[corner * 2 + 1], one, edge));
            }
        }
        return nearest;
    }

    /** A box's four corners, x then y, in order round it. */
    private static float[] corners(Geometry.Box box, Coord3D at, float turn) {
        float cos = (float) StrictMath.cos(turn);
        float sin = (float) StrictMath.sin(turn);
        float[] across = {box.majorRadius(), -box.majorRadius(), -box.majorRadius(), box.majorRadius()};
        float[] along = {box.minorRadius(), box.minorRadius(), -box.minorRadius(), -box.minorRadius()};
        float[] points = new float[8];
        for (int i = 0; i < 4; i++) {
            points[i * 2] = at.x() + across[i] * cos - along[i] * sin;
            points[i * 2 + 1] = at.y() + across[i] * sin + along[i] * cos;
        }
        return points;
    }

    /** How far apart two corner sets are along one axis; negative where their shadows on it overlap. */
    private static float gapAlong(float[] one, float[] two, float axisX, float axisY) {
        float oneLow = Float.MAX_VALUE;
        float oneHigh = -Float.MAX_VALUE;
        float twoLow = Float.MAX_VALUE;
        float twoHigh = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            float p = one[i * 2] * axisX + one[i * 2 + 1] * axisY;
            float q = two[i * 2] * axisX + two[i * 2 + 1] * axisY;
            oneLow = Math.min(oneLow, p);
            oneHigh = Math.max(oneHigh, p);
            twoLow = Math.min(twoLow, q);
            twoHigh = Math.max(twoHigh, q);
        }
        return Math.max(twoLow - oneHigh, oneLow - twoHigh);
    }

    /** From a point to one edge of a corner set — edge {@code i} runs from corner i to corner i + 1. */
    private static float toSegment(float px, float py, float[] corners, int edge) {
        float ax = corners[edge * 2];
        float ay = corners[edge * 2 + 1];
        float bx = corners[(edge + 1) % 4 * 2];
        float by = corners[(edge + 1) % 4 * 2 + 1];
        float ex = bx - ax;
        float ey = by - ay;
        float length = ex * ex + ey * ey;
        float t = length <= 0f ? 0f : Math.clamp(((px - ax) * ex + (py - ay) * ey) / length, 0f, 1f);
        float dx = px - (ax + ex * t);
        float dy = py - (ay + ey * t);
        return (float) Math.sqrt(dx * dx + dy * dy);
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
