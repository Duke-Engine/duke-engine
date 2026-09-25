package uz.dukeengine.client3d;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/**
 * One piece thrown off — the reference's debris, {@code CreateDebris} flown by its {@code PhysicsBehavior}: flung up
 * and out, turning, falling under gravity, striking the ground with a share of its speed kept until it is too slow to
 * leave it again, sliding to a stop where it has friction, and fading over the last frames of its life. Stepped a game
 * frame at a time, in the client's frame (y up); it decides nothing, so it is the client's alone.
 */
final class Thrown {

    /**
     * {@code Thing::isSignificantlyAboveTerrain}: within three frames' fall of the ground — nine frames of gravity, as
     * the reference reckons it — a piece is on the ground for its friction.
     */
    private static final float NEAR_GROUND = 3 * 3;
    /** {@code isVerySmall3D}: slower than this every way across the ground, a sliding piece lies still. */
    private static final float STILL = 0.01f;
    /** {@code W3DDebrisDraw}'s {@code MIN_FINAL_FRAMES}: it has landed only on the ground after this many frames. */
    private static final int MIN_FLIGHT_FRAMES = 3;

    private final Vector3f at;
    private final Vector3f speed;
    private final Vector3f axis;
    private float spin;
    private final float gravity;
    private final float bounce;
    private final float friction;
    private final int life;
    private final boolean lifeFromRest;
    private final int fadeFrames;
    private float turned;
    private int age;
    private int restedFor;
    private boolean resting;
    private boolean sliding;
    private boolean struck;
    private boolean landed;
    private boolean landedNow;

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
        this(at, speed, axis, spin, gravity, bounce, 0f, life, false, fadeFrames);
    }

    /**
     * @param friction     the share of its speed across the ground it loses a frame near the ground; 0 lies where
     *                     it stops bouncing
     * @param lifeFromRest its life counted from the frame it comes to rest, not from the throw
     */
    Thrown(Vector3f at, Vector3f speed, Vector3f axis, float spin, float gravity, float bounce, float friction,
            int life, boolean lifeFromRest, int fadeFrames) {
        this.at = at.clone();
        this.speed = speed.clone();
        this.axis = axis.clone();
        this.spin = spin;
        this.gravity = gravity;
        this.bounce = Math.max(0f, bounce);
        this.friction = Math.clamp(friction, 0f, 1f);
        this.life = Math.max(1, life);
        this.lifeFromRest = lifeFromRest;
        this.fadeFrames = Math.max(0, fadeFrames);
    }

    /** One frame of its flight; false once its life is over and it is to be taken away. */
    boolean frame(WorldMoments.Floor floor) {
        age++;
        struck = false;
        landedNow = false;
        if (resting) {
            restedFor++;
        }
        if ((lifeFromRest ? restedFor : age) >= life) {
            return false;
        }
        if (resting) {
            return true;
        }
        float below = floor.at(at.x, at.z);
        if (friction > 0f && at.y - below <= NEAR_GROUND * gravity) {
            // PhysicsBehavior::applyFrictionalForces: across the ground, and its turning damped with it.
            speed.x *= 1f - friction;
            speed.z *= 1f - friction;
            spin *= 1f - friction;
            sliding |= at.y <= below && speed.y <= 0f; // thrown along the ground: it slides, it does not strike it
        }
        if (sliding) {
            slide(floor);
        } else {
            fly(floor);
        }
        if (!landed && age > MIN_FLIGHT_FRAMES && (struck || sliding || resting)) {
            landed = true;
            landedNow = true;
        }
        return true;
    }

    private void fly(WorldMoments.Floor floor) {
        speed.y -= gravity;
        at.addLocal(speed);
        turned += spin;
        float ground = floor.at(at.x, at.z);
        if (at.y <= ground && speed.y < 0f) {
            at.y = ground;
            struck = true;
            speed.multLocal(bounce).y *= -1f;
            if (speed.y < gravity) {
                speed.y = 0f; // too slow to leave the ground again: it lies where it came down, or slides on
                if (friction > 0f && !still()) {
                    sliding = true;
                } else {
                    rest();
                }
            }
        }
    }

    private void slide(WorldMoments.Floor floor) {
        at.x += speed.x;
        at.z += speed.z;
        at.y = floor.at(at.x, at.z);
        turned += spin;
        if (still()) {
            rest();
        }
    }

    private boolean still() {
        return Math.abs(speed.x) < STILL && Math.abs(speed.z) < STILL;
    }

    private void rest() {
        resting = true;
        sliding = false;
        speed.zero();
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
        int left = life - (lifeFromRest ? restedFor : age);
        return fadeFrames == 0 || left >= fadeFrames ? 1f : Math.max(0f, (float) left / fadeFrames);
    }

    boolean resting() {
        return resting;
    }

    /** Whether it struck the ground from the air this frame. */
    boolean struck() {
        return struck;
    }

    /** Whether this frame it first touched the ground past its first few, bouncing on or not. */
    boolean landedNow() {
        return landedNow;
    }
}
