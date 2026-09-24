package uz.dukeengine.core.thing;

/**
 * Transient status flags an object can carry, ported in spirit from SAGE's
 * {@code ObjectStatusType}.
 *
 * <p>Modules query these to alter behaviour: a {@link #DISABLED} unit cannot act
 * at all, a {@link #HELD} one cannot move and still fights, a {@link #SLOWED} unit
 * moves at reduced speed. Timed application is handled by a status module; the
 * flag itself just records the current state. They are part of the world's
 * checksum.
 */
public enum ObjectStatus {
    DISABLED,
    SLOWED,
    /**
     * Kept where it is, and still fighting — SAGE's {@code DISABLED_HELD}: nothing moves it (an order to move is
     * taken and goes nowhere, a pursuit does not close in), while its weapons go on choosing and firing at what is
     * in their range, and it can be hit, killed and carried. A game's code sets it and clears it; cleared, a move
     * it was ordered on goes on at once.
     */
    HELD,
    /**
     * Being taken down for its worth — SAGE's {@code OBJECT_STATUS_SOLD}: it may no longer be selected and does
     * nothing it was for, while it can still be hit; when it is down, its side has its refund and it is gone.
     */
    SOLD,
    /**
     * Still being built: standing, seen, and able to be hurt, but doing nothing of what it is for until it is
     * whole — a half-built barracks trains nobody and a half-built tower shoots at nobody. SAGE's
     * {@code OBJECT_STATUS_UNDER_CONSTRUCTION}. Only a module that builds it runs meanwhile; see
     * {@code UpdateModule.runsWhileUnderConstruction}.
     */
    UNDER_CONSTRUCTION,
    /**
     * In the air: flying, not standing on the ground under it — SAGE's {@code OBJECT_STATUS_AIRBORNE_TARGET}.
     * {@code FlyUpdate} sets it while its thing is aloft; a game that flies things some other way sets it when they
     * take off and clears it when they land, and whatever asks whether a thing is in the air asks this.
     */
    AIRBORNE
}
