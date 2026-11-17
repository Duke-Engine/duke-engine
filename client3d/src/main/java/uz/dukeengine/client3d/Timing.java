package uz.dukeengine.client3d;

/**
 * Moments along a machine's own clock: a float that runs from 0 as the work goes, read once a frame. A sound, a spark
 * or a result belongs to the point of the motion it goes with, not to a timer of its own — so a slower or quicker clock
 * keeps them together.
 */
public final class Timing {

    private Timing() {
    }

    /**
     * Whether the clock passed {@code mark} this frame, going from {@code before} to {@code now}: true in exactly one frame
     * of a run forwards (the one reaching it), never twice, and not on a clock standing at it.
     */
    public static boolean crossed(float before, float now, float mark) {
        return before < mark && now >= mark;
    }
}
