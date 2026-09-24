package uz.dukeengine.core.module;

import uz.dukeengine.core.event.ObjectHurt;
import uz.dukeengine.core.math.Coord3D;
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
        damage(amount, type, blow, null, null);
    }

    /**
     * The same, saying where on the body the blow landed and where it came from, for the {@link ObjectHurt} it
     * posts — a blow that takes health posts one, a blow that takes none nothing.
     *
     * @param at   where on the body it landed, or {@code null} for the body's middle
     * @param from where it came from, or {@code null} for wherever its dealer stands
     */
    public final void damage(float amount, DamageType type, Death blow, Coord3D at, Coord3D from) {
        boolean wasAlive = !isDead();
        float worth = wasAlive ? estimateDamage(amount, type) : 0f;
        damage(amount, type);
        if (!wasAlive) {
            return;
        }
        death = isDead() ? blow : null;
        if (worth > 0f) {
            hurt(type, worth, blow, at, from);
        }
    }

    private void hurt(DamageType type, float worth, Death blow, Coord3D at, Coord3D from) {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null) {
            return;
        }
        var attacker = blow == null ? null : blow.killer();
        var dealer = attacker == null ? null : world.findObject(attacker);
        var middle = owner.getPosition();
        var where = at != null ? at
                : new Coord3D(middle.x(), middle.y(), middle.z() + owner.getGeometry().height() / 2f);
        world.post(new ObjectHurt(world.getFrame(), owner.getId(), owner.getTemplate().name(),
                owner.getPlayerIndex(), type, worth, attacker, where,
                from != null ? from : dealer == null ? null : dealer.getPosition()));
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

    /** What a change of a body's most health does to what it has now: SAGE's {@code MaxHealthChangeType}. */
    public enum MaxHealthChange {
        /** The same share of the new most as of the old — {@code PRESERVE_RATIO}: 50 of 100 is 100 of 200. */
        KEEP_SHARE,
        /** What the most rose or fell by, added — {@code ADD_CURRENT_HEALTH_TOO}: 50 of 100 is 150 of 200. */
        ADD_DIFFERENCE,
        /** What it has, left as it is — {@code SAME_CURRENTHEALTH}: 50 of 100 is 50 of 200. */
        KEEP_HEALTH
    }

    /**
     * Change the most health it may have, and what it has now by {@code rule} — an upgrade's, from game code on the
     * simulation thread. A body that cannot says so in the log and stays as it is.
     */
    public void setMaxHealth(float most, MaxHealthChange rule) {
        java.util.logging.Logger.getLogger(BodyModule.class.getName()).warning(
                getClass().getSimpleName() + " cannot change its most health; it stays " + getMaxHealth());
    }

    public boolean isDead() {
        return getHealth() <= 0f;
    }
}
