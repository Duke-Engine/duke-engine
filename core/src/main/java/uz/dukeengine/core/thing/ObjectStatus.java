package uz.dukeengine.core.thing;

/**
 * Transient status flags an object can carry, ported in spirit from SAGE's
 * {@code ObjectStatusType}.
 *
 * <p>Modules query these to alter behaviour: a {@link #DISABLED} unit cannot act
 * at all, a {@link #SLOWED} unit moves at reduced speed. Timed application is
 * handled by a status module; the flag itself just records the current state.
 */
public enum ObjectStatus {
    DISABLED,
    SLOWED,
    /**
     * Still being built: standing, seen, and able to be hurt, but doing nothing of what it is for until it is
     * whole — a half-built barracks trains nobody and a half-built tower shoots at nobody. SAGE's
     * {@code OBJECT_STATUS_UNDER_CONSTRUCTION}. Only a module that builds it runs meanwhile; see
     * {@code UpdateModule.runsWhileUnderConstruction}.
     */
    UNDER_CONSTRUCTION,
    /**
     * In the air: flying, not standing on the ground under it — SAGE's {@code OBJECT_STATUS_AIRBORNE_TARGET}.
     * Nothing in the engine flies yet, so nothing in it sets this; a game whose aircraft take off sets it when
     * they do and clears it when they land, and whatever asks whether a thing is in the air asks this. When
     * the engine flies things itself it will set it the same way.
     */
    AIRBORNE
}
