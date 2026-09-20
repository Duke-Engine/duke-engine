package uz.dukeengine.core.network;

/**
 * A player is out of the game, and everyone must stop waiting for them from
 * {@code fromFrame} onward.
 *
 * <p>Lock-step cannot let each peer notice a dropped player for itself. Two peers
 * that stop expecting a third at different frames apply different command sets
 * and their worlds diverge — the very thing lock-step exists to prevent. So the
 * decision is made once, by the host, and announced as a fact about a specific
 * frame. Every peer then drops the player at exactly that frame, whether it
 * noticed the disconnection or not.
 *
 * <p>{@code fromFrame} is far enough ahead that no peer has simulated it yet,
 * for the same reason input is sent ahead: a decision about the past cannot be
 * applied consistently.
 */
public record PeerLeft(int playerIndex, int fromFrame) implements NetMessage {
}
