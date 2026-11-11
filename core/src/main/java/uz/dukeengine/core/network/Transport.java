package uz.dukeengine.core.network;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Carries {@link NetMessage}s between the peers of a lock-step game.
 *
 * <p>{@link #send} reaches every peer <em>and</em> echoes back locally, so a peer
 * hears its own player through exactly the same path as everyone else's — one
 * code path, no special case for "mine".
 *
 * <p>The simulation is single-threaded, so a networked transport must never hand
 * an arrival to a listener from its reader thread. It queues instead, and the
 * game thread calls {@link #pump()} once per loop to deliver them on its own
 * thread. A disconnection is delivered through the same queue, in order, so
 * "this peer's last packet" and "this peer is gone" can never be seen the wrong
 * way round.
 */
public interface Transport {

    /** Send to all peers, including a local echo. */
    void send(NetMessage message);

    /** Register a listener invoked for every message that arrives (local or remote). */
    void subscribe(Consumer<NetMessage> listener);

    /**
     * Deliver everything that has arrived since the last call, on this thread.
     * In-process transports have nothing to do.
     */
    default void pump() {
    }

    /**
     * Register a listener told, by player index, that a peer's connection has
     * dropped — reported in order with that peer's messages, never before its
     * last one.
     *
     * <p>This is a statement about a socket, not a decision about the game: only
     * the host turns it into a {@link PeerLeft}, so every peer drops the player
     * on the same frame.
     */
    default void onLinkLost(IntConsumer listener) {
    }

    /**
     * Say a line of the game's own to every other peer, beside the lock-step, from any thread: it is not ordered with
     * the frames and holds none of them up. Nothing, for a transport with no peers.
     */
    default void aside(int sender, String text) {
    }

    /**
     * Register a listener told, on a reader's thread and never the game's, each line said aside by a peer: who said
     * it, as the relay saw it, and what.
     */
    default void onAside(BiConsumer<Integer, String> listener) {
    }

    /**
     * Let a peer's link go — the relay taking a player out of the game, who sent nothing for too long or was voted
     * out: reported as a lost link is, in order. Nothing, for a transport that is no relay.
     */
    default void drop(int playerIndex) {
    }
}
