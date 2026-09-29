package uz.dukeengine.client3d;

import com.jme3.math.FastMath;
import com.jme3.scene.Mesh;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.FloatBuffer;
import java.util.function.BiFunction;
import uz.dukeengine.core.math.Coord3D;

/**
 * A disc laid over the ground — a ring's wash, the mark under a selected thing — its middle and rings out to its edge
 * {@link #STEP} apart, each point on the ground under it, where a flat disc at the height under its middle put the half
 * on a slope that climbs under the hill. Its edge is a polygon of its own corners, each side cut to steps no longer
 * than {@link #STEP}, so a disc of few corners keeps them. Its buffers are kept and made again only when a larger disc
 * needs more.
 */
final class GroundDisc {

    /** How far apart its points stand at most, in world units: the ground bends inside a cell. */
    static final float STEP = 2f;
    /** The most points round it and rings inside it: a very large disc is drawn a little coarser. */
    static final int MOST_POINTS = 512;
    private static final int MOST_RINGS = 48;

    private final int corners;
    private FloatBuffer points = BufferUtils.createFloatBuffer(0);
    private float[] units = new float[0];
    private int laidAround = -1;
    private int laidRings = -1;

    GroundDisc(int corners) {
        this.corners = Math.clamp(corners, 3, MOST_POINTS);
    }

    /** Its points round a disc of {@code radius}: its corners, each side cut to steps of {@link #STEP} at most. */
    int pointsAround(float radius) {
        float side = 2f * radius * FastMath.sin(FastMath.PI / corners);
        int cuts = Math.clamp((int) Math.ceil(side / STEP), 1, Math.max(1, MOST_POINTS / corners));
        return corners * cuts;
    }

    /** Its {@code around} points on a disc of radius one, x then z, along the sides between its corners. */
    float[] unitsFor(int around) {
        if (units.length == around * 2) {
            return units;
        }
        units = new float[around * 2];
        int cuts = around / corners;
        float step = FastMath.TWO_PI / corners;
        for (int point = 0; point < around; point++) {
            int corner = point / cuts;
            float along = (float) (point % cuts) / cuts;
            units[point * 2] = FastMath.interpolateLinear(along, FastMath.cos(corner * step),
                    FastMath.cos((corner + 1) * step));
            units[point * 2 + 1] = FastMath.interpolateLinear(along, FastMath.sin(corner * step),
                    FastMath.sin((corner + 1) * step));
        }
        return units;
    }

    /**
     * Lay it into {@code mesh} round {@code at}, {@code radius} across each way, each point as high over {@code base}
     * — the ground under its middle, where its node stands — as the ground under it.
     */
    void lay(Mesh mesh, Coord3D at, float radius, float base, BiFunction<Float, Float, Float> floorAt) {
        int around = pointsAround(radius);
        int rings = Math.clamp((int) Math.ceil(radius / STEP), 1, MOST_RINGS);
        if (points.capacity() < (1 + rings * around) * 3) {
            points = BufferUtils.createFloatBuffer((1 + rings * around) * 3);
        }
        points.clear();
        put(points, at, 0f, 0f, base, floorAt);
        var u = unitsFor(around);
        for (int ring = 1; ring <= rings; ring++) {
            float out = radius * ring / rings;
            for (int point = 0; point < around; point++) {
                put(points, at, u[point * 2] * out, u[point * 2 + 1] * out, base, floorAt);
            }
        }
        mesh.setBuffer(VertexBuffer.Type.Position, 3, points.flip());
        if (around != laidAround || rings != laidRings) {
            laidAround = around;
            laidRings = rings;
            var order = BufferUtils.createShortBuffer(around * 3 + (rings - 1) * around * 6);
            for (int point = 0; point < around; point++) {
                order.put((short) 0).put((short) (1 + point)).put((short) (1 + (point + 1) % around));
            }
            for (int ring = 1; ring < rings; ring++) {
                int in = 1 + (ring - 1) * around;
                int out = in + around;
                for (int point = 0; point < around; point++) {
                    int next = (point + 1) % around;
                    order.put((short) (in + point)).put((short) (out + point)).put((short) (out + next));
                    order.put((short) (in + point)).put((short) (out + next)).put((short) (in + next));
                }
            }
            mesh.setBuffer(VertexBuffer.Type.Index, 3, order.flip());
        }
        mesh.updateCounts();
    }

    /** A point {@code x, z} from the middle, as high over the middle's ground as its own ground stands. */
    static void put(FloatBuffer points, Coord3D at, float x, float z, float base,
            BiFunction<Float, Float, Float> floorAt) {
        points.put(x).put(floorAt.apply(at.x() + x, at.y() + z) - base).put(z);
    }
}
