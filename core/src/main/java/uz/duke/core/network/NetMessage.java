package uz.duke.core.network;

/**
 * Anything that crosses the wire between peers.
 *
 * <p>Two planes share the connection. {@link CommandPacket} is the <b>data</b>
 * plane — what players did, which is the game's business and is encoded by the
 * game's own {@link PacketCodec}. {@link PeerLeft} and {@link FrameChecksum} are
 * the <b>control</b> plane — who is still in the game, and whether everyone is
 * still playing the same one. Both are the engine's business, and the engine
 * encodes them itself.
 *
 * <p>Keeping them apart is what lets the engine manage membership and verify
 * determinism without knowing a single thing about a game's command set.
 */
public sealed interface NetMessage permits CommandPacket, PeerLeft, FrameChecksum {
}
