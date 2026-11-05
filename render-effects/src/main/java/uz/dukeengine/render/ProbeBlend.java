package uz.dukeengine.render;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * The arithmetic of box light probes, as {@code LightProbes.glsllib} does it on the GPU (parallax-corrected reflection,
 * how deep a point stands in the bounds, how several probes share a pixel). The shader is the one that draws; this is the
 * one the test pins down.
 *
 * <p>A box probe has two boxes: the one its reflections are projected on, {@code half} here (the walls of the room), and its
 * area, {@link #FEATHER} further out each way, which is what the light filter culls by and the shader reads as the matrix's
 * scale; the probe fades out between them.
 */
final class ProbeBlend {

    /** The area of a probe reaches this share of the projection box's half extent further, fading out. */
    static final float FEATHER = 0.15f;

    private ProbeBlend() {
    }

    /** {@code world} as the projection box with these axes and half extents sees it, centred on {@code centre} and with its faces at +-1. */
    private static Vector3f unit(Quaternion axes, Vector3f centre, Vector3f half, Vector3f world) {
        var local = axes.inverse().mult(world.subtract(centre));
        return new Vector3f(local.x / half.x, local.y / half.y, local.z / half.z);
    }

    /** How deep in the projection box {@code point} stands: 0 at the centre, 1 on its faces, 1 + {@link #FEATHER} where the area ends. */
    static float depth(Quaternion axes, Vector3f centre, Vector3f half, Vector3f point) {
        var at = unit(axes, centre, half, point);
        return Math.max(Math.max(Math.abs(at.x), Math.abs(at.y)), Math.abs(at.z));
    }

    /** How deep in a sphere probe of this radius (its area's): 1 + {@link #FEATHER} at the radius. */
    static float depth(Vector3f centre, float radius, Vector3f point) {
        return point.distance(centre) / radius * (1f + FEATHER);
    }

    /** How much of a pixel a probe lights by itself: all within its bounds, nothing {@link #FEATHER} beyond them. */
    static float influence(float depth) {
        float t = FastMath.clamp((depth - 1f) / FEATHER, 0f, 1f);
        return 1f - t * t * (3f - 2f * t);
    }

    /**
     * The weights of the probes at a pixel, narrowest first, as many as there are depths (1 to 3): each takes its influence
     * of what the narrower ones left, the last all that is left.
     */
    static float[] weights(float... depths) {
        var weights = new float[depths.length];
        float left = 1f;
        for (int i = 0; i < depths.length; i++) {
            weights[i] = i == depths.length - 1 ? left : left * influence(depths[i]);
            left -= weights[i];
        }
        return weights;
    }

    /**
     * {@code direction} from {@code point}, re-aimed from the box's centre at where it meets the box: what to fetch a cube
     * baked at the centre with. A point beyond the box is taken at the nearest point of it. Not a unit vector.
     */
    static Vector3f parallax(Quaternion axes, Vector3f centre, Vector3f half, Vector3f point, Vector3f direction) {
        var ray = axes.inverse().mult(direction);
        ray.set(ray.x / half.x, ray.y / half.y, ray.z / half.z);
        var at = unit(axes, centre, half, point);
        at.set(FastMath.clamp(at.x, -1f, 1f), FastMath.clamp(at.y, -1f, 1f), FastMath.clamp(at.z, -1f, 1f));
        float reach = Float.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            float along = i == 0 ? ray.x : i == 1 ? ray.y : ray.z;
            float from = i == 0 ? at.x : i == 1 ? at.y : at.z;
            if (Math.abs(along) < 1e-6f) {
                continue;
            }
            reach = Math.min(reach, Math.max((1f - from) / along, (-1f - from) / along));
        }
        var hit = at.add(ray.mult(reach));
        return axes.mult(new Vector3f(hit.x * half.x, hit.y * half.y, hit.z * half.z));
    }
}
