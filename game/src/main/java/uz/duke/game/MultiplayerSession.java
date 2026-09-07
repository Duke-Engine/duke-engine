package uz.duke.game;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import uz.duke.core.network.HostTransport;
import uz.duke.core.network.LockstepGate;
import uz.duke.core.network.LockstepScheduler;
import uz.duke.core.network.SocketTransport;
import uz.duke.core.network.Transport;
import uz.duke.rts.message.GameMessage;
import uz.duke.rts.network.CommandCodec;

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
 * guest → host   DUKE-JOIN
 * host  → guest  DUKE-WELCOME &lt;yourIndex&gt; &lt;playerCount&gt; &lt;scenario&gt;
 * host  → guest  DUKE-START                (once everyone has arrived)
 * </pre>
 * All machines must be running the same game definition.
 */
public final class MultiplayerSession implements AutoCloseable {

    public static final int DEFAULT_PORT = 7777;

    /** The host is always the first player; guests are numbered after it. */
    public static final int HOST_PLAYER_INDEX = 1;

    /** Input latency in logic frames (3 = 100ms at 30Hz) that hides network lag. */
    static final int FRAME_DELAY = 3;

    private final Transport transport;
    private final LockstepGate gate;
    private final int localPlayerIndex;
    private final int playerCount;
    private String scenarioSpec = "";

    private MultiplayerSession(Transport transport, int localPlayerIndex, int playerCount, boolean host) {
        this.transport = transport;
        this.localPlayerIndex = localPlayerIndex;
        this.playerCount = playerCount;
        var scheduler = new LockstepScheduler(
                IntStream.rangeClosed(1, playerCount).boxed().toList());
        scheduler.init();
        this.gate = new LockstepGate(scheduler, transport, localPlayerIndex, FRAME_DELAY, host);
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
        try {
            for (int index = HOST_PLAYER_INDEX + 1; index <= playerCount; index++) {
                var socket = server.accept();
                var hello = readLine(socket);
                if (hello == null || !hello.startsWith("DUKE-JOIN")) {
                    socket.close();
                    throw new IOException("unexpected handshake from guest: " + hello);
                }
                write(socket, "DUKE-WELCOME " + index + " " + playerCount + " "
                        + (spec.isBlank() ? "-" : spec));
                guests.put(index, socket);
                if (onGuestJoined != null) {
                    onGuestJoined.accept(guests.size());
                }
            }
            for (var socket : guests.values()) {
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
        var session = new MultiplayerSession(transport, HOST_PLAYER_INDEX, playerCount, true);
        session.scenarioSpec = spec;
        return session;
    }

    /** Join a hosted game at {@code host:port}. Blocks until the host starts it. */
    public static MultiplayerSession join(String host, int port) throws IOException {
        var socket = new Socket(host, port);
        write(socket, "DUKE-JOIN");
        var welcome = readLine(socket);
        if (welcome == null || !welcome.startsWith("DUKE-WELCOME")) {
            socket.close();
            throw new IOException("host refused: " + welcome);
        }
        var parts = welcome.trim().split("\\s+");
        int assigned = Integer.parseInt(parts[1]);
        int playerCount = Integer.parseInt(parts[2]);

        var go = readLine(socket); // blocks until every other guest has arrived
        if (go == null || !go.startsWith("DUKE-START")) {
            socket.close();
            throw new IOException("host closed the lobby: " + go);
        }

        var session = new MultiplayerSession(
                SocketTransport.wrap(socket, CommandCodec.INSTANCE, HOST_PLAYER_INDEX),
                assigned, playerCount, false);
        session.scenarioSpec = parts.length > 3 && !parts[3].equals("-") ? parts[3] : "";
        return session;
    }

    /** The host's map/faction choice, exactly as sent in the welcome. */
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

    /** Told, by player index, when a player drops out of the game. */
    public void onPlayerLeft(IntConsumer listener) {
        gate.onPlayerLeft(listener);
    }

    /** Told once, when this machine is cut off from the game for good. */
    public void onConnectionLost(Runnable listener) {
        gate.onConnectionLost(listener);
    }

    /** Told once, the first time this machine's world disagrees with another's. */
    public void onDesync(java.util.function.Consumer<uz.duke.core.network.Desync> listener) {
        gate.onDesync(listener);
    }

    /** The first disagreement found, or {@code null} while every peer still agrees. */
    public uz.duke.core.network.Desync getDesync() {
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
        if (transport instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }
}
