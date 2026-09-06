package uz.duke.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * An in-process {@link Transport} that fans every broadcast out to all
 * subscribers immediately on the calling thread.
 *
 * <p>For single-machine games — hotseat, skirmish-vs-AI, or replays — where all
 * peers share one process. Multiple {@link LockstepDriver}s share one instance;
 * a packet broadcast by any of them reaches every subscriber (including the
 * sender). No threads, fully deterministic.
 */
public final class LoopbackTransport implements Transport {

    private final List<Consumer<CommandPacket>> listeners = new ArrayList<>();

    @Override
    public void subscribe(Consumer<CommandPacket> listener) {
        listeners.add(listener);
    }

    @Override
    public void broadcast(CommandPacket packet) {
        for (var listener : listeners) {
            listener.accept(packet);
        }
    }
}
