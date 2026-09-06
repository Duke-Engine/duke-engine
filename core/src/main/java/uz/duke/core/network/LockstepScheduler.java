package uz.duke.core.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import uz.duke.core.SubsystemInterface;
import uz.duke.core.message.Command;

/**
 * The heart of SAGE's lock-step networking: a frame may only be simulated once
 * <em>every</em> player has reported their commands for it. Ported in spirit
 * from {@code ConnectionManager}/{@code FrameData}.
 *
 * <p>In lock-step there is no shared world state on the wire — only commands.
 * Each peer runs the identical deterministic simulation and exchanges the small
 * set of commands issued each frame; because every peer applies the same
 * commands at the same frame numbers, every peer's world stays bit-identical.
 * This class is the gate that enforces it: {@link #isFrameReady} backs
 * {@code GameEngine.isLogicFrameReady()}, stalling the simulation until the slowest
 * peer's input arrives rather than letting machines drift out of sync.
 *
 * <p>A player with nothing to do still {@linkplain #submit submits} an empty
 * command set, so "no input" is distinguished from "not yet heard from". Command
 * ordering is made deterministic by always draining players in ascending index
 * order — never hash/iteration order.
 *
 * <p>This is transport-agnostic: it does not touch sockets. A real connection
 * layer feeds {@link #submit} from the network and local input; here it can be
 * driven directly, which is exactly what makes lock-step testable.
 */
public final class LockstepScheduler extends SubsystemInterface {

    private final Set<Integer> players;

    /** frame number -> (player index -> that player's commands for the frame). */
    private final Map<Integer, Map<Integer, List<Command>>> byFrame = new HashMap<>();

    public LockstepScheduler(Collection<Integer> playerIndices) {
        this.players = Set.copyOf(playerIndices);
    }

    @Override
    public void init() {
        reset();
    }

    @Override
    public void reset() {
        byFrame.clear();
    }

    @Override
    public void update() {
    }

    /**
     * Record a player's commands for a future frame. An empty list is a valid,
     * meaningful submission ("I have no commands this frame").
     */
    public void submit(int frame, int playerIndex, List<Command> commands) {
        if (!players.contains(playerIndex)) {
            throw new IllegalArgumentException("player " + playerIndex + " is not in this game");
        }
        byFrame.computeIfAbsent(frame, f -> new HashMap<>())
                .put(playerIndex, List.copyOf(commands));
    }

    /** True once every player has submitted for {@code frame}. */
    public boolean isFrameReady(int frame) {
        var submissions = byFrame.get(frame);
        return submissions != null && submissions.keySet().containsAll(players);
    }

    /**
     * Remove and return all commands for {@code frame}, ordered by player index
     * then submission order. Call only when {@link #isFrameReady} is true.
     */
    public List<Command> takeCommands(int frame) {
        var submissions = byFrame.remove(frame);
        if (submissions == null) {
            return List.of();
        }
        var ordered = new ArrayList<Command>();
        // TreeMap → ascending player index, the deterministic drain order.
        for (var commands : new TreeMap<>(submissions).values()) {
            ordered.addAll(commands);
        }
        return ordered;
    }
}
