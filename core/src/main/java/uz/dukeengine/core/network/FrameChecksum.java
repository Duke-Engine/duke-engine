package uz.dukeengine.core.network;

/**
 * One peer's answer to "what does the world look like at frame N?".
 *
 * <p>Lock-step is a promise, not a mechanism: peers exchange commands and
 * <em>trust</em> that running them produces the same world everywhere. Nothing
 * checks it. One unordered iteration, one wall-clock read, one platform-specific
 * {@code Math.sin} is enough to break the promise quietly, and the players are
 * the ones who find out — each of them playing a game the others cannot see.
 *
 * <p>This is the check. Every peer hashes its world at the same agreed frames and
 * says the number out loud; a mismatch means the game has already gone wrong.
 * It cannot repair anything, and it is not meant to: it turns a silent, unfalsifiable
 * failure into a loud one with a frame number attached.
 */
public record FrameChecksum(int frame, int playerIndex, long checksum) implements NetMessage {
}
