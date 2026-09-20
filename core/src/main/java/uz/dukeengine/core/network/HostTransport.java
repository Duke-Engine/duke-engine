package uz.dukeengine.core.network;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The host's end of a game: one connection per guest, and everything that
 * arrives on any of them is passed on to all the others.
 *
 * <p>Guests are not connected to each other — they only talk to the host, which
 * relays. That makes the host the single point where the message stream is put
 * in order, and that is the property the whole design rests on: every guest
 * receives exactly what the host received, in exactly the order the host saw it.
 * There is therefore one shared view of who said what, so when a peer drops
 * there is no doubt about which of its packets everybody had.
 *
 * <p>The price is that the host is special: its latency is nobody's problem but
 * everybody's advantage, and if it leaves the game ends. A full mesh trades that
 * for a connection between every pair and a port to open on every machine.
 */
public final class HostTransport implements Transport, AutoCloseable {

    /** One guest's connection. */
    private static final class Link {
        final int playerIndex;
        final Socket socket;
        final BufferedWriter out;
        volatile boolean open = true;

        Link(int playerIndex, Socket socket) throws IOException {
            this.playerIndex = playerIndex;
            this.socket = socket;
            socket.setTcpNoDelay(true);
            this.out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }
    }

    /** Something that came in from a guest: a message, or the end of its link. */
    private record Arrival(int playerIndex, NetMessage message) {
    }

    private final PacketCodec codec;
    private final Map<Integer, Link> links = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<NetMessage>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<IntConsumer> lostListeners = new CopyOnWriteArrayList<>();

    /** Ordered per link by TCP, and drained on the game thread. */
    private final ConcurrentLinkedQueue<Arrival> inbox = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;

    public HostTransport(PacketCodec codec) {
        this.codec = codec;
    }

    /**
     * Take on a guest that has already been through the lobby handshake and been
     * told which player it is.
     */
    public void addGuest(int playerIndex, Socket socket) throws IOException {
        var link = new Link(playerIndex, socket);
        links.put(playerIndex, link);
        var reader = new Thread(() -> readLoop(link), "lockstep-reader-" + playerIndex);
        reader.setDaemon(true);
        reader.start();
    }

    /** How many guests are still connected. */
    public int getGuestCount() {
        return links.size();
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
        for (var link : links.values()) {
            write(link, message);
        }
    }

    @Override
    public void pump() {
        Arrival arrival;
        while ((arrival = inbox.poll()) != null) {
            if (arrival.message() == null) {
                dropLink(arrival.playerIndex());
                continue;
            }
            // Relay before delivering locally: every guest ends up with the same
            // stream the host has, so the host's view is the shared view.
            relay(arrival);
            deliver(arrival.message());
        }
    }

    private void relay(Arrival arrival) {
        for (var link : links.values()) {
            if (link.playerIndex != arrival.playerIndex()) {
                write(link, arrival.message());
            }
        }
    }

    private void dropLink(int playerIndex) {
        var link = links.remove(playerIndex);
        if (link == null) {
            return; // already gone
        }
        link.open = false;
        closeQuietly(link.socket);
        for (var listener : lostListeners) {
            listener.accept(playerIndex);
        }
    }

    private void write(Link link, NetMessage message) {
        if (!link.open) {
            return;
        }
        try {
            synchronized (link.out) {
                link.out.write(NetFraming.encode(message, codec));
                link.out.write('\n');
                link.out.flush();
            }
        } catch (IOException e) {
            // A write failure is the same news as a read failure; queue it so it
            // is reported in order rather than in the middle of a send.
            link.open = false;
            inbox.add(new Arrival(link.playerIndex, null));
        }
    }

    private void deliver(NetMessage message) {
        for (var listener : listeners) {
            listener.accept(message);
        }
    }

    private void readLoop(Link link) {
        try (var in = new java.io.BufferedReader(
                new InputStreamReader(link.socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (running && link.open && (line = in.readLine()) != null) {
                inbox.add(new Arrival(link.playerIndex, NetFraming.decode(line, codec)));
            }
        } catch (IOException e) {
            // fall through: the end of the link is the news, and it is queued below
        }
        inbox.add(new Arrival(link.playerIndex, null));
    }

    @Override
    public void close() {
        running = false;
        for (var link : links.values()) {
            link.open = false;
            closeQuietly(link.socket);
        }
        links.clear();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing best-effort
        }
    }
}
