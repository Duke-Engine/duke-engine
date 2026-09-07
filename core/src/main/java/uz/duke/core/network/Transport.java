package uz.duke.core.network;

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
}
