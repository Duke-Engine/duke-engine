package uz.dukeengine.core.module;

import uz.dukeengine.core.thing.GameObject;

/**
 * Holds an object's health, ported from SAGE's {@code BodyModule}.
 *
 * <p>An object's "aliveness" is owned here, not on the object itself: damage,
 * healing and death all flow through the body. Different body modules model
 * different rules (an indestructible body, a structure body with construction
 * percent, …); {@link ActiveBody} is the standard damageable one.
 */
public abstract class BodyModule extends Module {

    /** The blow that killed it, once one has; see {@link #getDeath}. */
    private Death death;

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
        damage(amount, DamageType.NORMAL, Death.NORMAL);
    }

    /**
     * Apply a blow that says how it kills — what death it deals and whose it is — and keep that, if it is the
     * blow that kills, as how this body died. A blow landing on a living body forgets an older one, so a thing
     * brought back and killed again died of the second.
     */
    public final void damage(float amount, DamageType type, Death blow) {
        boolean wasAlive = !isDead();
        damage(amount, type);
        if (wasAlive) {
            death = isDead() ? blow : null;
        }
    }

    /**
     * How it died: the blow that killed it, or {@link Death#NORMAL} for a body that died some other way — its
     * health set to nothing, or damage that said nothing about how. {@code null} while it lives.
     */
    public final Death getDeath() {
        if (!isDead()) {
            return null;
        }
        return death == null ? Death.NORMAL : death;
    }

    /**
     * How much of {@code amount} of {@code type} damage this body would take, without taking it — what a unit
     * choosing between its weapons weighs them by. SAGE's {@code BodyModule::estimateDamage}. A body with no
     * armour takes all of it, which is this default; one that armours itself says otherwise.
     */
    public float estimateDamage(float amount, DamageType type) {
        return Math.max(0f, amount);
    }

    /** Restore {@code amount} of health, clamped to the maximum. */
    public abstract void heal(float amount);

    public boolean isDead() {
        return getHealth() <= 0f;
    }
}
