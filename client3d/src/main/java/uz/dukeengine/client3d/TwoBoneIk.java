package uz.dukeengine.client3d;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * Two-bone IK (an arm, a leg), solved by the law of cosines: where the elbow and the wrist go for the wrist to touch a target, the
 * elbow bent towards a pole point. Out of reach, the arm points straight at the target.
 */
public final class TwoBoneIk {

    private static final float EPSILON = 1e-6f;

    /**
     * Where the joints go, and each bone's turn from pointing along +y (with +z towards the pole) to its place: the
     * bones of a Blender or UE rig point along their own +y.
     */
    public record Pose(Vector3f elbow, Vector3f wrist, Quaternion upper, Quaternion lower) {
    }

    private TwoBoneIk() {
    }

    public static Pose solve(Vector3f shoulder, float upperLen, float lowerLen, Vector3f target, Vector3f pole) {
        var toTarget = target.subtract(shoulder);
        float d = toTarget.length();
        var toPole = pole.subtract(shoulder);
        // At the shoulder there is no way to the target: the arm reaches towards the pole instead.
        var along = d > EPSILON ? toTarget.divide(d) : direction(toPole, Vector3f.UNIT_Z.negate());
        var bend = toPole.subtract(along.mult(toPole.dot(along)));
        bend = bend.lengthSquared() > EPSILON ? bend.normalizeLocal() : any(along);
        float reach = FastMath.clamp(d, Math.abs(upperLen - lowerLen), upperLen + lowerLen);
        float cos = (upperLen * upperLen + reach * reach - lowerLen * lowerLen)
                / (2f * upperLen * Math.max(reach, EPSILON));
        float angle = FastMath.acos(FastMath.clamp(cos, -1f, 1f));
        var elbow = shoulder.add(along.mult(upperLen * FastMath.cos(angle)))
                .addLocal(bend.mult(upperLen * FastMath.sin(angle)));
        var wrist = shoulder.add(along.mult(reach));
        return new Pose(elbow, wrist, turn(elbow.subtract(shoulder), bend), turn(wrist.subtract(elbow), bend));
    }

    /** How many times longer an arm {@code length} long goes to get {@code distance} away, at most {@code most}. */
    public static float stretch(float distance, float length, float most) {
        return Math.clamp(distance / length, 1f, most);
    }

    /** From +y along the bone, +z towards {@code bend}. */
    private static Quaternion turn(Vector3f bone, Vector3f bend) {
        var y = direction(bone, Vector3f.UNIT_Y);
        var z = bend.subtract(y.mult(bend.dot(y)));
        z = z.lengthSquared() > EPSILON ? z.normalizeLocal() : any(y);
        var x = y.cross(z);
        return new Quaternion().fromAxes(x, y, z);
    }

    private static Vector3f direction(Vector3f v, Vector3f otherwise) {
        return v.lengthSquared() > EPSILON ? v.normalize() : otherwise.clone();
    }

    /** Some direction square to {@code v}. */
    private static Vector3f any(Vector3f v) {
        var side = Math.abs(v.x) < 0.9f ? Vector3f.UNIT_X : Vector3f.UNIT_Y;
        return v.cross(side).normalizeLocal();
    }
}
