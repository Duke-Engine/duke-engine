package uz.dukeengine.core.module;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How a thing died — blown apart, burned, run over — ported in spirit from SAGE's {@code DeathType}, and open
 * the way {@link DamageType} is, for the same reason.
 *
 * <p>Damage type is how armour answers a blow; death type is how the death looks and what it leaves. The
 * reference keeps them apart and so does this: in the RTS this was measured in, its weapons deal {@code
 * EXPLODED} (153), {@code NORMAL} (116), {@code BURNED} (16), {@code SUICIDED} (15), {@code POISONED} (11) and
 * a dozen more, and its death modules each name the ones they answer — a soldier shot falls over, the same
 * soldier shelled is thrown, the same soldier run over is flattened.
 *
 * <p>So a type is a word, interned. The engine names {@code NORMAL}, what a blow that says nothing deals, and
 * knows no others; a game says {@code EXPLODED} in its weapon's block and reads it back in its die module and
 * in the name of the moment its client plays ({@code died.<template>.exploded}). Names are case-insensitive and
 * folded to upper case once, so two machines reading the same file reach the same object.
 */
public final class DeathType {

    private static final Map<String, DeathType> INTERNED = new ConcurrentHashMap<>();

    /** What a blow that names no death deals, and how a thing dies that nobody killed. */
    public static final DeathType NORMAL = of("NORMAL");

    private final String name;

    private DeathType(String name) {
        this.name = name;
    }

    /** The type with this name, creating it on first sight — also how the {@code Binder} reads one. */
    public static DeathType of(String name) {
        return INTERNED.computeIfAbsent(name.toUpperCase(Locale.ROOT), DeathType::new);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    // Identity equality, as DamageType: interned, so two are equal exactly when they are the same object.
}
