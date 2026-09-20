package uz.dukeengine.game.view;

import java.util.List;
import uz.dukeengine.core.event.WorldEvent;

/**
 * An immutable frame of the world for the presentation layer, built on the
 * simulation thread once per client frame and handed to Swing via a volatile
 * reference — the thread-safety seam between logic and rendering.
 *
 * <p>Contains only what the local player can see (fog of war is applied when the
 * snapshot is built, not at draw time).
 *
 * <p>{@link #units} is <em>state</em>: where everything is, right now.
 * {@link #events} is what <em>happened</em> since the last snapshot — a shot
 * fired, a unit destroyed. State cannot carry a moment, which is why both are
 * here: a renderer draws the first and reacts to the second.
 * <p>{@link #status} is the game's own line: whatever figures a particular game
 * wants on screen that the engine has no name for — a hero's level, a wave
 * number, a countdown. The engine cannot enumerate those in advance, so it
 * carries a string it never reads and leaves the meaning to the game.
 */
public record WorldSnapshot(
        int frame,
        float gameTimeSeconds,
        boolean paused,
        int localPlayerMoney,
        int localPlayerPowerSurplus,
        List<UnitView> units,
        List<WorldEvent> events,
        String banner,
        String status) {

    public static final WorldSnapshot EMPTY =
            new WorldSnapshot(0, 0f, false, 0, 0, List.of(), List.of(), "", "");

    public boolean hasBanner() {
        return banner != null && !banner.isEmpty();
    }

    /** Whether the game has put anything on its own line. */
    public boolean hasStatus() {
        return status != null && !status.isEmpty();
    }

    public WorldSnapshot {
        units = List.copyOf(units);
        events = List.copyOf(events);
        banner = banner == null ? "" : banner;
        status = status == null ? "" : status;
    }
}
