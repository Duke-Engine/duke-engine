package uz.dukeengine.client3d.action;

import com.jme3.math.Vector3f;
import java.util.function.DoubleUnaryOperator;

/**
 * The camera of a hero seen from behind and over his right shoulder — {@link ActionControls#shoulder}: it stands {@code
 * distance} back from the point it looks past and {@code side} to the right of it, turned and tilted by the held mouse,
 * pulled in where the map's walls or ground would stand between it and the hero and eased out again once they do not.
 * All lengths are the world's units.
 *
 * @param distance       how far back along its line of sight the eye stands
 * @param side           how far to the right of the hero it looks past him, so he is on the left of the picture
 * @param lookHeight     how high over his feet the point he is looked at by lies: his chest or his head
 * @param pitchStart     how steeply it looks down when it begins, radians
 * @param pitchMin       the least it looks down, radians: negative looks up at him from below, but never under the ground
 * @param pitchMax       the most it looks down, radians: short of straight overhead
 * @param sensitivity    how far the held mouse turns it for each pixel, radians
 * @param followSeconds  the time constant it eases after the hero with, which also hides the simulation's steps and a
 *                       lunge
 * @param releaseSeconds the time constant it eases back out with once nothing is in the way; pulling in is at once
 * @param blockerHeight  how high a thing must stand to be in the way: lower things (a barrel, a stone) are seen over
 */
public record ShoulderCamera(float distance, float side, float lookHeight, float pitchStart, float pitchMin,
        float pitchMax, float sensitivity, float followSeconds, float releaseSeconds, float blockerHeight) {

    /** How far the eye keeps from what pulled it in, beyond the near plane's own corners. */
    static final float CLEARANCE = 0.3f;
    /** How finely the ground between the hero and the eye is looked at. */
    static final float GROUND_STEP = 1.5f;

    public ShoulderCamera {
        if (distance <= 0f || lookHeight < 0f || pitchMin >= pitchMax || pitchStart < pitchMin
                || pitchStart > pitchMax || pitchMax >= (float) Math.PI / 2f || sensitivity <= 0f
                || followSeconds < 0f || releaseSeconds < 0f || blockerHeight < 0f) {
            throw new IllegalArgumentException("a shoulder camera needs a distance, pitches inside a quarter turn and"
                    + " a turn for each pixel: " + this);
        }
    }

    /** The follow that keeps the camera's focus on the hero, eased, turned by the held mouse. */
    CameraFollow follow() {
        return new CameraFollow(null, 0f, followSeconds, 0f, 1f).orbiting(sensitivity, false, 0f);
    }

    /** {@code pitch} kept within what it may be. */
    float clamped(float pitch) {
        return Math.clamp(pitch, pitchMin, pitchMax);
    }

    /** The pitch after the held mouse moved {@code dy} pixels down — it looks further down — within its limits. */
    float tilted(float pitch, float dy) {
        return clamped(pitch + dy * sensitivity);
    }

    /** Where the camera would stand with nothing in the way, and which way it looks. */
    record Pose(Vector3f eye, Vector3f gaze, Vector3f right, Vector3f up) {

        /** The distance from {@code focus} to the eye. */
        float reach(Vector3f focus) {
            return eye.distance(focus);
        }
    }

    /**
     * The pose for a hero whose look-at point is {@code focus} (scene axes: x, height, then the world's y), the camera
     * turned {@code yaw} and looking down {@code pitch}: the eye back along the gaze and to the right of it, the gaze
     * the same wherever it is pulled to.
     */
    Pose pose(Vector3f focus, float yaw, float pitch) {
        float level = (float) Math.cos(pitch);
        var back = new Vector3f((float) Math.sin(yaw) * level, (float) Math.sin(pitch), (float) Math.cos(yaw) * level);
        var right = new Vector3f((float) Math.cos(yaw), 0f, (float) -Math.sin(yaw));
        var gaze = back.negate();
        return new Pose(focus.add(back.mult(distance)).addLocal(right.mult(side)), gaze, right, right.cross(gaze));
    }

    /**
     * How far along a line of {@code length} the ground lets it run. {@code over(d)} says how far the line stands above
     * the ground {@code d} along it; the run stops where that first falls to nothing (worked out between the samples), and
     * is 0 where it starts below.
     */
    static float groundRun(float length, DoubleUnaryOperator over) {
        double before = over.applyAsDouble(0);
        if (before <= 0) {
            return 0f;
        }
        float prev = 0f;
        while (prev < length) {
            float at = Math.min(prev + GROUND_STEP, length);
            double now = over.applyAsDouble(at);
            if (now <= 0) {
                return (float) (prev + (at - prev) * before / (before - now));
            }
            prev = at;
            before = now;
        }
        return length;
    }

    /**
     * The length of the boom after a frame of {@code seconds}: pulled in to {@code room} at once, whatever it was, and
     * eased back out toward {@code room} once there is more of it than the boom has.
     */
    float boomAfter(float boom, float room, float seconds) {
        if (room <= boom) {
            return room;
        }
        float share = releaseSeconds <= 0f ? 1f : 1f - (float) Math.exp(-seconds / releaseSeconds);
        return boom + (room - boom) * share;
    }

    /**
     * How far in from its pose the eye must stand to be clear of the scene: each of the five lines from the hero toward
     * the eye — the middle one and one past each corner of the near plane, which {@code nearRight} and {@code nearUp}
     * (its half width and half height) and {@code near} give — is run
     * through {@code scene} as far as the eye, and the eye stands where the shortest stops, a near plane's depth and
     * the clearance short of what stopped it.
     */
    float roomFor(Vector3f focus, Pose pose, float near, float nearRight, float nearUp, Scene scene) {
        float length = pose.reach(focus);
        var way = pose.eye().subtract(focus).normalizeLocal();
        float room = length;
        for (int corner = 0; corner < 5; corner++) {
            float across = corner == 0 ? 0f : (corner & 1) == 0 ? -nearRight : nearRight;
            float over = corner == 0 ? 0f : corner > 2 ? -nearUp : nearUp;
            var from = focus.add(pose.right().mult(across)).addLocal(pose.up().mult(over));
            float ran = scene.run(from, way, length, blockerHeight);
            if (ran < length) {
                room = Math.min(room, ran + near - CLEARANCE);
            }
        }
        return Math.clamp(room, 0f, length);
    }

    /** What the camera asks of the scene: how far a line runs, nothing lower than the height stopping it. */
    @FunctionalInterface
    interface Scene {
        float run(Vector3f from, Vector3f way, float length, float minHeight);
    }

    /** What the camera keeps from frame to frame: how far out its boom is, and how high the point it looks at stands. */
    static final class Boom {
        private float length = Float.NaN;
        private float height = Float.NaN;

        /** Forgotten, so the next frame begins where it should be: after the game's own camera had the screen. */
        void forget() {
            length = Float.NaN;
            height = Float.NaN;
        }

        /** The height of the look-at point after a frame, eased toward {@code wanted}; at once the first time. */
        float heightAfter(ShoulderCamera look, float wanted, float seconds) {
            if (Float.isNaN(height)) {
                height = wanted;
            } else {
                float share = look.followSeconds <= 0f ? 1f : 1f - (float) Math.exp(-seconds / look.followSeconds);
                height += (wanted - height) * share;
            }
            return height;
        }

        /** The boom's length after a frame: see {@link ShoulderCamera#boomAfter}; from the full length the first time. */
        float lengthAfter(ShoulderCamera look, float full, float room, float seconds) {
            length = look.boomAfter(Float.isNaN(length) ? full : length, room, seconds);
            return length;
        }
    }
}
