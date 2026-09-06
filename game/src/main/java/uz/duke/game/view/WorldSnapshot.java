package uz.duke.game.view;

import java.util.List;

/**
 * An immutable frame of the world for the presentation layer, built on the
 * simulation thread once per client frame and handed to Swing via a volatile
 * reference — the thread-safety seam between logic and rendering.
 *
 * <p>Contains only what the local player can see (fog of war is applied when the
 * snapshot is built, not at draw time).
 */
public record WorldSnapshot(
        int frame,
        float gameTimeSeconds,
        boolean paused,
        int localPlayerMoney,
        int localPlayerPowerSurplus,
        List<UnitView> units,
        String banner) {

    public static final WorldSnapshot EMPTY =
            new WorldSnapshot(0, 0f, false, 0, 0, List.of(), "");

    public boolean hasBanner() {
        return banner != null && !banner.isEmpty();
    }

    public WorldSnapshot {
        units = List.copyOf(units);
    }
}
