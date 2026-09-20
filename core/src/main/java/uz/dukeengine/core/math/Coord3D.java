package uz.dukeengine.core.math;

/**
 * An immutable 3D position/vector, ported from SAGE's {@code Coord3D}.
 *
 * <p>SAGE's struct is mutable; this is a record because immutability is the
 * project default and avoids aliasing bugs in the simulation. Operations return
 * new values rather than mutating in place. Game code keeps a mutable reference
 * field (e.g. an object's position) and reassigns it each frame.
 *
 * <p>Determinism: all arithmetic is plain IEEE-754 {@code float}, which Java
 * evaluates strictly, and {@link #length()} uses correctly-rounded
 * {@link Math#sqrt}, so results are reproducible across machines — a hard
 * requirement for lock-step simulation.
 */
public record Coord3D(float x, float y, float z) {

    public static final Coord3D ZERO = new Coord3D(0, 0, 0);

    public float lengthSqr() {
        return x * x + y * y + z * z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSqr());
    }

    public Coord3D add(Coord3D o) {
        return new Coord3D(x + o.x, y + o.y, z + o.z);
    }

    public Coord3D sub(Coord3D o) {
        return new Coord3D(x - o.x, y - o.y, z - o.z);
    }

    public Coord3D scale(float s) {
        return new Coord3D(x * s, y * s, z * s);
    }

    /** Unit vector in the same direction, or {@link #ZERO} if this is zero-length. */
    public Coord3D normalize() {
        float len = length();
        return len == 0 ? ZERO : new Coord3D(x / len, y / len, z / len);
    }

    public float dot(Coord3D o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Coord3D cross(Coord3D o) {
        return new Coord3D(
                y * o.z - z * o.y,
                z * o.x - x * o.z,
                x * o.y - y * o.x);
    }

    /** Straight-line distance to another point. */
    public float distance(Coord3D o) {
        return sub(o).length();
    }
}
