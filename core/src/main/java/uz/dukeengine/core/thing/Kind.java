package uz.dukeengine.core.thing;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A classification flag on a thing — SAGE's {@code KindOfType}, opened up.
 *
 * <p>A thing carries a <em>set</em> of these, which systems query to make broad
 * decisions ("can this be selected?", "is this a structure?") without caring
 * about the exact object type. In INI a thing lists them by name:
 * {@code KindOf = SELECTABLE VEHICLE CAN_ATTACK}.
 *
 * <p>SAGE hardcodes ~100 of these in an enum, which only works because SAGE is
 * one game. The engine has no business knowing that {@code HARVESTER} is a
 * thing, so kinds are <strong>interned by name</strong> instead: a game defines
 * whatever vocabulary it needs (see {@code uz.dukeengine.rts.thing.RtsKinds}) and INI
 * spells the same names. Interning keeps the comparison an identity check, so
 * {@code isKindOf} stays as cheap as the enum was.
 *
 * <p>Names are case-insensitive and normalised to upper case, so
 * {@code KindOf = structure} and {@code KindOf = STRUCTURE} mean the same thing.
 */
public final class Kind {

    private static final Map<String, Kind> INTERNED = new ConcurrentHashMap<>();

    private final String name;

    private Kind(String name) {
        this.name = name;
    }

    /** The kind with this name, creating it on first sight. */
    public static Kind of(String name) {
        return INTERNED.computeIfAbsent(name.toUpperCase(Locale.ROOT), Kind::new);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    // Identity equality is correct and intended: instances are interned, so two
    // Kinds are equal exactly when they are the same object. equals/hashCode are
    // left at Object's on purpose.
}
