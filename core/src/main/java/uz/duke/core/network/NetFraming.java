package uz.duke.core.network;

/**
 * Puts a {@link NetMessage} on the wire and takes it off again: one message per
 * line, tagged with which plane it belongs to.
 *
 * <pre>
 * C &lt;whatever the game's PacketCodec produced&gt;
 * L &lt;playerIndex&gt; &lt;fromFrame&gt;
 * K &lt;playerIndex&gt; &lt;frame&gt; &lt;checksum&gt;
 * H &lt;frame&gt; &lt;playerIndex&gt; &lt;expected&gt; &lt;actual&gt;
 * </pre>
 *
 * <p>The engine owns this outer envelope and the control plane inside it; the
 * game owns only the command payload, through its {@link PacketCodec}. That
 * split is why membership can be managed for a game the engine knows nothing
 * about.
 *
 * <p>It is also the format a recording is written in — a replay is this same
 * stream of commands and checksums, kept instead of thrown away. See
 * {@code uz.duke.core.replay}.
 */
public final class NetFraming {

    private static final char COMMANDS = 'C';
    private static final char LEFT = 'L';
    private static final char CHECKSUM = 'K';
    private static final char HALTED = 'H';

    private NetFraming() {
    }

    public static String encode(NetMessage message, PacketCodec codec) {
        return switch (message) {
            case CommandPacket packet -> COMMANDS + " " + codec.encode(packet);
            case PeerLeft left -> LEFT + " " + left.playerIndex() + " " + left.fromFrame();
            case FrameChecksum sum ->
                    CHECKSUM + " " + sum.playerIndex() + " " + sum.frame() + " " + sum.checksum();
            case SessionHalted halted -> HALTED + " " + halted.frame() + " " + halted.playerIndex()
                    + " " + halted.expected() + " " + halted.actual();
        };
    }

    public static NetMessage decode(String line, PacketCodec codec) {
        if (line.length() < 2 || line.charAt(1) != ' ') {
            throw new IllegalArgumentException("malformed net message: " + line);
        }
        var body = line.substring(2);
        return switch (line.charAt(0)) {
            case COMMANDS -> codec.decode(body);
            case LEFT -> decodeLeft(body);
            case CHECKSUM -> decodeChecksum(body);
            case HALTED -> decodeHalted(body);
            default -> throw new IllegalArgumentException("unknown net message kind: " + line);
        };
    }

    private static SessionHalted decodeHalted(String body) {
        var parts = fields(body, 4, "session-halted");
        return new SessionHalted(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Long.parseLong(parts[2]), Long.parseLong(parts[3]));
    }

    private static PeerLeft decodeLeft(String body) {
        var parts = fields(body, 2, "peer-left");
        return new PeerLeft(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private static FrameChecksum decodeChecksum(String body) {
        var parts = fields(body, 3, "checksum");
        return new FrameChecksum(Integer.parseInt(parts[1]), Integer.parseInt(parts[0]),
                Long.parseLong(parts[2]));
    }

    private static String[] fields(String body, int expected, String what) {
        var parts = body.split(" ", expected);
        if (parts.length != expected) {
            throw new IllegalArgumentException("malformed " + what + " message: " + body);
        }
        return parts;
    }
}
