package uz.dukeengine.client3d;

import com.jme3.math.Transform;
import com.jme3.math.Vector3f;

/**
 * A thing passed from one place to another that may itself be moving (a bar from the table into the hands): it leaves
 * exactly where it was, eases (smoothstep) into the target over the given time, and from then on is exactly the target,
 * however the target moves. Position and scale are blended straight, the turn along the shorter arc.
 */
public final class Handoff {

    private Transform from;
    private float seconds;
    private float elapsed;

    /** Starts a handoff from {@code fromWorld}, taking {@code seconds} (0 or less: at the target at once). */
    public void start(Transform fromWorld, float seconds) {
        from = fromWorld.clone();
        this.seconds = seconds;
        elapsed = 0f;
    }

    /** Whether it is still on its way: until then {@link #at} is not yet the target. */
    public boolean running() {
        return from != null;
    }

    /** Where the thing is {@code dt} seconds on, the target being {@code targetWorld} now. */
    public Transform at(Transform targetWorld, float dt) {
        if (from == null) {
            return targetWorld.clone();
        }
        elapsed = Math.min(seconds, elapsed + dt);
        float t = seconds > 0f ? elapsed / seconds : 1f;
        if (t >= 1f) {
            from = null;
            return targetWorld.clone();
        }
        float s = t * t * (3f - 2f * t);
        var out = new Transform();
        out.setTranslation(new Vector3f().interpolateLocal(from.getTranslation(), targetWorld.getTranslation(), s));
        out.setScale(new Vector3f().interpolateLocal(from.getScale(), targetWorld.getScale(), s));
        out.getRotation().slerp(from.getRotation(), targetWorld.getRotation(), s);
        return out;
    }
}
