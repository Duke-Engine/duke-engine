package uz.duke.core.network;

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

/**
 * A real-network {@link Transport} over a single TCP connection (one peer),
 * encoding packets with a caller-supplied {@link PacketCodec}.
 *
 * <p>Threading is the crux: the simulation, scheduler and driver are
 * single-threaded, so incoming packets must not be handed to listeners from the
 * socket reader thread. Instead the reader thread only enqueues them; the game
 * thread calls {@link #pump()} once per loop to drain the queue and deliver on
 * its own thread. Local echoes from {@link #broadcast} are delivered inline
 * (already on the game thread).
 *
 * <p>This is the minimal two-player wiring. A lobby/host with N peers would fan
 * out to several connections, but the per-connection mechanics are identical.
 */
public final class SocketTransport implements Transport, AutoCloseable {

    private final Socket socket;
    private final PacketCodec codec;
    private final BufferedWriter out;
    private final BufferedReader in;
    private final Thread reader;
    private final CopyOnWriteArrayList<Consumer<CommandPacket>> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<CommandPacket> inbox = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;

    private SocketTransport(Socket socket, PacketCodec codec) throws IOException {
        this.socket = socket;
        this.codec = codec;
        this.socket.setTcpNoDelay(true);
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.reader = new Thread(this::readLoop, "lockstep-reader");
        this.reader.setDaemon(true);
        this.reader.start();
    }

    /** Open a connection to a listening peer. */
    public static SocketTransport connect(String host, int port, PacketCodec codec) throws IOException {
        return new SocketTransport(new Socket(host, port), codec);
    }

    /** Accept one incoming connection on an already-bound server socket. */
    public static SocketTransport accept(ServerSocket server, PacketCodec codec) throws IOException {
        return new SocketTransport(server.accept(), codec);
    }

    /**
     * Wrap an already-connected socket — for callers that run their own
     * handshake on the raw streams before switching to lock-step packets.
     */
    public static SocketTransport wrap(Socket socket, PacketCodec codec) throws IOException {
        return new SocketTransport(socket, codec);
    }

    @Override
    public void subscribe(Consumer<CommandPacket> listener) {
        listeners.add(listener);
    }

    @Override
    public void broadcast(CommandPacket packet) {
        deliver(packet); // local echo, on the game thread
        try {
            synchronized (out) {
                out.write(codec.encode(packet));
                out.write('\n');
                out.flush();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to send command packet", e);
        }
    }

    /** Deliver all packets received from the peer since the last call, on this thread. */
    public void pump() {
        CommandPacket packet;
        while ((packet = inbox.poll()) != null) {
            deliver(packet);
        }
    }

    private void deliver(CommandPacket packet) {
        for (var listener : listeners) {
            listener.accept(packet);
        }
    }

    private void readLoop() {
        try {
            String line;
            while (running && (line = in.readLine()) != null) {
                inbox.add(codec.decode(line));
            }
        } catch (IOException e) {
            // socket closed or errored; reader simply stops
        }
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
