package uz.dukeengine.core.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * A {@link Transport} over a single connection — the guest's end of a
 * hosted game, where the one connection is the host and the host passes
 * everything on to everyone else: a TCP socket, or a channel of lines the game
 * opened itself ({@link LineChannel}).
 *
 * <p>Threading is the crux: the simulation is single-threaded, so incoming
 * messages must not be handed to listeners from the socket reader thread. The
 * reader only enqueues; the game thread calls {@link #pump()} to deliver them on
 * its own thread. The end of the connection is enqueued too, behind the last
 * message, so a peer's final packet can never be seen after its disconnection.
 *
 * <p>Local echoes from {@link #send} are delivered inline — they are already on
 * the game thread.
 */
public final class SocketTransport implements Transport, AutoCloseable {

    /** The index reported when this link drops; a guest's only link is the host. */
    private final int peerIndex;

    private final LineChannel channel;
    private final PacketCodec codec;
    private final CopyOnWriteArrayList<Consumer<NetMessage>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<IntConsumer> lostListeners = new CopyOnWriteArrayList<>();

    /** Arrivals awaiting the game thread; a null element marks the end of the link. */
    private final ConcurrentLinkedQueue<NetMessage> inbox = new ConcurrentLinkedQueue<>();
    private volatile boolean linkEnded;
    private volatile boolean linkEndReported;
    private volatile boolean running = true;

    private SocketTransport(LineChannel channel, PacketCodec codec, int peerIndex) {
        this.channel = channel;
        this.codec = codec;
        this.peerIndex = peerIndex;
        var reader = new Thread(this::readLoop, "lockstep-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Open a connection to a listening peer. */
    public static SocketTransport connect(String host, int port, PacketCodec codec) throws IOException {
        return new SocketTransport(LineChannel.over(new Socket(host, port)), codec, 0);
    }

    /** Accept one incoming connection on an already-bound server socket. */
    public static SocketTransport accept(ServerSocket server, PacketCodec codec) throws IOException {
        return new SocketTransport(LineChannel.over(server.accept()), codec, 0);
    }

    /**
     * Wrap an already-connected socket — for callers that run their own
     * handshake on the raw streams before switching to lock-step messages.
     *
     * @param peerIndex the player on the other end, reported if the link drops
     */
    public static SocketTransport wrap(Socket socket, PacketCodec codec, int peerIndex) throws IOException {
        return new SocketTransport(LineChannel.over(socket), codec, peerIndex);
    }

    /**
     * Over a channel of lines the game opened itself — a WebSocket through a gateway — its handshake already run on
     * it: the same lock-step, a closed channel the same lost link as a closed socket.
     *
     * @param peerIndex the player on the other end, reported if the link drops
     */
    public static SocketTransport over(LineChannel channel, PacketCodec codec, int peerIndex) {
        return new SocketTransport(channel, codec, peerIndex);
    }

    @Override
    public void subscribe(Consumer<NetMessage> listener) {
        listeners.add(listener);
    }

    @Override
    public void onLinkLost(IntConsumer listener) {
        lostListeners.add(listener);
    }

    @Override
    public void send(NetMessage message) {
        deliver(message); // local echo, on the game thread
        write(message);
    }

    /**
     * A message on its way over the link. A send that finds the link gone — the peer's machine went before the reader
     * saw it close — is the same news as a read that ends: reported at the next pump, after whatever arrived before
     * it, to the lost-link listeners, and nothing is thrown out of the frame; as the reference leaves a failed send to
     * its disconnect logic ({@code Transport::doSend}). Any other failure keeps its error.
     */
    private void write(NetMessage message) {
        if (linkEnded) {
            return; // gone: said at the next pump
        }
        if (!channel.send(NetFraming.encode(message, codec))) {
            linkEnded = true;
        }
    }

    @Override
    public void pump() {
        NetMessage message;
        while ((message = inbox.poll()) != null) {
            deliver(message);
        }
        if (linkEnded && !linkEndReported) {
            linkEndReported = true; // reported once, and only after the last message
            for (var listener : lostListeners) {
                listener.accept(peerIndex);
            }
        }
    }

    private void deliver(NetMessage message) {
        for (var listener : listeners) {
            listener.accept(message);
        }
    }

    private void readLoop() {
        String line;
        while (running && (line = channel.receive()) != null) {
            inbox.add(NetFraming.decode(line, codec));
        }
        linkEnded = true; // closed or errored: the end of the link, reported after its last message
    }

    @Override
    public void close() {
        running = false;
        channel.close();
    }
}
