package uz.dukeengine.rts.module;

/**
 * Where a weapon stands between one shot and the next — SAGE's {@code WeaponStatus}, without its pre-attack
 * wait, which nothing here has.
 */
public enum WeaponStatus {
    /** It may fire now. */
    READY,
    /** It fired and has rounds left, and is waiting its delay between shots. */
    BETWEEN_SHOTS,
    /** It emptied its clip and is refilling it by itself. */
    RELOADING,
    /** It emptied its clip and does not refill it by itself: nothing until something refills it. */
    OUT
}
