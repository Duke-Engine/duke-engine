package uz.duke.core.module;

import uz.duke.core.thing.GameObject;

/**
 * Holds an object's health, ported from SAGE's {@code BodyModule}.
 *
 * <p>An object's "aliveness" is owned here, not on the object itself: damage,
 * healing and death all flow through the body. Different body modules model
 * different rules (an indestructible body, a structure body with construction
 * percent, …); {@link ActiveBody} is the standard damageable one.
 */
public abstract class BodyModule extends Module {

    protected BodyModule(GameObject owner) {
        super(owner);
    }

    public abstract float getHealth();

    public abstract float getMaxHealth();

    public abstract void setHealth(float health);

    /**
     * Apply {@code amount} of {@code type} damage, scaled by this body's armor and
     * clamped so health never drops below 0.
     */
    public abstract void damage(float amount, DamageType type);

    /** Apply untyped ({@link DamageType#NORMAL}) damage. */
    public final void damage(float amount) {
        damage(amount, DamageType.NORMAL);
    }

    /** Restore {@code amount} of health, clamped to the maximum. */
    public abstract void heal(float amount);

    public boolean isDead() {
        return getHealth() <= 0f;
    }
}
