package uz.duke.game;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import uz.duke.rts.message.GameMessage;
import uz.duke.core.network.CommandPacket;
import uz.duke.core.network.LockstepScheduler;
import uz.duke.core.network.SocketTransport;
import uz.duke.rts.network.CommandCodec;

/**
 * A two-player lock-step game over TCP — one machine hosts, the other joins.
 *
 * <p>Classic RTS networking: no world state ever crosses the wire, only each
 * player's commands, applied by both deterministic simulations at the same
 * frame numbers. This session is the glue between the engine's lock-step
 * primitives and {@link DukeGame}: local input is buffered and shipped
 * {@link #FRAME_DELAY} frames ahead; a frame may only simulate once both
 * players' commands for it have arrived ({@link #beforeStep}), so the two
 * worlds stay bit-identical (verifiable via {@code GameLogic.checksum()}).
 *
 * <p>The handshake runs on the raw socket before lock-step starts:
 * client sends {@code DUKE-JOIN}, host answers {@code DUKE-WELCOME <index>}.
 * The host is always engine player 1, the guest player 2 — both machines must
 * run the same game definition (same exported game or project).
 */
public final class MultiplayerSession implements AutoCloseable {

    public static final int DEFAULT_PORT = 7777;

    /** Input latency in logic frames (3 = 100ms at 30Hz) that hides network lag. */
    static final int FRAME_DELAY = 3;

    private final SocketTransport transport;
    private final LockstepScheduler scheduler;
    private final int localPlayerIndex;
    private String scenarioSpec = "";

    private final List<GameMessage> pending = new ArrayList<>(); // sim thread only
    private int nextSubmitFrame;
    private boolean primed;

    private MultiplayerSession(SocketTransport transport, int localPlayerIndex) {
        this.transport = transport;
        this.localPlayerIndex = localPlayerIndex;
        this.scheduler = new LockstepScheduler(List.of(1, 2));
        this.scheduler.init();
        transport.subscribe(packet ->
                scheduler.submit(packet.frame(), packet.playerIndex(), packet.commands()));
    }

    /**
     * Host a game: blocks until a guest connects. {@code scenarioSpec} (the
     * host's map/faction choice, URL-encoded) is sent in the welcome so both
     * machines assemble the identical match.
     */
    public static MultiplayerSession host(ServerSocket server, String scenarioSpec) throws IOException {
        var socket = server.accept();
        var out = writer(socket);
        var in = reader(socket);
        var hello = in.readLine();
        if (hello == null || !hello.startsWith("DUKE-JOIN")) {
            socket.close();
            throw new IOException("unexpected handshake from guest: " + hello);
        }
        out.write("DUKE-WELCOME 2 " + (scenarioSpec == null || scenarioSpec.isBlank() ? "-" : scenarioSpec) + "\n");
        out.flush();
        var session = new MultiplayerSession(SocketTransport.wrap(socket, CommandCodec.INSTANCE), 1);
        session.scenarioSpec = scenarioSpec == null ? "" : scenarioSpec;
        return session;
    }

    /** Join a hosted game at {@code host:port}. Blocks until welcomed. */
    public static MultiplayerSession join(String host, int port) throws IOException {
        var socket = new Socket(host, port);
        var out = writer(socket);
        var in = reader(socket);
        out.write("DUKE-JOIN\n");
        out.flush();
        var welcome = in.readLine();
        if (welcome == null || !welcome.startsWith("DUKE-WELCOME")) {
            socket.close();
            throw new IOException("host refused: " + welcome);
        }
        var parts = welcome.trim().split("\\s+");
        int assigned = Integer.parseInt(parts[1]);
        var session = new MultiplayerSession(SocketTransport.wrap(socket, CommandCodec.INSTANCE), assigned);
        session.scenarioSpec = parts.length > 2 && !parts[2].equals("-") ? parts[2] : "";
        return session;
    }

    /** The host's map/faction choice, exactly as sent in the welcome. */
    public String getScenarioSpec() {
        return scenarioSpec;
    }

    private static BufferedWriter writer(Socket socket) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    /** The engine player index this machine controls (host 1, guest 2). */
    public int getLocalPlayerIndex() {
        return localPlayerIndex;
    }

    /** Buffer a local command; it ships with the next frame submission. */
    void issueLocal(GameMessage command) {
        pending.add(command);
    }

    /**
     * Lock-step gate, called by the engine before every logic step. Ships this
     * peer's commands for the future frame, then reports whether {@code frame}
     * may simulate; when it may, the frame's commands (both players', in
     * deterministic order) are injected into the logic first.
     *
     * @return true if the frame may step; false to stall (waiting on the peer)
     */
    boolean beforeStep(RtsLogic logic) {
        if (!primed) {
            primed = true;
            for (int frame = 0; frame < FRAME_DELAY; frame++) {
                transport.broadcast(new CommandPacket(frame, localPlayerIndex, List.of()));
            }
            nextSubmitFrame = FRAME_DELAY;
        }

        transport.pump(); // deliver the peer's packets on the sim thread

        int frame = logic.getFrame();
        int target = frame + FRAME_DELAY;
        if (target >= nextSubmitFrame) {
            transport.broadcast(new CommandPacket(target, localPlayerIndex, List.copyOf(pending)));
            pending.clear();
            nextSubmitFrame = target + 1;
        }

        if (!scheduler.isFrameReady(frame)) {
            return false; // the peer's input hasn't arrived yet — stall, don't drift
        }
        for (var command : scheduler.takeCommands(frame)) {
            logic.issueCommand(command);
        }
        return true;
    }

    @Override
    public void close() {
        transport.close();
    }
}
