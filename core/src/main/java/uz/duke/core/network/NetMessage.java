package uz.duke.core.network;

/**
 * Anything that crosses the wire between peers.
 *
 * <p>Two planes share the connection. {@link CommandPacket} is the <b>data</b>
 * plane — what players did, which is the game's business and is encoded by the
 * game's own {@link PacketCodec}. {@link PeerLeft} is the <b>control</b> plane —
 * who is still in the game, which is the engine's business and which the engine
 * encodes itself.
 *
 * <p>Keeping them apart is what lets the engine manage membership without
 * knowing a single thing about a game's command set.
 */
public sealed interface NetMessage permits CommandPacket, PeerLeft {
}
