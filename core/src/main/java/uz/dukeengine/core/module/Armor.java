package uz.dukeengine.core.module;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-damage-type damage multipliers for a body, ported in spirit from SAGE's
 * {@code Armor}.
 *
 * <p>Incoming damage of a given {@link DamageType} is scaled by this armor's
 * multiplier for that type: below 1.0 resists it, above 1.0 is a weakness.
 * Unlisted types default to 1.0 (full damage). {@link #NONE} resists nothing.
 */
public final class Armor {

    public static final Armor NONE = new Armor(Map.of());

    private final Map<DamageType, Float> multipliers;

    public Armor(Map<DamageType, Float> multipliers) {
        // Insertion order kept rather than a hash's: a DamageType is interned and so hashes by identity,
        // which is settled per run of the JVM. Nothing iterates this today; the day something does --
        // a save format, a checksum -- it has to walk it the same way on both machines.
        this.multipliers = multipliers.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(multipliers));
    }

    /** The damage multiplier for {@code type} (1.0 if unspecified). */
    public float getMultiplier(DamageType type) {
        return multipliers.getOrDefault(type, 1.0f);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<DamageType, Float> multipliers = new LinkedHashMap<>();

        public Builder set(DamageType type, float multiplier) {
            multipliers.put(type, multiplier);
            return this;
        }

        public Armor build() {
            return new Armor(multipliers);
        }
    }
}
