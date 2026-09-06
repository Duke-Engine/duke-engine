package uz.duke.core.module;

/**
 * A unit's experience rank, ported from SAGE's {@code VeterancyLevel}.
 *
 * <p>Units climb from {@link #REGULAR} to {@link #HEROIC} by earning experience
 * in combat; each rank carries a combat bonus (here, a damage multiplier). SAGE
 * authors per-template multipliers; this uses fixed, sensible defaults.
 */
public enum VeterancyLevel {
    REGULAR(1.0f),
    VETERAN(1.1f),
    ELITE(1.2f),
    HEROIC(1.3f);

    private final float damageMultiplier;

    VeterancyLevel(float damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }
}
