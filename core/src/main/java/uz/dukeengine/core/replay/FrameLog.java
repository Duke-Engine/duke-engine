package uz.dukeengine.core.replay;

import java.util.List;
import uz.dukeengine.core.message.Command;

/**
 * Watches what a simulation consumes, frame by frame.
 *
 * <p>A deterministic simulation is a function of its starting conditions and the
 * commands applied to it, so those two things <em>are</em> the recording — there
 * is nothing else to keep. The world does not need saving; it can be computed
 * again from the same inputs, which is the same property lock-step already
 * depends on.
 *
 * <p>The commands reported here are the ones actually about to be applied, not
 * the ones somebody asked for. In a network game those differ: input is issued
 * frames earlier and arrives in an order the peers agree on. Recording what the
 * simulation consumed rather than what a player pressed is what makes one
 * recorder work for single-player and multiplayer alike.
 */
public interface FrameLog {

    /**
     * The commands about to be applied on {@code frame}. Never empty — a frame
     * with no input is simply not reported.
     */
    void commands(int frame, List<Command> commands);

    /**
     * Whether this frame should be checkpointed.
     *
     * <p>Asked before the checksum is computed, because computing one walks the
     * whole world and most frames do not want it.
     */
    boolean wantsCheckpoint(int frame);

    /** The world's hash at the start of {@code frame}, before its commands run. */
    void checkpoint(int frame, long checksum);
}
