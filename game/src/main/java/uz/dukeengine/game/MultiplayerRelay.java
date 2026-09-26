package uz.dukeengine.game;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import uz.dukeengine.core.network.CommandPacket;
import uz.dukeengine.core.network.Desync;
import uz.dukeengine.core.network.FrameChecksum;
import uz.dukeengine.core.network.HostTransport;
import uz.dukeengine.core.network.LineChannel;
import uz.dukeengine.core.network.LockstepGate;
import uz.dukeengine.core.network.NetMessage;
import uz.dukeengine.core.network.PeerLeft;
import uz.dukeengine.core.network.SessionHalted;
import uz.dukeengine.rts.network.CommandCodec;

/**
 * A relay that holds no seat — a server beside the players' lobby that relays a game it does not play, where the
 * reference's packet router is always a player. It takes on every player's channel, seats 1 to the player count all
 * guests, runs the handshake as a host does ({@link MultiplayerSession#host(MultiplayerSession.Guests, int, String,
 * java.util.function.IntConsumer)}), relays every message as {@link HostTransport} does, and makes the host's decisions
 * with no simulation of its own: a player whose link closes, who goes quiet past a limit, or whom the server takes out
 * leaves from a frame no peer can have run — past every packet of his it relayed — told to every machine as one {@link
 * PeerLeft}; a join after the start is refused; worlds that disagree stop the game ({@link SessionHalted}). It hears
 * every message it relays, in order, each with its frame — the game's command stream for a replay, the checksums the
 * peers report — and says when the game is over for it: every seat has left.
 *
 * <p>Driven by the server: {@link #admit} for each channel as it arrives, off the relay's own thread if it likes, then
 * {@link #pump} in a loop until {@link #isOver}.
 */
public final class MultiplayerRelay implements AutoCloseable {

    private static final String REFUSED = "DUKE-REFUSED";

    private final int playerCount;
    private final String scenarioSpec;
    private final HostTransport transport = new HostTransport(CommandCodec.INSTANCE);
    private final Map<Integer, LineChannel> waiting = new TreeMap<>();
    private final List<Consumer<NetMessage>> heard = new CopyOnWriteArrayList<>();
    private final List<Consumer<Desync>> desyncListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> overListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Integer> lostLinks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Integer> takenOut = new ConcurrentLinkedQueue<>();
    /** The highest frame of each seat's packets relayed: no machine can have run a frame past it without him. */
    private final Map<Integer, Integer> highestHeld = new HashMap<>();
    /** When each seat was last heard from, on the relay's clock. */
    private final Map<Integer, Long> lastHeard = new HashMap<>();
    /** The checksums each seat reported, by frame and then seat. */
    private final TreeMap<Integer, Map<Integer, Long>> checksums = new TreeMap<>();
    private final Set<Integer> gone = new TreeSet<>();
    private volatile boolean started;
    private long silenceLimitMillis;
    private Desync desync;
    private volatile boolean over;

    /**
     * @param playerCount  the seats, 1 to this, every one a guest's
     * @param scenarioSpec the host's settings, handed to every guest whole
     */
    public MultiplayerRelay(int playerCount, String scenarioSpec) {
        if (playerCount < 2) {
            throw new IllegalArgumentException("a network game needs at least two players");
        }
        this.playerCount = playerCount;
        this.scenarioSpec = scenarioSpec == null ? "" : scenarioSpec;
        transport.subscribe(this::hear);
        transport.onLinkLost(lostLinks::add);
    }

    /**
     * Take on a player's channel: its handshake run, the next seat given it, and the game started once every seat is
     * filled — or refused, and its channel closed, once the game has started or when it says no join.
     *
     * @return the seat given, or -1 where it was refused
     */
    public synchronized int admit(LineChannel channel) throws IOException {
        var hello = channel.receive();
        if (started || hello == null || !hello.startsWith("DUKE-JOIN")) {
            channel.send(REFUSED + (started ? " started" : ""));
            channel.close();
            return -1;
        }
        int seat = waiting.size() + 1;
        channel.send(MultiplayerSession.welcome(seat, playerCount, scenarioSpec));
        waiting.put(seat, channel);
        if (waiting.size() == playerCount) {
            for (var seated : waiting.values()) {
                seated.send("DUKE-PEERS"); // nobody listens anywhere: the relay is the only way to the game
                seated.send("DUKE-START");
            }
            long now = System.currentTimeMillis();
            waiting.forEach((one, way) -> {
                lastHeard.put(one, now);
                transport.addGuest(one, way);
            });
            started = true;
        }
        return seat;
    }

    /** Whether every seat is filled and the game under way. */
    public boolean isStarted() {
        return started;
    }

    /**
     * Take out a player who has said nothing for {@code limit} — the reference's 60 s — as one whose link closed is.
     * {@code Duration.ZERO}, the default, waits for ever.
     */
    public void setSilenceLimit(java.time.Duration limit) {
        this.silenceLimitMillis = limit == null ? 0L : Math.max(0L, limit.toMillis());
    }

    /** Take {@code seat} out at the next pump, on the server's word, as one whose link closed is. From any thread. */
    public void takeOut(int seat) {
        takenOut.add(seat);
    }

    /** Told every message the relay relays or says, in order — each with its frame where it has one. */
    public void onHeard(Consumer<NetMessage> listener) {
        heard.add(listener);
    }

    /** Told once, the first time two seats report different worlds for one frame; the game is stopped. */
    public void onDesync(Consumer<Desync> listener) {
        desyncListeners.add(listener);
    }

    /** Told once, when the game is over for the relay: every seat has left. */
    public void onOver(Runnable listener) {
        overListeners.add(listener);
    }

    /** The first disagreement it saw, or null. */
    public Desync getDesync() {
        return desync;
    }

    /** Whether every seat has left. */
    public boolean isOver() {
        return over;
    }

    /** The relay's step: what arrived relayed and heard, then its decisions — who leaves, from which frame. */
    public void pump() {
        if (!started || over) {
            return;
        }
        transport.pump();
        long now = System.currentTimeMillis();
        if (silenceLimitMillis > 0) {
            for (int seat = 1; seat <= playerCount; seat++) {
                if (!gone.contains(seat) && now - lastHeard.getOrDefault(seat, now) > silenceLimitMillis) {
                    takenOut.add(seat);
                }
            }
        }
        Integer seat;
        while ((seat = takenOut.poll()) != null) {
            if (leaves(seat)) {
                transport.drop(seat); // told first, while its link is open; then let go
            }
        }
        while ((seat = lostLinks.poll()) != null) {
            leaves(seat);
        }
        if (gone.size() == playerCount) {
            over = true;
            overListeners.forEach(Runnable::run);
        }
    }

    /**
     * {@code seat} leaves: from the frame after the last of his packets it relayed, which no machine can have run
     * without him, told to every machine once. Whether it was news.
     */
    private boolean leaves(int seat) {
        if (seat < 1 || seat > playerCount || !gone.add(seat)) {
            return false;
        }
        transport.send(new PeerLeft(seat, highestHeld.getOrDefault(seat, -1) + 1));
        return true;
    }

    /** A message relayed, or the relay's own: kept track of, then told. */
    private void hear(NetMessage message) {
        switch (message) {
            case CommandPacket packet -> {
                highestHeld.merge(packet.playerIndex(), packet.frame(), Math::max);
                lastHeard.put(packet.playerIndex(), System.currentTimeMillis());
            }
            case FrameChecksum reported -> compare(reported);
            default -> {
            }
        }
        for (var listener : heard) {
            listener.accept(message);
        }
    }

    /** A seat's checksum for a frame, against the others' for it: the first disagreement stops the game. */
    private void compare(FrameChecksum reported) {
        var said = checksums.computeIfAbsent(reported.frame(), frame -> new HashMap<>());
        said.put(reported.playerIndex(), reported.checksum());
        checksums.headMap(reported.frame() - LockstepGate.CHECKSUM_INTERVAL * 4).clear();
        if (desync != null) {
            return;
        }
        for (var other : new TreeMap<>(said).entrySet()) {
            if (other.getValue() != reported.checksum()) {
                desync = new Desync(reported.frame(), other.getKey(), other.getValue(), reported.playerIndex(),
                        reported.checksum());
                transport.send(new SessionHalted(reported.frame(), reported.playerIndex(), other.getValue(),
                        reported.checksum()));
                desyncListeners.forEach(listener -> listener.accept(desync));
                return;
            }
        }
    }

    @Override
    public synchronized void close() {
        transport.close();
        new ArrayList<>(waiting.values()).forEach(LineChannel::close);
    }
}
