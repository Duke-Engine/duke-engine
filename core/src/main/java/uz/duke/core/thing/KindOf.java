package uz.duke.core.thing;

/**
 * Classification flags for a thing, ported from SAGE's {@code KindOfType}.
 *
 * <p>A thing carries a <em>set</em> of these (an {@link java.util.EnumSet}),
 * which the engine queries to make broad decisions — "can this be selected?",
 * "is this a structure?", "is it a valid target?" — without caring about the
 * exact object type. In INI a thing lists them by name:
 * {@code KindOf = SELECTABLE VEHICLE CAN_ATTACK}.
 *
 * <p>This is the common, structurally important subset; SAGE defines ~100 and
 * more can be added as systems need them.
 */
public enum KindOf {
    OBSTACLE,
    SELECTABLE,
    IMMOBILE,
    CAN_ATTACK,
    STRUCTURE,
    INFANTRY,
    VEHICLE,
    AIRCRAFT,
    HUGE_VEHICLE,
    DOZER,
    HARVESTER,
    COMMANDCENTER,
    TRANSPORT,
    PROJECTILE,
    NO_COLLIDE,
    SCORE,
    CRATE,
    CAPTURABLE,
    UNATTACKABLE,
    ALWAYS_VISIBLE,
    POWERED,
    TECH_BUILDING,
    DRONE
}
