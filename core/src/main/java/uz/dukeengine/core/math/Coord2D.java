package uz.dukeengine.core.math;

/**
 * An immutable 2D position/vector, ported from SAGE's {@code Coord2D}.
 *
 * <p>Used for ground-plane math (movement, facing) where height is irrelevant.
 */
public record Coord2D(float x, float y) {

    public static final Coord2D ZERO = new Coord2D(0, 0);

    public float lengthSqr() {
        return x * x + y * y;
    }

    public float length() {
        return (float) Math.sqrt(lengthSqr());
    }

    public Coord2D add(Coord2D o) {
        return new Coord2D(x + o.x, y + o.y);
    }

    public Coord2D sub(Coord2D o) {
        return new Coord2D(x - o.x, y - o.y);
    }

    public Coord2D scale(float s) {
        return new Coord2D(x * s, y * s);
    }

    public Coord2D normalize() {
        float len = length();
        return len == 0 ? ZERO : new Coord2D(x / len, y / len);
    }

    /**
     * Angle of this vector in radians, where 0 points down the +x axis.
     * Ported from SAGE's {@code Coord2D::toAngle}.
     */
    public float toAngle() {
        return (float) Math.atan2(y, x);
    }
}
