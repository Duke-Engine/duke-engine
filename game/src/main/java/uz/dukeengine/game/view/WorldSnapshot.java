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
 * <p>{@link #commands} is the same bargain with a shape: what the player may do with whatever he has
 * selected, worked out here — on the simulation thread, where the state it is about lives — and drawn by
 * the window. The engine knows what none of the buttons mean.
 * <p>{@link #attackable} is the simulation's answer to the one thing the pointer asks of it over a thing that
 * is not the player's: would an attack on that by what is selected be taken — for the pointer to say so, since
 * an order to attack something no selected weapon may be fired at is refused.
 * <p>{@link #contextOrder} is the game's word for the order a click on the thing under the pointer would give what
 * is selected — {@code Enter}, {@code Dock}, {@code Repair} — or {@code null} for none: the pointer shows that word's
 * picture, and the click sends it as a {@code GameOrder}. The engine never reads it.
 * <p>{@link #beams} are the beams the simulation owns that the player sees an end of — see {@code World.beam}.
 * <p>{@link #rallies} are the rally points of the viewer's own things, with the lines they are shown by while selected.
 * <p>{@link #effects} are the effects riding the things the viewer is shown until the simulation ends them — see
 * {@code World.effect}.
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
        String status,
        List<CommandButton> commands,
        boolean aimFits,
        boolean attackable,
        CameraView camera,
        boolean revealed,
        String contextOrder,
        List<BeamView> beams,
        List<RallyView> rallies,
        List<EffectView> effects) {

    public static final WorldSnapshot EMPTY = new WorldSnapshot(0, 0f, false, 0, 0, List.of(), List.of(), "", "",
            List.of(), true, true, null, false, null, List.of(), List.of(), List.of());

    /** A frame with no effect riding a thing: every frame from before the simulation could keep one going. */
    public WorldSnapshot(int frame, float gameTimeSeconds, boolean paused, int localPlayerMoney,
            int localPlayerPowerSurplus, List<UnitView> units, List<WorldEvent> events, String banner,
            String status, List<CommandButton> commands, boolean aimFits, boolean attackable, CameraView camera,
            boolean revealed, String contextOrder, List<BeamView> beams, List<RallyView> rallies) {
        this(frame, gameTimeSeconds, paused, localPlayerMoney, localPlayerPowerSurplus, units, events, banner,
                status, commands, aimFits, attackable, camera, revealed, contextOrder, beams, rallies, List.of());
    }

    /** A frame with no rally point in it: every frame from before one was shown. */
    public WorldSnapshot(int frame, float gameTimeSeconds, boolean paused, int localPlayerMoney,
            int localPlayerPowerSurplus, List<UnitView> units, List<WorldEvent> events, String banner,
            String status, List<CommandButton> commands, boolean aimFits, boolean attackable, CameraView camera,
            boolean revealed, String contextOrder, List<BeamView> beams) {
        this(frame, gameTimeSeconds, paused, localPlayerMoney, localPlayerPowerSurplus, units, events, banner,
                status, commands, aimFits, attackable, camera, revealed, contextOrder, beams, List.of());
    }

    /** A frame with no beam in it: every frame from before the simulation could own one. */
    public WorldSnapshot(int frame, float gameTimeSeconds, boolean paused, int localPlayerMoney,
            int localPlayerPowerSurplus, List<UnitView> units, List<WorldEvent> events, String banner,
            String status, List<CommandButton> commands, boolean aimFits, boolean attackable, CameraView camera,
            boolean revealed, String contextOrder) {
        this(frame, gameTimeSeconds, paused, localPlayerMoney, localPlayerPowerSurplus, units, events, banner,
                status, commands, aimFits, attackable, camera, revealed, contextOrder, List.of());
    }

    /** A frame that says nothing of a click's order: every frame from before a click had any but its own. */
    public WorldSnapshot(int frame, float gameTimeSeconds, boolean paused, int localPlayerMoney,
            int localPlayerPowerSurplus, List<UnitView> units, List<WorldEvent> events, String banner,
            String status, List<CommandButton> commands, boolean aimFits, boolean attackable, CameraView camera,
            boolean revealed) {
        this(frame, gameTimeSeconds, paused, localPlayerMoney, localPlayerPowerSurplus, units, events, banner,
                status, commands, aimFits, attackable, camera, revealed, null);
    }

    /** A frame that says nothing of the map being revealed: every frame from before a map could be. */
    public WorldSnapshot(int frame, float gameTimeSeconds, boolean paused, int localPlayerMoney,
            int localPlayerPowerSurplus, List<UnitView> units, List<WorldEvent> events, String banner,
            String status, List<CommandButton> commands, boolean aimFits, boolean attackable, CameraView camera) {
        this(frame, gameTimeSeconds, paused, localPlayerMoney, localPlayerPowerSurplus, units, events, banner,
                status, commands, aimFits, attackable, camera, false);
    }

    /** A frame in which the game has not taken the camera: it is the player's. */
    public WorldSnapshot(int frame, float gameTimeSeconds, boolean paused, int localPlayerMoney,
            int localPlayerPowerSurplus, List<UnitView> units, List<WorldEvent> events, String banner,
            String status, List<CommandButton> commands, boolean aimFits, boolean attackable) {
        this(frame, gameTimeSeconds, paused, localPlayerMoney, localPlayerPowerSurplus, units, events, banner,
                status, commands, aimFits, attackable, null);
    }

    /** A frame that says nothing of attacking, which is a frame in which nothing is refused. */
    public WorldSnapshot(int frame, float gameTimeSeconds, boolean paused, int localPlayerMoney,
            int localPlayerPowerSurplus, List<UnitView> units, List<WorldEvent> events, String banner,
            String status, List<CommandButton> commands, boolean aimFits) {
        this(frame, gameTimeSeconds, paused, localPlayerMoney, localPlayerPowerSurplus, units, events, banner,
                status, commands, aimFits, true);
    }

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
        commands = commands == null ? List.of() : List.copyOf(commands);
        contextOrder = contextOrder == null || contextOrder.isBlank() ? null : contextOrder;
        beams = beams == null ? List.of() : List.copyOf(beams);
        rallies = rallies == null ? List.of() : List.copyOf(rallies);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
}
