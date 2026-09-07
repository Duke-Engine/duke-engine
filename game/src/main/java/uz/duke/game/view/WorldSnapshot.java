package uz.duke.game.view;

import java.util.List;
import uz.duke.core.event.WorldEvent;

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
 */
public record WorldSnapshot(
        int frame,
        float gameTimeSeconds,
        boolean paused,
        int localPlayerMoney,
        int localPlayerPowerSurplus,
        List<UnitView> units,
        List<WorldEvent> events,
        String banner) {

    public static final WorldSnapshot EMPTY =
            new WorldSnapshot(0, 0f, false, 0, 0, List.of(), List.of(), "");

    public boolean hasBanner() {
        return banner != null && !banner.isEmpty();
    }

    public WorldSnapshot {
        units = List.copyOf(units);
        events = List.copyOf(events);
    }
}
