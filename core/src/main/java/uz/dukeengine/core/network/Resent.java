package uz.dukeengine.core.network;

/**
 * A peer has sent the game's new relay everything it held for the frames still in play — the reference's
 * {@code resendPendingCommands} after its packet router left. The new relay decides who is gone, and from which frame,
 * only once every peer that reached it has said this: until then a packet of the old relay's that one of them held
 * could still be on its way, and a frame run with it by one peer must be run with it by all.
 */
public record Resent(int playerIndex) implements NetMessage {
}
