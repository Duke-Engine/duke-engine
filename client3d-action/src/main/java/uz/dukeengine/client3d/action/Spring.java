package uz.dukeengine.client3d.action;

/**
 * One number following another as a critically damped spring does — the way third-person cameras chase their hero.
 * An exponential ease answers a change of the target's speed at once, so the camera's own speed jumps: a start, a stop
 * or a turn of the hero is a jolt. The spring's speed is its own and changes smoothly; it gathers pace behind him,
 * trails him by his speed times {@code smoothSeconds} while he runs, and settles on him without overshooting.
 */
final class Spring {

    private float speed;

    /** Where {@code value} is after {@code seconds} following {@code target}; at once for no smoothing. */
    float after(float value, float target, float smoothSeconds, float seconds) {
        if (smoothSeconds <= 0f || seconds <= 0f) {
            speed = 0f;
            return smoothSeconds <= 0f ? target : value;
        }
        float omega = 2f / smoothSeconds;
        float x = omega * seconds;
        float decay = 1f / (1f + x + 0.48f * x * x + 0.235f * x * x * x); // exp(-x), stable for any frame length
        float off = value - target;
        float pull = (speed + omega * off) * seconds;
        speed = (speed - omega * pull) * decay;
        return target + (off + pull) * decay;
    }

    /** Stopped where it is: after a cut, so it does not carry the old speed on. */
    void stop() {
        speed = 0f;
    }
}
