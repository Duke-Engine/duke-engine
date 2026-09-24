package uz.dukeengine.core.network;

import java.util.Set;
import java.util.SortedSet;

/**
 * How a peer finds the game again when the relay it talked through is gone — see {@link LockstepGate}: the next
 * living player in the order every peer agreed when the game began takes over relaying, and the others reconnect to
 * it. The reference's packet router fallback ({@code ConnectionManager}, {@code m_packetRouterFallback}).
 */
public interface RelayFallback {

    /**
     * The game again without {@code gone}: a transport to the new relay, or — where this peer is the next in the order
     * — the transport it now relays through; null where nobody is left to reach. Called off the game's thread, and it
     * may take a few seconds while the others find the new relay.
     *
     * @param stillIn every player the game still expects but {@code gone}, in the agreed order
     */
    Relay reconnect(int gone, SortedSet<Integer> stillIn);

    /**
     * The game found again.
     *
     * @param transport the way to it from now on
     * @param relaying  whether this peer is the new relay
     * @param reached   for a new relay, the peers that reconnected to it; every other it expected is gone too
     */
    record Relay(Transport transport, boolean relaying, Set<Integer> reached) {

        public Relay {
            reached = reached == null ? Set.of() : Set.copyOf(reached);
        }
    }
}
