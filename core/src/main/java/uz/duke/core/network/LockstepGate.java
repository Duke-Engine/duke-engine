package uz.duke.core.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import uz.duke.core.GameLogic;
import uz.duke.core.message.Command;

/**
 * The gate a networked engine asks before every logic frame: <em>may this frame
 * run, and if so, what did everyone do on it?</em>
 *
 * <p>Each peer sends its commands {@link #getFrameDelay()} frames ahead of when
 * they execute, so there is time for them to arrive. A frame runs only once every
 * player still in the game has reported for it; otherwise the simulation
 * <b>stalls</b> rather than guessing. Every peer runs the same deterministic
 * simulation over the same commands at the same frame numbers, so their worlds
 * stay bit-identical — checkable with {@link GameLogic#checksum()}.
 *
 * <p>Stalling is the whole trick, and it is also the danger: a player who
 * vanishes would stall everyone forever. So a disconnection must become a fact
 * that every peer applies at the same frame, not something each notices for
 * itself. Only the host may declare it: it fills in the missing player's silence
 * up to a frame nobody has simulated yet, then announces {@link PeerLeft} from
 * that frame on. Guests never decide; they obey.
 *
 * <p>All of that rests on determinism actually holding, which nothing else
 * verifies. So every {@link #CHECKSUM_INTERVAL} frames the peers hash their
 * worlds and say the number out loud ({@link FrameChecksum}); a mismatch is
 * reported as a {@link Desync}. It fixes nothing — it turns a silent failure,
 * where players quietly stop sharing a game, into a loud one with a frame number.
 */
public final class LockstepGate {

    private final LockstepScheduler scheduler;
    private final Transport transport;
    private final int localPlayer;
    private final int frameDelay;
    private final boolean host;

    /**
     * How often the peers compare worlds, in frames. Once a second at 30Hz: a few
     * bytes per player, and a divergence is named within a second of happening —
     * close enough to the cause to be worth investigating.
     */
    public static final int CHECKSUM_INTERVAL = 30;

    /** Enough history to outlive the frames still in flight, and no more. */
    private static final int CHECKSUM_HISTORY = CHECKSUM_INTERVAL * 4;

    private final List<Command> pending = new ArrayList<>();
    private final List<IntConsumer> leftListeners = new ArrayList<>();
    private final List<Runnable> lostConnectionListeners = new ArrayList<>();
    private final List<Consumer<Desync>> desyncListeners = new ArrayList<>();
    private final List<Integer> lostLinks = new ArrayList<>(); // filled during pump, on the game thread

    /** This peer's own view of the world at each checked frame. */
    private final Map<Integer, Long> ownChecksums = new HashMap<>();

    /** Others' views, held until this peer reaches the frame and can compare. */
    private final Map<Integer, Map<Integer, Long>> reportedChecksums = new HashMap<>();

    private int nextSubmitFrame;
    private int lastCheckedFrame = -1;
    private boolean primed;
    private boolean connectionLost;
    private Desync desync;

    /**
     * @param localPlayer which player's input this peer supplies
     * @param frameDelay  how many frames ahead commands are sent; the input
     *                    latency players feel, and the lag the game can absorb
     * @param host        whether this peer is the one that decides who is still in
     */
    public LockstepGate(LockstepScheduler scheduler, Transport transport,
            int localPlayer, int frameDelay, boolean host) {
        if (frameDelay < 1) {
            throw new IllegalArgumentException("frameDelay must be >= 1");
        }
        this.scheduler = scheduler;
        this.transport = transport;
        this.localPlayer = localPlayer;
        this.frameDelay = frameDelay;
        this.host = host;
        this.nextSubmitFrame = frameDelay;

        transport.subscribe(this::receive);
        transport.onLinkLost(lostLinks::add);
    }

    public int getFrameDelay() {
        return frameDelay;
    }

    public int getLocalPlayer() {
        return localPlayer;
    }

    /**
     * True once this peer can no longer reach the game — for a guest, that the
     * host has gone. Nothing can advance after this; the game should say so
     * rather than sit there frozen.
     */
    public boolean isConnectionLost() {
        return connectionLost;
    }

    /** Told, by player index, when a player has been dropped from the game. */
    public void onPlayerLeft(IntConsumer listener) {
        leftListeners.add(listener);
    }

    /**
     * Told once, when this peer is cut off for good.
     *
     * <p>Worth saying out loud: from the outside, a peer that has lost the game
     * and a peer that is merely waiting for a slow player look exactly the same —
     * both are stopped. Silence would leave a player staring at a frozen screen.
     */
    public void onConnectionLost(Runnable listener) {
        lostConnectionListeners.add(listener);
    }

    /**
     * Told, once, the first time this peer's world disagrees with another's.
     *
     * <p>Once is deliberate: divergence compounds, so after the first mismatch
     * every later frame differs too and repeating it says nothing new. The first
     * report carries the frame that matters.
     */
    public void onDesync(Consumer<Desync> listener) {
        desyncListeners.add(listener);
    }

    /** The first disagreement found, or {@code null} while the peers still agree. */
    public Desync getDesync() {
        return desync;
    }

    /** Queue a command from local input; it ships with the next submission. */
    public void issueLocal(Command command) {
        pending.add(command);
    }

    /**
     * Advance the network side of one frame and decide whether the simulation may
     * step. When it may, that frame's commands — everyone's, in a deterministic
     * order — have already been handed to {@code logic}.
     *
     * @return false to stall: someone's input for this frame has not arrived
     */
    public boolean beforeStep(GameLogic logic) {
        if (!primed) {
            primed = true;
            // Nobody has any input yet, but the first frames still need everyone's
            // "nothing from me" before they can run.
            for (int frame = 0; frame < frameDelay; frame++) {
                transport.send(new CommandPacket(frame, localPlayer, List.of()));
            }
        }

        transport.pump(); // arrivals and disconnections, in order, on this thread
        int frame = logic.getFrame();
        handleLostLinks(frame);
        submitLocal(frame);
        compareWorlds(logic, frame);

        if (connectionLost || !scheduler.isFrameReady(frame)) {
            return false;
        }
        for (var command : scheduler.takeCommands(frame)) {
            logic.issueCommand(command);
        }
        return true;
    }

    private void submitLocal(int frame) {
        int target = frame + frameDelay;
        if (target < nextSubmitFrame) {
            return; // already sent for that frame
        }
        transport.send(new CommandPacket(target, localPlayer, List.copyOf(pending)));
        pending.clear();
        nextSubmitFrame = target + 1;
    }

    private void receive(NetMessage message) {
        switch (message) {
            case CommandPacket packet ->
                    scheduler.submit(packet.frame(), packet.playerIndex(), packet.commands());
            case PeerLeft left -> {
                scheduler.retirePlayer(left.playerIndex(), left.fromFrame());
                for (var listener : leftListeners) {
                    listener.accept(left.playerIndex());
                }
            }
            case FrameChecksum reported -> {
                if (reported.playerIndex() != localPlayer) { // our own send echoes back
                    reportedChecksums
                            .computeIfAbsent(reported.frame(), f -> new HashMap<>())
                            .put(reported.playerIndex(), reported.checksum());
                    check(reported.frame(), reported.playerIndex(), reported.checksum());
                }
            }
        }
    }

    /**
     * Say what this peer thinks the world is, and check it against what everyone
     * else has said.
     *
     * <p>The world is hashed <em>before</em> the frame runs, which is exactly the
     * state after the previous frame — a point every peer passes through, and the
     * only one they can agree on without a post-step hook.
     */
    private void compareWorlds(GameLogic logic, int frame) {
        if (frame % CHECKSUM_INTERVAL != 0 || frame == lastCheckedFrame) {
            return; // not a checked frame, or already sent while stalling on this one
        }
        lastCheckedFrame = frame;
        long own = logic.checksum();
        ownChecksums.put(frame, own);
        transport.send(new FrameChecksum(frame, localPlayer, own));

        var reported = reportedChecksums.get(frame); // peers that got here first
        if (reported != null) {
            for (var entry : new java.util.TreeMap<>(reported).entrySet()) {
                check(frame, entry.getKey(), entry.getValue());
            }
        }
        forget(frame - CHECKSUM_HISTORY);
    }

    private void check(int frame, int otherPlayer, long theirs) {
        var own = ownChecksums.get(frame);
        if (own == null || own == theirs || desync != null) {
            return; // not there yet, agreed, or already reported
        }
        desync = new Desync(frame, localPlayer, own, otherPlayer, theirs);
        for (var listener : desyncListeners) {
            listener.accept(desync);
        }
    }

    private void forget(int before) {
        ownChecksums.keySet().removeIf(frame -> frame < before);
        reportedChecksums.keySet().removeIf(frame -> frame < before);
    }

    /**
     * Turn dropped connections into a decision everyone can apply identically.
     *
     * <p>The missing player's silence is filled in up to {@code fromFrame} so the
     * frames already in flight can still run, and those filler packets go out over
     * the same relay as everything else — so no peer is left with a different idea
     * of what that player did before they vanished.
     */
    private void handleLostLinks(int frame) {
        if (lostLinks.isEmpty()) {
            return;
        }
        var lost = List.copyOf(lostLinks);
        lostLinks.clear();
        if (!host) {
            connectionLost = true; // a guest's only link is the host
            for (var listener : lostConnectionListeners) {
                listener.run();
            }
            return;
        }
        int fromFrame = frame + frameDelay; // far enough ahead that nobody has run it
        for (var player : lost) {
            for (int f = frame; f < fromFrame; f++) {
                if (!scheduler.hasSubmitted(f, player)) {
                    transport.send(new CommandPacket(f, player, List.of()));
                }
            }
            transport.send(new PeerLeft(player, fromFrame));
        }
    }
}
