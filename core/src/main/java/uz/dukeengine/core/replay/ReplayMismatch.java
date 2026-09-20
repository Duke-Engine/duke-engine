package uz.dukeengine.core.replay;

/**
 * A replayed world stopped matching the one that was recorded.
 *
 * <p>This is a determinism failure, and a valuable one: the same build, the same
 * machine, the same inputs, and a different answer. It means something in the
 * simulation is reading state that is not part of the simulation — a clock, a
 * hash order, a platform-dependent function.
 *
 * <p>The frame is the whole finding. Divergence is not gradual, so whatever ran
 * on {@code frame} is the cause.
 */
public record ReplayMismatch(int frame, long recorded, long replayed) {

    @Override
    public String toString() {
        return "replay diverged at frame " + frame
                + ": recorded " + recorded + ", replayed " + replayed;
    }
}
