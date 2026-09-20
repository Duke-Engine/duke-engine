package uz.dukeengine.core.math;

/**
 * An immutable integer 3D coordinate, ported from SAGE's {@code ICoord3D}.
 *
 * <p>Used where positions are cell/grid indices rather than world space.
 */
public record ICoord3D(int x, int y, int z) {

    public static final ICoord3D ZERO = new ICoord3D(0, 0, 0);

    public int lengthSqr() {
        return x * x + y * y + z * z;
    }

    public ICoord3D add(ICoord3D o) {
        return new ICoord3D(x + o.x, y + o.y, z + o.z);
    }

    public ICoord3D sub(ICoord3D o) {
        return new ICoord3D(x - o.x, y - o.y, z - o.z);
    }
}
