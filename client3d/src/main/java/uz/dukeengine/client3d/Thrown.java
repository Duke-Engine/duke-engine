package uz.dukeengine.client3d;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * One piece thrown off — the reference's debris, {@code CreateDebris} flown by its {@code PhysicsBehavior}: flung up
 * and out, turning, falling under gravity, striking the ground with a share of its speed kept until it is too slow to
 * leave it again, and fading over the last frames of its life. Stepped a game frame at a time, in the client's frame
 * (y up); it decides nothing, so it is the client's alone.
 */
final class Thrown {

    private final Vector3f at;
    private final Vector3f speed;
    private final Vector3f axis;
    private final float spin;
    private final float gravity;
    private final float bounce;
    private final int life;
    private final int fadeFrames;
    private float turned;
    private int age;
    private boolean resting;

    /**
     * @param speed      how far it goes the first frame, up and out
     * @param axis       what it turns about, of length one
     * @param spin       how far it turns a frame, in radians
     * @param gravity    how much faster it falls each frame
     * @param bounce     the share of its speed it keeps each time it strikes the ground
     * @param life       frames before it is gone
     * @param fadeFrames the last frames of its life, over which it fades away
     */
    Thrown(Vector3f at, Vector3f speed, Vector3f axis, float spin, float gravity, float bounce, int life,
            int fadeFrames) {
        this.at = at.clone();
        this.speed = speed.clone();
        this.axis = axis.clone();
        this.spin = spin;
        this.gravity = gravity;
        this.bounce = Math.max(0f, bounce);
        this.life = Math.max(1, life);
        this.fadeFrames = Math.max(0, fadeFrames);
    }

    /** One frame of its flight; false once its life is over and it is to be taken away. */
    boolean frame(WorldMoments.Floor floor) {
        age++;
        if (age >= life) {
            return false;
        }
        if (resting) {
            return true;
        }
        speed.y -= gravity;
        at.addLocal(speed);
        turned += spin;
        float ground = floor.at(at.x, at.z);
        if (at.y <= ground && speed.y < 0f) {
            at.y = ground;
            speed.multLocal(bounce).y *= -1f;
            if (speed.y < gravity) {
                resting = true; // too slow to leave the ground again: it lies where it came down
                speed.zero();
            }
        }
        return true;
    }

    Vector3f at() {
        return at;
    }

    /** How it is turned by its spin so far. */
    Quaternion turn() {
        return new Quaternion().fromAngleNormalAxis(turned, axis);
    }

    /** How much of it is still to be seen: whole, till the last of its fade frames take it away. */
    float opacity() {
        int left = life - age;
        return fadeFrames == 0 || left >= fadeFrames ? 1f : Math.max(0f, (float) left / fadeFrames);
    }

    boolean resting() {
        return resting;
    }
}
