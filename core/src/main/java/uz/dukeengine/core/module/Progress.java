package uz.dukeengine.core.module;

import uz.dukeengine.core.GameConstants;

/**
 * Whether a mover is getting any nearer the point it walks to: what stops one that circles an obstacle, or is hemmed
 * in, rather than letting it try for ever.
 *
 * <p>Counting blocked frames is not enough: a unit circling an obstacle finds
 * a free step every frame and would orbit forever. What actually matters is
 * whether it is getting closer, so that is what is measured.
 */
final class Progress {

    /** Give up on a leg after this long without getting any closer to it. */
    static final int STUCK_FRAME_LIMIT = 2 * GameConstants.LOGICFRAMES_PER_SECOND;

    /** How much nearer counts as nearer: a quarter of a frame's step. */
    private float epsilon;
    private float closestApproach;
    private int framesWithoutProgress;
    /** How far off its way it was last frame, in radians, to tell a turn toward it from standing still. */
    private float lastOff = Float.MAX_VALUE;

    void setStep(float stepPerFrame) {
        epsilon = stepPerFrame * 0.25f;
    }

    void reset() {
        closestApproach = Float.MAX_VALUE;
        framesWithoutProgress = 0;
        lastOff = Float.MAX_VALUE;
    }

    /** Counted as progress this frame, though it came no nearer: a turn toward its way, or a turn held up. */
    void counted() {
        framesWithoutProgress = 0;
    }

    /** Whether it is now {@code off} its way, having been {@code was} off, and turned toward it since last frame. */
    boolean turnedToward(float off, float was) {
        boolean toward = off < was - 1e-6f && off < lastOff;
        lastOff = off;
        return toward;
    }

    /** True once it has spent {@link #STUCK_FRAME_LIMIT} frames without closing meaningfully on its waypoint. */
    boolean madeNoProgress(float distance) {
        if (distance < closestApproach - epsilon) {
            closestApproach = distance;
            framesWithoutProgress = 0;
            return false;
        }
        return ++framesWithoutProgress >= STUCK_FRAME_LIMIT;
    }
}
