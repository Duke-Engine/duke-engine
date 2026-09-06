package uz.duke.core.thing;

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
    SLOWED
}
