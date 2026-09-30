package uz.dukeengine.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.IntConsumer;
import uz.dukeengine.core.Flavour;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.message.Command;

/**
 * What {@link DukeGame} does on the simulation thread each frame, just before the world's own {@code simulate} ({@code
 * GameLogic.eachFrame}), whatever kind of game the world is: the work posted from other threads; the commands posted
 * from them — sent to every machine where there is a session and the kind's wire carries them, given at once where
 * there is none; the game's per-frame and every-so-often code; and the annihilation rule, a side that had things and
 * has none left told defeated.
 */
final class LogicDriver implements Runnable {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(LogicDriver.class.getName());

    private final GameLogic logic;
    private final Flavour flavour;

    /**
     * Commands posted from the UI thread, drained each frame on the logic thread: a game's own besides the kind's, in
     * the one queue that makes input land on a frame boundary and reach the replay log.
     */
    private final Queue<Command> inbox = new ConcurrentLinkedQueue<>();

    /** Arbitrary work posted from other threads, run on the logic thread. */
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    /** When set, local commands go over the wire instead of straight in. */
    private MultiplayerSession session;

    private final List<Runnable> tickCallbacks = new ArrayList<>();
    private final List<IntervalCallback> intervalCallbacks = new ArrayList<>();

    private final Set<Integer> everOwnedObjects = new HashSet<>();
    private final Set<Integer> defeated = new HashSet<>();
    private IntConsumer defeatListener;

    private record IntervalCallback(int everyFrames, Runnable action) {
    }

    LogicDriver(GameLogic logic, Flavour flavour) {
        this.logic = logic;
        this.flavour = flavour;
    }

    void setSession(MultiplayerSession session) {
        this.session = session;
    }

    /** Thread-safe: post a command from any thread (typically the window's). */
    void post(Command command) {
        inbox.add(command);
    }

    /** Thread-safe: run {@code task} on the logic thread next frame. */
    void postTask(Runnable task) {
        tasks.add(task);
    }

    void addTickCallback(Runnable action) {
        tickCallbacks.add(action);
    }

    void addIntervalCallback(int everyFrames, Runnable action) {
        intervalCallbacks.add(new IntervalCallback(Math.max(1, everyFrames), action));
    }

    /** Told the index of each side the annihilation rule defeats. */
    void setDefeatListener(IntConsumer listener) {
        this.defeatListener = listener;
    }

    @Override
    public void run() {
        for (var task = tasks.poll(); task != null; task = tasks.poll()) {
            task.run();
        }
        for (var command = inbox.poll(); command != null; command = inbox.poll()) {
            if (session != null && flavour.carries(command)) {
                session.issueLocal(command); // ships to every peer, applied in lock-step
            } else {
                if (session != null) {
                    // The wire speaks the kind's set, and applying this one locally would desync, so say so loudly —
                    // and say what does travel.
                    var unsendable = command;
                    LOG.warning(() -> "game command cannot be sent to peers: "
                            + unsendable.getClass().getName() + "; send it as a GameOrder");
                    continue;
                }
                logic.issueCommand(command); // applied at the start of the next frame
            }
        }

        for (var callback : tickCallbacks) {
            callback.run();
        }
        for (var interval : intervalCallbacks) {
            if (logic.getFrame() > 0 && logic.getFrame() % interval.everyFrames() == 0) {
                interval.action().run();
            }
        }

        checkDefeats();
    }

    /** Annihilation rule: a player who had objects and now has none is defeated. */
    private void checkDefeats() {
        everOwnedObjects.addAll(logic.getOwners());
        if (defeatListener == null) {
            return;
        }
        for (var playerIndex : everOwnedObjects) {
            if (defeated.contains(playerIndex)) {
                continue;
            }
            boolean anyAlive = false;
            for (var object : logic.getObjectsOf(playerIndex)) {
                if (!object.isEffectivelyDead()) {
                    anyAlive = true;
                    break;
                }
            }
            if (!anyAlive) {
                defeated.add(playerIndex);
                defeatListener.accept(playerIndex);
            }
        }
    }
}
