package uz.duke.core.network;

import java.util.function.Consumer;

/**
 * Carries {@link CommandPacket}s between peers for a lock-step game.
 *
 * <p>A {@link LockstepDriver} sends its local commands via {@link #broadcast}
 * (which also echoes them back locally, so the local scheduler hears its own
 * player) and is fed remote commands through a listener registered with
 * {@link #subscribe}. The driver and scheduler are single-threaded, so a
 * networked transport must deliver to listeners on the game thread — see
 * {@link SocketTransport#pump()}.
 */
public interface Transport {

    /** Send a packet to all peers, including a local echo. */
    void broadcast(CommandPacket packet);

    /** Register a listener invoked for every packet that arrives (local or remote). */
    void subscribe(Consumer<CommandPacket> listener);
}
