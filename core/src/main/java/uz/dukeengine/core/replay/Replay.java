package uz.dukeengine.core.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.network.CommandPacket;
import uz.dukeengine.core.network.FrameChecksum;
import uz.dukeengine.core.network.NetFraming;
import uz.dukeengine.core.network.PacketCodec;
import uz.dukeengine.core.network.PeerLeft;
import uz.dukeengine.core.network.SessionHalted;

/**
 * A recorded game, played back into a fresh simulation.
 *
 * <p>Playback is not a video: nothing is stored about how the world looked. The
 * world is <em>recomputed</em>, by starting the same game and feeding it the same
 * commands on the same frames. That is only possible because the simulation is
 * deterministic — and it is also how the recording tests that claim, since the
 * recorded checkpoints must match what the replay computes.
 *
 * <p>The game itself is not in the file. A recording plays back against the same
 * build and the same game definition that made it; against anything else it is
 * meaningless, and the checkpoints will say so on the first one that differs.
 */
public final class Replay {

    private final Map<Integer, List<Command>> commandsByFrame = new HashMap<>();
    private final Map<Integer, Long> checkpoints = new HashMap<>();
    private final List<Consumer<ReplayMismatch>> mismatchListeners = new ArrayList<>();
    private final int lastFrame;

    private ReplayMismatch mismatch;

    private Replay(Map<Integer, List<Command>> commands, Map<Integer, Long> checkpoints) {
        this.commandsByFrame.putAll(commands);
        this.checkpoints.putAll(checkpoints);
        int last = 0;
        for (var frame : commandsByFrame.keySet()) {
            last = Math.max(last, frame);
        }
        for (var frame : this.checkpoints.keySet()) {
            last = Math.max(last, frame);
        }
        this.lastFrame = last;
    }

    /** Read a recording written by {@link ReplayRecorder}. */
    public static Replay parse(String text, PacketCodec codec) {
        var lines = text.split("\n");
        if (lines.length == 0 || !lines[0].strip().equals(ReplayRecorder.HEADER)) {
            throw new IllegalArgumentException("not a duke replay: "
                    + (lines.length == 0 ? "<empty>" : lines[0]));
        }
        var commands = new HashMap<Integer, List<Command>>();
        var checkpoints = new HashMap<Integer, Long>();
        for (int i = 1; i < lines.length; i++) {
            var line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            switch (NetFraming.decode(line, codec)) {
                case CommandPacket packet -> commands.put(packet.frame(), packet.commands());
                case FrameChecksum sum -> checkpoints.put(sum.frame(), sum.checksum());
                case PeerLeft ignored -> {
                    // Membership is a fact about a live network, not about a game
                    // that already happened; a recording has no use for it.
                }
                case SessionHalted ignored -> {
                    // Likewise: a recording of a game that ended in a desync still
                    // replays as far as it goes, and stops where the frames run out.
                }
            }
        }
        return new Replay(commands, checkpoints);
    }

    /** The last frame the recording covers; playing past it shows an empty game going on. */
    public int getLastFrame() {
        return lastFrame;
    }

    /** Told, once, if the replayed world stops matching the recorded one. */
    public void onMismatch(Consumer<ReplayMismatch> listener) {
        mismatchListeners.add(listener);
    }

    /** The first divergence found, or {@code null} while the replay still matches. */
    public ReplayMismatch getMismatch() {
        return mismatch;
    }

    /**
     * Drive one frame: check the world against the recording, then hand it the
     * commands that frame is due.
     *
     * <p>Call immediately before {@code logic.update()}, which is where the
     * recording was taken from, so the two line up exactly.
     *
     * @return true always — a replay knows every frame's input in advance and so,
     *         unlike a network game, never has anything to wait for
     */
    public boolean beforeStep(GameLogic logic) {
        int frame = logic.getFrame();
        var expected = checkpoints.get(frame);
        if (expected != null && mismatch == null) {
            long actual = logic.checksum();
            if (expected != actual) {
                mismatch = new ReplayMismatch(frame, expected, actual);
                for (var listener : mismatchListeners) {
                    listener.accept(mismatch);
                }
            }
        }
        // The recording is the whole truth about this frame's input. A simulation
        // that generates commands of its own would otherwise have them applied
        // twice: once because it generated them again, once from the recording.
        logic.discardPendingCommands();
        for (var command : commandsByFrame.getOrDefault(frame, List.of())) {
            logic.issueCommand(command);
        }
        return true;
    }
}
