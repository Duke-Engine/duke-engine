package uz.dukeengine.game;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import uz.dukeengine.core.network.HostTransport;
import uz.dukeengine.core.network.LockstepGate;
import uz.dukeengine.core.network.LockstepScheduler;
import uz.dukeengine.core.network.RelayFallback;
import uz.dukeengine.core.network.SocketTransport;
import uz.dukeengine.core.network.Transport;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.network.CommandCodec;

/**
 * A LAN game of two or more players over TCP: one machine hosts, the rest join.
 *
 * <p>Classic RTS networking — no world state ever crosses the wire, only each
 * player's commands, applied by every deterministic simulation at the same frame
 * numbers. Guests connect only to the host, which passes every message on to
 * everyone else; see {@link HostTransport} for why that ordering matters.
 *
 * <p>This class owns the lobby handshake and the wiring to {@link DukeGame}; the
 * lock-step rules themselves live in {@link LockstepGate}.
 *
 * <p>The handshake runs on the raw socket, before lock-step messages start
 * flowing on it:
 * <pre>
 * guest → host   DUKE-JOIN &lt;the port it listens on, should it have to relay&gt;
 * host  → guest  DUKE-WELCOME &lt;yourIndex&gt; &lt;playerCount&gt; &lt;scenario, "=" and URL-encoded, or "-"&gt;
 * host  → guest  DUKE-PEERS &lt;index=address:port …&gt;   (once everyone has arrived)
 * host  → guest  DUKE-START
 * </pre>
 * All machines must be running the same game definition.
 *
 * <p><b>The host leaving is not the end.</b> Every guest listens from the start, and the host tells each where the
 * others listen: the fallback order is the players by seat, agreed before the first frame. When the host's link drops,
 * the next living player in that order takes over relaying and the others reconnect to it ({@code DUKE-REJOIN
 * &lt;index&gt;}, answered {@code DUKE-RELAYING}) — the reference's packet router fallback; see {@link LockstepGate}
 * for how no frame is lost or doubled on the way. Whoever cannot be reached has left, as each machine sees it: one cut
 * off from everybody plays on alone, as in the reference.
 */
public final class MultiplayerSession implements AutoCloseable {

    public static final int DEFAULT_PORT = 7777;

    /** The host is always the first player; guests are numbered after it. */
    public static final int HOST_PLAYER_INDEX = 1;

    /** Input latency in logic frames (3 = 100ms at 30Hz) that hides network lag. */
    static final int FRAME_DELAY = 3;

    /** How long the peers look for one another after the relay has gone before the game is over. */
    private static final int REJOIN_MILLIS = 5000;

    private final Transport transport;
    private final LockstepGate gate;
    private final int localPlayerIndex;
    private final int playerCount;
    /** Where each guest listens, should it have to relay: the fallback order is their seats. */
    private final Map<Integer, InetSocketAddress> listeners;
    /** Where this machine listens, should it have to relay; null for the host, which already does. */
    private final ServerSocket listening;
    /** The ways to the game it has found since the first, closed with it. */
    private final List<Transport> later = new CopyOnWriteArrayList<>();
    private String scenarioSpec = "";

    private MultiplayerSession(Transport transport, int localPlayerIndex, int playerCount, boolean host,
            Map<Integer, InetSocketAddress> listeners, ServerSocket listening) {
        this.transport = transport;
        this.localPlayerIndex = localPlayerIndex;
        this.playerCount = playerCount;
        this.listeners = Map.copyOf(listeners);
        this.listening = listening;
        var scheduler = new LockstepScheduler(
                IntStream.rangeClosed(1, playerCount).boxed().toList());
        scheduler.init();
        this.gate = new LockstepGate(scheduler, transport, localPlayerIndex, FRAME_DELAY, host, this::reconnect);
    }

    /**
     * The game found again without {@code gone}: this machine takes over relaying if it is the next in the seats'
     * order, or reconnects to whichever of the players before it answers first.
     */
    private RelayFallback.Relay reconnect(int gone, SortedSet<Integer> stillIn) {
        for (int next : stillIn) {
            if (next == localPlayerIndex) {
                return listening == null ? null : relayFor(stillIn);
            }
            var address = listeners.get(next);
            var socket = address == null ? null : connect(address);
            if (socket == null) {
                continue; // gone too, or never listening: the next in the order
            }
            try {
                write(socket, "DUKE-REJOIN " + localPlayerIndex);
                // Answered only by a machine that is relaying: one whose own relay is still there never accepts, and
                // a machine cut off alone must not wait on it for good.
                socket.setSoTimeout(REJOIN_MILLIS);
                var answer = readLine(socket);
                socket.setSoTimeout(0);
                if (answer == null || !answer.startsWith("DUKE-RELAYING")) {
                    closeQuietly(socket);
                    continue;
                }
                var way = SocketTransport.wrap(socket, CommandCodec.INSTANCE, next);
                later.add(way);
                return new RelayFallback.Relay(way, false, Set.of());
            } catch (IOException e) {
                closeQuietly(socket);
            }
        }
        return null;
    }

    /** This machine the relay now: every other player still in, taken on as it reconnects, for a while. */
    private RelayFallback.Relay relayFor(SortedSet<Integer> stillIn) {
        var relay = new HostTransport(CommandCodec.INSTANCE);
        later.add(relay);
        var reached = new TreeSet<Integer>();
        long until = System.currentTimeMillis() + REJOIN_MILLIS;
        while (reached.size() < stillIn.size() - 1 && System.currentTimeMillis() < until) {
            try {
                listening.setSoTimeout((int) Math.max(1, until - System.currentTimeMillis()));
                var socket = listening.accept();
                socket.setSoTimeout(REJOIN_MILLIS);
                var hello = readLine(socket);
                socket.setSoTimeout(0);
                int index = hello == null || !hello.startsWith("DUKE-REJOIN") ? -1
                        : Integer.parseInt(hello.trim().split("\\s+")[1]);
                if (!stillIn.contains(index) || index == localPlayerIndex || reached.contains(index)) {
                    closeQuietly(socket);
                    continue;
                }
                write(socket, "DUKE-RELAYING");
                relay.addGuest(index, socket);
                reached.add(index);
            } catch (IOException | RuntimeException e) {
                // timed out, or a stranger: whoever has not come by now is gone too
            }
        }
        return new RelayFallback.Relay(relay, true, reached);
    }

    /** A connection to where a player listens, tried for a while; null where nobody answers. */
    private static Socket connect(InetSocketAddress address) {
        long until = System.currentTimeMillis() + REJOIN_MILLIS;
        while (System.currentTimeMillis() < until) {
            var socket = new Socket();
            try {
                socket.connect(address, 1000);
                return socket;
            } catch (IOException notYet) {
                closeQuietly(socket);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Host a game and wait for {@code playerCount - 1} guests.
     *
     * <p>Guests are welcomed as they arrive but nobody starts until everyone is
     * present, so no peer is left stalling on a player who has not connected yet.
     *
     * @param onGuestJoined told how many are in so far, for a lobby to display
     */
    public static MultiplayerSession host(ServerSocket server, int playerCount,
            String scenarioSpec, IntConsumer onGuestJoined) throws IOException {
        if (playerCount < 2) {
            throw new IllegalArgumentException("a network game needs at least two players");
        }
        var spec = scenarioSpec == null ? "" : scenarioSpec;
        Map<Integer, Socket> guests = new LinkedHashMap<>();
        Map<Integer, InetSocketAddress> listening = new LinkedHashMap<>();
        try {
            for (int index = HOST_PLAYER_INDEX + 1; index <= playerCount; index++) {
                var socket = server.accept();
                var hello = readLine(socket);
                if (hello == null || !hello.startsWith("DUKE-JOIN")) {
                    socket.close();
                    throw new IOException("unexpected handshake from guest: " + hello);
                }
                var said = hello.trim().split("\\s+");
                if (said.length > 1) {
                    listening.put(index, new InetSocketAddress(socket.getInetAddress(),
                            Integer.parseInt(said[1])));
                }
                write(socket, "DUKE-WELCOME " + index + " " + playerCount + " "
                        + (spec.isEmpty() ? "-" : "=" + java.net.URLEncoder.encode(spec, StandardCharsets.UTF_8)));
                guests.put(index, socket);
                if (onGuestJoined != null) {
                    onGuestJoined.accept(guests.size());
                }
            }
            var peers = new StringBuilder("DUKE-PEERS");
            listening.forEach((index, where) -> peers.append(' ').append(index).append('=')
                    .append(where.getAddress().getHostAddress()).append(':').append(where.getPort()));
            for (var socket : guests.values()) {
                write(socket, peers.toString());
                write(socket, "DUKE-START");
            }
        } catch (IOException | RuntimeException e) {
            for (var socket : guests.values()) {
                closeQuietly(socket);
            }
            throw e;
        }

        var transport = new HostTransport(CommandCodec.INSTANCE);
        for (var guest : guests.entrySet()) {
            transport.addGuest(guest.getKey(), guest.getValue());
        }
        var session = new MultiplayerSession(transport, HOST_PLAYER_INDEX, playerCount, true, listening, null);
        session.scenarioSpec = spec;
        return session;
    }

    /** Join a hosted game at {@code host:port}. Blocks until the host starts it. */
    public static MultiplayerSession join(String host, int port) throws IOException {
        var listening = new ServerSocket(0); // should it ever have to relay
        var socket = new Socket(host, port);
        write(socket, "DUKE-JOIN " + listening.getLocalPort());
        var welcome = readLine(socket);
        if (welcome == null || !welcome.startsWith("DUKE-WELCOME")) {
            socket.close();
            listening.close();
            throw new IOException("host refused: " + welcome);
        }
        var parts = welcome.trim().split("\\s+");
        int assigned = Integer.parseInt(parts[1]);
        int playerCount = Integer.parseInt(parts[2]);

        Map<Integer, InetSocketAddress> peers = new LinkedHashMap<>();
        var go = readLine(socket); // blocks until every other guest has arrived
        if (go != null && go.startsWith("DUKE-PEERS")) {
            for (var one : go.trim().split("\\s+")) {
                int equals = one.indexOf('=');
                int colon = one.lastIndexOf(':');
                if (equals > 0 && colon > equals) {
                    peers.put(Integer.parseInt(one.substring(0, equals)), new InetSocketAddress(
                            one.substring(equals + 1, colon), Integer.parseInt(one.substring(colon + 1))));
                }
            }
            go = readLine(socket);
        }
        if (go == null || !go.startsWith("DUKE-START")) {
            socket.close();
            listening.close();
            throw new IOException("host closed the lobby: " + go);
        }

        var session = new MultiplayerSession(
                SocketTransport.wrap(socket, CommandCodec.INSTANCE, HOST_PLAYER_INDEX),
                assigned, playerCount, false, peers, listening);
        // Sent encoded, so a spec with spaces in it is still one word of the line; "=" is never an encoding's.
        session.scenarioSpec = parts.length > 3 && parts[3].startsWith("=")
                ? java.net.URLDecoder.decode(parts[3].substring(1), StandardCharsets.UTF_8) : "";
        return session;
    }

    /**
     * The host's settings for the match, exactly as the host gave them — spaces, line breaks and all: the choice
     * {@link DukeGame} makes of a map and factions, or whatever a game that runs its own lobby writes of its slots.
     */
    public String getScenarioSpec() {
        return scenarioSpec;
    }

    /** The engine player index this machine controls. */
    public int getLocalPlayerIndex() {
        return localPlayerIndex;
    }

    /** How many players the game started with. */
    public int getPlayerCount() {
        return playerCount;
    }

    /** True once this machine can no longer reach the game (for a guest: the host is gone). */
    public boolean isConnectionLost() {
        return gate.isConnectionLost();
    }

    /**
     * Whether the game can still advance, and if not, why — a peer waiting on a
     * slow player looks exactly like one whose game has ended.
     */
    public uz.dukeengine.core.network.SessionState getState() {
        return gate.getState();
    }

    /** True once the worlds have diverged and the game has been stopped. */
    public boolean isDesynced() {
        return gate.getState() == uz.dukeengine.core.network.SessionState.DESYNCED;
    }

    /** Told, by player index, how far another machine has got loading the match. */
    public void onPeerProgress(java.util.function.BiConsumer<Integer, Integer> listener) {
        gate.onPeerProgress(listener);
    }

    /**
     * Say how far this machine has got loading the match, and take in what the others have said — on the thread
     * doing the load, until the match starts stepping.
     */
    public void shareProgress(int percent) {
        gate.shareProgress(percent);
        gate.pumpWhileLoading();
    }

    /** Take in what the others have said, saying nothing new. */
    void listenWhileLoading() {
        gate.pumpWhileLoading();
    }

    /** Told every line said to this machine's player, its own included. */
    void onChat(java.util.function.Consumer<uz.dukeengine.core.network.ChatLine> listener) {
        gate.onChat(listener);
    }

    /** Say a line to these players and take in what has arrived — on the game's own thread. */
    void say(String text, java.util.Collection<Integer> to) {
        gate.say(text, to);
    }

    /** Take in what has arrived between frames — on the game's own thread. */
    void listen() {
        gate.pumpBetweenFrames();
    }

    /** Told, by player index, when a player drops out of the game. */
    public void onPlayerLeft(IntConsumer listener) {
        gate.onPlayerLeft(listener);
    }

    /** Told once, when this machine is cut off from the game for good. */
    public void onConnectionLost(Runnable listener) {
        gate.onConnectionLost(listener);
    }

    /** Told once, the first time this machine's world disagrees with another's. */
    public void onDesync(java.util.function.Consumer<uz.dukeengine.core.network.Desync> listener) {
        gate.onDesync(listener);
    }

    /** The first disagreement found, or {@code null} while every peer still agrees. */
    public uz.dukeengine.core.network.Desync getDesync() {
        return gate.getDesync();
    }

    /** Buffer a local command; it ships with the next frame submission. */
    void issueLocal(GameMessage command) {
        gate.issueLocal(command);
    }

    /**
     * The lock-step gate, called by the engine before every logic step.
     *
     * @return true if the frame may step; false to stall (waiting on a peer)
     */
    boolean beforeStep(RtsLogic logic) {
        return gate.beforeStep(logic);
    }

    private static void write(Socket socket, String line) throws IOException {
        var out = socket.getOutputStream();
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush(); // the stream is not closed: it goes on to carry lock-step messages
    }

    /**
     * Read one handshake line without reading a byte more.
     *
     * <p>A buffered reader would happily pull the first lock-step messages in
     * along with the handshake and then be thrown away, taking them with it. The
     * connection outlives the handshake, so the handshake must not touch anything
     * that is not addressed to it.
     */
    private static String readLine(Socket socket) throws IOException {
        var in = socket.getInputStream();
        var line = new StringBuilder();
        for (int b = in.read(); b != -1 && b != '\n'; b = in.read()) {
            if (b != '\r') {
                line.append((char) b);
            }
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // best-effort
        }
    }

    @Override
    public void close() {
        var ways = new ArrayList<Transport>(later);
        ways.add(transport);
        for (var way : ways) {
            if (way instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        }
        if (listening != null) {
            try {
                listening.close();
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }
}
