package uz.dukeengine.game;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Logger;
import uz.dukeengine.combat.message.GameOrder;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.GameLogic;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.message.Command;

/** What the game's own code asked to hear, and when the simulation tells it. */
final class Callbacks {

    private static final Logger LOG = Logger.getLogger(Callbacks.class.getName());

    private record Interval(double seconds, Consumer<DukeGame> action) {
    }

    private final List<Consumer<DukeGame>> start = new ArrayList<>();
    private final List<Consumer<DukeGame>> tick = new ArrayList<>();
    private final List<Interval> intervals = new ArrayList<>();
    private final List<Consumer<GameOrder>> orders = new ArrayList<>();
    private final List<Consumer<ObjectDied>> died = new ArrayList<>();
    private final List<BiConsumer<DukeGame, GamePlayer>> defeated = new ArrayList<>();
    private final List<BiConsumer<DukeGame, GamePlayer>> left = new ArrayList<>();
    private final List<LongSupplier> checksumParts = new ArrayList<>();
    /** Asked on the simulation thread as each command of the game's own is applied. */
    private volatile Consumer<Command> commandHandler;

    void onStart(Consumer<DukeGame> callback) {
        start.add(callback);
    }

    void onTick(Consumer<DukeGame> callback) {
        tick.add(callback);
    }

    void every(double seconds, Consumer<DukeGame> callback) {
        intervals.add(new Interval(seconds, callback));
    }

    void onOrder(Consumer<GameOrder> listener) {
        orders.add(listener);
    }

    void onDied(Consumer<ObjectDied> callback) {
        died.add(callback);
    }

    void onDefeated(BiConsumer<DukeGame, GamePlayer> callback) {
        defeated.add(callback);
    }

    void onLeft(BiConsumer<DukeGame, GamePlayer> callback) {
        left.add(callback);
    }

    void checksumAlso(LongSupplier part) {
        checksumParts.add(part);
    }

    private final java.util.Map<String, uz.dukeengine.core.save.Saveable> saveParts = new java.util.LinkedHashMap<>();

    void saveAlso(String name, uz.dukeengine.core.save.Saveable part) {
        if (saveParts.putIfAbsent(name, part) != null) {
            throw new IllegalArgumentException("a save part called " + name + " is kept already");
        }
    }

    void onCommand(Consumer<Command> handler) {
        this.commandHandler = handler;
    }

    /** Hand the simulation what it is to tell: the game's checksum numbers, its deaths, its own commands. */
    void attach(GameLogic logic) {
        checksumParts.forEach(logic::checksumAlso);
        saveParts.forEach(logic::saveAlso);
        died.forEach(logic::onDied);
    }

    /** Hand the driver the game's per-frame and timed code, in the order it was given. */
    void schedule(DukeGame game, LogicDriver driver) {
        for (var callback : tick) {
            driver.addTickCallback(() -> callback.accept(game));
        }
        for (var interval : intervals) {
            int frames = Math.max(1, (int) Math.round(interval.seconds() * GameConstants.LOGICFRAMES_PER_SECOND));
            driver.addIntervalCallback(frames, () -> interval.action().accept(game));
        }
    }

    void started(DukeGame game) {
        start.forEach(callback -> callback.accept(game));
    }

    void playerDefeated(DukeGame game, GamePlayer player) {
        defeated.forEach(callback -> callback.accept(game, player));
    }

    void playerLeft(DukeGame game, GamePlayer player) {
        left.forEach(callback -> callback.accept(game, player));
    }

    /**
     * A command outside the world's own set, as it is applied: a word order told every {@link #onOrder} watcher, in the
     * order they were added; one of the game's own told its handler, or logged where it has none.
     */
    void otherCommand(Command command) {
        switch (command) {
            case GameOrder order -> orders.forEach(watcher -> watcher.accept(order));
            default -> {
                var handler = commandHandler;
                if (handler == null) {
                    LOG.warning(() -> "ignoring a command of no set this game knows: " + command.getClass().getName());
                    return;
                }
                handler.accept(command);
            }
        }
    }
}
