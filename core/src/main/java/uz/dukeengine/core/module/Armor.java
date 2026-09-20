package uz.dukeengine.core.module;

import java.util.EnumMap;
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
        this.multipliers = multipliers.isEmpty()
                ? Map.of()
                : new EnumMap<>(multipliers);
    }

    /** The damage multiplier for {@code type} (1.0 if unspecified). */
    public float getMultiplier(DamageType type) {
        return multipliers.getOrDefault(type, 1.0f);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<DamageType, Float> multipliers = new EnumMap<>(DamageType.class);

        public Builder set(DamageType type, float multiplier) {
            multipliers.put(type, multiplier);
            return this;
        }

        public Armor build() {
            return new Armor(multipliers);
        }
    }
}
