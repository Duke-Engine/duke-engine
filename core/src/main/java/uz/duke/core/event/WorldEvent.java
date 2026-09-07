package uz.duke.core.event;

/**
 * Something that happened during a logic frame, announced so the presentation
 * layer can react to it.
 *
 * <p>The simulation is a state machine, and a snapshot of state cannot express a
 * <em>moment</em>. "This unit is at 40 health" is state; "this unit was just
 * destroyed" is not — by the time the client looks, the object is gone from the
 * world. Without a channel for moments, a client is reduced to inferring them,
 * and inference is wrong at the edges: a unit vanishing because it died looks
 * exactly like one vanishing into fog of war.
 *
 * <p>Events flow one way only. Nothing in the simulation reads them, they take
 * no part in {@code checksum()}, and dropping every one of them would not change
 * the outcome of a single frame. That is what makes them safe to hand across to
 * a renderer.
 *
 * <p>Like {@link uz.duke.core.message.Command}, the engine does not define what
 * a game's events are. It supplies the channel and its own genre-neutral events
 * (an object died); a game posts its own alongside them — see
 * {@code uz.duke.rts.event.WeaponFired}.
 */
public interface WorldEvent {

    /** The logic frame this happened on. */
    int frame();

    /**
     * Where on the map this happened, or {@code null} if it has no place — a
     * player being defeated, say.
     *
     * <p>Events are posted without regard to who can see them, so this is what
     * lets a client filter them through fog of war. Without it, a player would
     * hear an explosion in territory they have no eyes on.
     */
    default uz.duke.core.math.Coord3D where() {
        return null;
    }
}
