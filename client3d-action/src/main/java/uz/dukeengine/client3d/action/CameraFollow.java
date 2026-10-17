package uz.dukeengine.client3d.action;

/**
 * The camera kept on one of the player's units — the modern scheme's hero — easing after it with a look ahead of it
 * while it walks; the pan keys look away from it, and it eases back a while after the last.
 *
 * @param of                   the template of the local player's unit it follows, or null for his first unit
 * @param lookAhead            how far ahead of him, along his facing while he walks, it looks
 * @param easeSeconds          the time constant it eases after him with
 * @param lookAheadEaseSeconds the time constant the look ahead comes and goes with
 * @param lookAwaySeconds      how long after the last pan it eases back to him
 */
public record CameraFollow(String of, float lookAhead, float easeSeconds, float lookAheadEaseSeconds,
        float lookAwaySeconds, float orbitSensitivity, boolean invertX, float pitchBand) {

    /** The free camera: nothing followed. */
    public static final CameraFollow NONE = new CameraFollow(null, 0f, 0f, 0f, 0f);

    /** The mouse's turn the action scheme starts at: a quarter of a degree a pixel. */
    public static final float ORBIT_SENSITIVITY = (float) Math.toRadians(0.25);

    /** A follow the mouse does not turn, as every one was before it could. */
    public CameraFollow(String of, float lookAhead, float easeSeconds, float lookAheadEaseSeconds,
            float lookAwaySeconds) {
        this(of, lookAhead, easeSeconds, lookAheadEaseSeconds, lookAwaySeconds, 0f, false, 0f);
    }

    /**
     * The same follow turned round him by the captured mouse: {@code sensitivity} radians a pixel across, the other way
     * where {@code invertX}; mouse y tilting it within {@code pitchBand} radians of its own pitch, none for 0.
     */
    public CameraFollow orbiting(float sensitivity, boolean invert, float band) {
        return new CameraFollow(of, lookAhead, easeSeconds, lookAheadEaseSeconds, lookAwaySeconds,
                Math.max(0f, sensitivity), invert, Math.max(0f, band));
    }

    /** The turn {@code dx} pixels of the captured mouse make, in radians. */
    float turnOf(float dx) {
        return -dx * orbitSensitivity * (invertX ? -1f : 1f);
    }

    /** The local player's unit of {@code of} (null for his first), at the defaults. */
    public static CameraFollow of(String of) {
        return new CameraFollow(of, 8f, 0.15f, 0.4f, 1f);
    }

    boolean follows() {
        return !NONE.equals(this);
    }

    /** What the following keeps from frame to frame: the look ahead as it has eased, and when he last panned. */
    static final class Eye {
        private float aheadX;
        private float aheadY;
        private float pannedAt = Float.NEGATIVE_INFINITY;

        /**
         * One frame of {@code seconds}: the point {@code camera} looks at eased toward the unit at ({@code x},
         * {@code y}) plus its look ahead — unless the player panned, or did within the look-away time.
         */
        void step(CameraFollow look, uz.dukeengine.client3d.ControlScheme.Context camera, float x, float y, float facing, boolean walking,
                boolean panned, float now, float seconds) {
            if (panned) {
                pannedAt = now;
                return;
            }
            if (now - pannedAt < look.lookAwaySeconds()) {
                return;
            }
            float aheadShare = share(seconds, look.lookAheadEaseSeconds());
            float wantX = walking ? (float) Math.cos(facing) * look.lookAhead() : 0f;
            float wantY = walking ? (float) Math.sin(facing) * look.lookAhead() : 0f;
            aheadX += (wantX - aheadX) * aheadShare;
            aheadY += (wantY - aheadY) * aheadShare;
            float share = share(seconds, look.easeSeconds());
            var at = camera.lookingAt();
            camera.lookAt(at[0] + (x + aheadX - at[0]) * share, at[1] + (y + aheadY - at[1]) * share);
        }

        /** The share of the way an exponential ease of time constant {@code tau} closes in {@code seconds}. */
        private static float share(float seconds, float tau) {
            return tau <= 0f ? 1f : 1f - (float) Math.exp(-seconds / tau);
        }
    }
}
