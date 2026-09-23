package uz.dukeengine.core.module;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The kind of damage a weapon deals, ported in spirit from SAGE's {@code DamageType} — and opened up
 * the way {@link uz.dukeengine.core.thing.Kind} is, for the same reason.
 *
 * <p>Damage type is the whole of how a target's {@link Armor} answers a weapon: below 1.0 it resists,
 * above 1.0 it is a weakness, unlisted it takes the blow in full. It used to be an enum of five, which
 * its own javadoc called "a representative subset" — and a subset is exactly what a combat table cannot
 * be. A game with more kinds than the engine has heard of had to collapse them onto {@code NORMAL}, and
 * a table whose rows all key the same row is not a table. Measured in one RTS: of the thirty kinds its
 * 363 weapons fire, five were expressible, and collapsing the rest inverted the game — its infantry
 * resist armour-piercing at 0.1 and take small arms in full, so a rifle shredded tanks and a tank shell
 * scratched a man.
 *
 * <p>So a type is a word, interned. The engine names the five it always named and knows no others; a
 * dungeon says {@code HOLY} and {@code NECROTIC}, an RTS says {@code SMALL_ARMS}, and neither has to
 * tell the engine first. A word nothing uses is not an error — a data file naming a type no armour
 * lists is a weapon whose damage is never scaled, which is what "unlisted is 1.0" already meant.
 *
 * <p>Interned, so comparing two is an identity check and keying a map by one is as cheap as the enum
 * was — which matters, because this is read on every shot that lands. Names are case-insensitive and
 * folded to upper case once, so {@code DamageType = flame} and {@code DamageType = FLAME} are one type
 * and two machines reading the same file reach the same object.
 */
public final class DamageType {

    private static final Map<String, DamageType> INTERNED = new ConcurrentHashMap<>();

    /** What a weapon that names no type deals, and what an armour that lists nothing takes in full. */
    public static final DamageType NORMAL = of("NORMAL");

    public static final DamageType ARMOR_PIERCING = of("ARMOR_PIERCING");
    public static final DamageType EXPLOSION = of("EXPLOSION");
    public static final DamageType FLAME = of("FLAME");
    public static final DamageType SNIPER = of("SNIPER");

    private final String name;

    private DamageType(String name) {
        this.name = name;
    }

    /**
     * The type with this name, creating it on first sight.
     *
     * <p>Also how a data file's word becomes one: the {@code Binder} reads any type with a static
     * {@code of(String)} that way, so {@code DamageType = SMALL_ARMS} needs nothing registered.
     */
    public static DamageType of(String name) {
        return INTERNED.computeIfAbsent(name.toUpperCase(Locale.ROOT), DamageType::new);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    // Identity equality is correct and intended: instances are interned, so two DamageTypes are equal
    // exactly when they are the same object. equals/hashCode are left at Object's on purpose -- see Kind.
}
