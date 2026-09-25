package uz.dukeengine.rts.message;

/**
 * Who gave an order — the reference's {@code CommandSourceType}, which a weapon slot's {@code AutoChooseSources}
 * answers: a slot may be picked for an order only by the sources it names.
 */
public enum OrderSource {
    /** A player's own order: a click, a key. */
    PLAYER,
    /**
     * The game's own: its scripts, its computer players — and a unit's own look for a target, which the reference
     * counts as its computer's ({@code CMD_FROM_AI}).
     */
    GAME,
    /** Named on a slot, no source at all: a slot only a lock fires. */
    NONE
}
