package uz.duke.core.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import uz.duke.core.GameLogic;
import uz.duke.core.message.GameMessage;

/**
 * Drives one peer's {@link GameLogic} in lock-step, connecting local input, the
 * network, and the {@link LockstepScheduler} gate.
 *
 * <p>Each peer issues its commands {@code frameDelay} frames ahead of when they
 * execute, broadcasts them, and only advances a frame once every peer's commands
 * for that frame have arrived. Because all peers run the same deterministic
 * simulation and apply the same commands at the same frames, their worlds stay
 * identical — verifiable with {@link GameLogic#checksum()}.
 *
 * <p>Transport-agnostic: {@code broadcast} is whatever ships a {@link CommandPacket}
 * to all peers (including this one, so the local scheduler also hears it), and
 * {@link #receive} is called for every packet that arrives. A real build wires
 * these to sockets; tests wire them to an in-memory fan-out.
 */
public final class LockstepDriver {

    private final GameLogic logic;
    private final LockstepScheduler scheduler;
    private final int localPlayer;
    private final int frameDelay;
    private final Consumer<CommandPacket> broadcast;

    private final List<GameMessage> pending = new ArrayList<>();
    private int nextSubmitFrame;

    public LockstepDriver(GameLogic logic, LockstepScheduler scheduler,
            int localPlayer, int frameDelay, Consumer<CommandPacket> broadcast) {
        if (frameDelay < 1) {
            throw new IllegalArgumentException("frameDelay must be >= 1");
        }
        this.logic = logic;
        this.scheduler = scheduler;
        this.localPlayer = localPlayer;
        this.frameDelay = frameDelay;
        this.broadcast = broadcast;
        this.nextSubmitFrame = frameDelay;
    }

    /**
     * Announce empty commands for the first {@code frameDelay} frames so the
     * simulation can start before any real input exists. Call once, after all
     * peers are wired to the transport.
     */
    public void prime() {
        for (int frame = 0; frame < frameDelay; frame++) {
            broadcast.accept(new CommandPacket(frame, localPlayer, List.of()));
        }
    }

    /** Queue a command from local input; it ships on the next {@link #tick}. */
    public void issueLocal(GameMessage command) {
        pending.add(command);
    }

    /** Deliver a packet from any peer (including the local echo) to the scheduler. */
    public void receive(CommandPacket packet) {
        scheduler.submit(packet.frame(), packet.playerIndex(), packet.commands());
    }

    /**
     * Try to advance one frame. Submits this peer's pending commands for the
     * frame {@code frameDelay} ahead, then — if every peer has reported for the
     * current frame — applies that frame's commands and steps the simulation.
     *
     * @return true if the simulation advanced, false if it stalled waiting on a peer
     */
    public boolean tick() {
        int current = logic.getFrame();
        int target = current + frameDelay;
        if (target == nextSubmitFrame) {
            broadcast.accept(new CommandPacket(target, localPlayer, List.copyOf(pending)));
            pending.clear();
            nextSubmitFrame++;
        }

        if (!scheduler.isFrameReady(current)) {
            return false;
        }
        for (var command : scheduler.takeCommands(current)) {
            logic.issueCommand(command);
        }
        logic.update();
        return true;
    }
}
