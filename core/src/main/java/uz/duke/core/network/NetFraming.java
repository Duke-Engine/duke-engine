package uz.duke.core.network;

/**
 * Puts a {@link NetMessage} on the wire and takes it off again: one message per
 * line, tagged with which plane it belongs to.
 *
 * <pre>
 * C &lt;whatever the game's PacketCodec produced&gt;
 * L &lt;playerIndex&gt; &lt;fromFrame&gt;
 * </pre>
 *
 * <p>The engine owns this outer envelope and the control plane inside it; the
 * game owns only the command payload, through its {@link PacketCodec}. That
 * split is why membership can be managed for a game the engine knows nothing
 * about.
 */
final class NetFraming {

    private static final char COMMANDS = 'C';
    private static final char LEFT = 'L';

    private NetFraming() {
    }

    static String encode(NetMessage message, PacketCodec codec) {
        return switch (message) {
            case CommandPacket packet -> COMMANDS + " " + codec.encode(packet);
            case PeerLeft left -> LEFT + " " + left.playerIndex() + " " + left.fromFrame();
        };
    }

    static NetMessage decode(String line, PacketCodec codec) {
        if (line.length() < 2 || line.charAt(1) != ' ') {
            throw new IllegalArgumentException("malformed net message: " + line);
        }
        var body = line.substring(2);
        return switch (line.charAt(0)) {
            case COMMANDS -> codec.decode(body);
            case LEFT -> decodeLeft(body);
            default -> throw new IllegalArgumentException("unknown net message kind: " + line);
        };
    }

    private static PeerLeft decodeLeft(String body) {
        int space = body.indexOf(' ');
        if (space < 0) {
            throw new IllegalArgumentException("malformed peer-left message: " + body);
        }
        return new PeerLeft(Integer.parseInt(body.substring(0, space)),
                Integer.parseInt(body.substring(space + 1)));
    }
}
