package uz.dukeengine.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * An in-process {@link Transport} that hands every message to all subscribers
 * immediately, on the calling thread.
 *
 * <p>For peers that share one process — a hotseat game, an AI match, a replay, or
 * a test of several {@link LockstepGate}s at once. No sockets, no threads, and
 * nothing to {@link #pump()}: fully deterministic, which is what makes lock-step
 * testable without a network.
 */
public final class LoopbackTransport implements Transport {

    private final List<Consumer<NetMessage>> listeners = new ArrayList<>();

    @Override
    public void subscribe(Consumer<NetMessage> listener) {
        listeners.add(listener);
    }

    @Override
    public void send(NetMessage message) {
        for (var listener : List.copyOf(listeners)) {
            listener.accept(message);
        }
    }
}
