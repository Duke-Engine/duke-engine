package uz.duke.rts.thing;

import uz.duke.core.thing.Kind;

/**
 * The RTS classification vocabulary — the {@link Kind}s this module's systems
 * look for, and the names INI spells in {@code KindOf = …}.
 *
 * <p>Kinds are interned by name, so these constants are a convenience and a
 * spelling contract, not a closed set: a game can invent its own kinds and read
 * them back with {@link Kind#of(String)} without touching the engine or this
 * class.
 */
public final class RtsKinds {

    /** The player may click this to select it. */
    public static final Kind SELECTABLE = Kind.of("SELECTABLE");

    /** Carries a weapon. */
    public static final Kind CAN_ATTACK = Kind.of("CAN_ATTACK");

    /** A building: drawn larger, does not move. */
    public static final Kind STRUCTURE = Kind.of("STRUCTURE");

    /** A foot soldier. */
    public static final Kind INFANTRY = Kind.of("INFANTRY");

    /** A ground vehicle. */
    public static final Kind VEHICLE = Kind.of("VEHICLE");

    /** Draws from the base's power grid; stalls when the base is under-powered. */
    public static final Kind POWERED = Kind.of("POWERED");

    private RtsKinds() {
    }
}
