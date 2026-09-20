package uz.dukeengine.core.replay;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.network.CommandPacket;
import uz.dukeengine.core.network.FrameChecksum;
import uz.dukeengine.core.network.NetFraming;
import uz.dukeengine.core.network.PacketCodec;

/**
 * Writes down what a game was, as it happens.
 *
 * <p>The recording is text, in exactly the format peers already speak over the
 * wire: a line per frame that had commands, and a checkpoint line every
 * {@link #CHECKPOINT_INTERVAL} frames. A replay really is the network stream
 * kept instead of discarded, which is why no new format was invented for it.
 *
 * <p>The checkpoints are what make a recording worth more than a demo. Playing
 * one back and finding a world that no longer matches is a determinism failure
 * caught in a build, on a machine, with a frame number — rather than in a real
 * game, on somebody else's machine, with nothing to go on.
 */
public final class ReplayRecorder implements FrameLog {

    /** Header line: the format, so an old recording fails loudly rather than oddly. */
    public static final String HEADER = "DUKE-REPLAY 1";

    /** How often the world is hashed into the recording. Once a second at 30Hz. */
    public static final int CHECKPOINT_INTERVAL = 30;

    /**
     * A recorded frame's commands come from every player at once, so no single
     * player index describes them. Each {@link Command} carries its own.
     */
    static final int ALL_PLAYERS = 0;

    private final PacketCodec codec;
    private final List<String> lines = new ArrayList<>();
    private int lastFrame;

    public ReplayRecorder(PacketCodec codec) {
        this.codec = codec;
        lines.add(HEADER);
    }

    @Override
    public void commands(int frame, List<Command> commands) {
        lastFrame = Math.max(lastFrame, frame);
        lines.add(NetFraming.encode(new CommandPacket(frame, ALL_PLAYERS, commands), codec));
    }

    @Override
    public boolean wantsCheckpoint(int frame) {
        return frame % CHECKPOINT_INTERVAL == 0;
    }

    @Override
    public void checkpoint(int frame, long checksum) {
        lastFrame = Math.max(lastFrame, frame);
        lines.add(NetFraming.encode(new FrameChecksum(frame, ALL_PLAYERS, checksum), codec));
    }

    /** The last frame this recording covers. */
    public int getLastFrame() {
        return lastFrame;
    }

    /** The recording so far, ready to write to a file or hand to {@link Replay}. */
    public String toText() {
        return String.join("\n", lines) + "\n";
    }
}
