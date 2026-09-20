package uz.dukeengine.core.network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * A {@link Transport} over a single TCP connection — the guest's end of a
 * hosted game, where the one connection is the host and the host passes
 * everything on to everyone else.
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

    private final Socket socket;
    private final PacketCodec codec;
    private final BufferedWriter out;
    private final BufferedReader in;
    private final CopyOnWriteArrayList<Consumer<NetMessage>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<IntConsumer> lostListeners = new CopyOnWriteArrayList<>();

    /** Arrivals awaiting the game thread; a null element marks the end of the link. */
    private final ConcurrentLinkedQueue<NetMessage> inbox = new ConcurrentLinkedQueue<>();
    private volatile boolean linkEnded;
    private volatile boolean linkEndReported;
    private volatile boolean running = true;

    private SocketTransport(Socket socket, PacketCodec codec, int peerIndex) throws IOException {
        this.socket = socket;
        this.codec = codec;
        this.peerIndex = peerIndex;
        this.socket.setTcpNoDelay(true);
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        var reader = new Thread(this::readLoop, "lockstep-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Open a connection to a listening peer. */
    public static SocketTransport connect(String host, int port, PacketCodec codec) throws IOException {
        return new SocketTransport(new Socket(host, port), codec, 0);
    }

    /** Accept one incoming connection on an already-bound server socket. */
    public static SocketTransport accept(ServerSocket server, PacketCodec codec) throws IOException {
        return new SocketTransport(server.accept(), codec, 0);
    }

    /**
     * Wrap an already-connected socket — for callers that run their own
     * handshake on the raw streams before switching to lock-step messages.
     *
     * @param peerIndex the player on the other end, reported if the link drops
     */
    public static SocketTransport wrap(Socket socket, PacketCodec codec, int peerIndex) throws IOException {
        return new SocketTransport(socket, codec, peerIndex);
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

    private void write(NetMessage message) {
        try {
            synchronized (out) {
                out.write(NetFraming.encode(message, codec));
                out.write('\n');
                out.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to send " + message, e);
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
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                inbox.add(NetFraming.decode(line, codec));
            }
        } catch (IOException e) {
            // socket closed or errored; fall through and report the end of the link
        }
        linkEnded = true;
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing best-effort
        }
    }
}
