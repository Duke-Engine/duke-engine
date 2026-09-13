package uz.duke.dungeon.combat;

import uz.duke.core.module.Module;
import uz.duke.core.thing.GameObject;
import uz.duke.rts.module.DamageModifier;

/**
 * How much harder a monster hits for being this far down.
 *
 * <p>Attached when the monster is spawned rather than written into its template,
 * because the same Runner appears on every floor and only the floor is different.
 * A template is what a thing is; this is where it was found.
 *
 * <p>Rides the engine's {@link DamageModifier} seam, which is exactly the case
 * that seam was opened for: a bonus belonging to one unit rather than to its
 * whole side, from a module the engine has never heard of.
 *
 * <p>Read by its skills too, which deal their own damage rather than going through
 * the weapon: see {@code SkillBook}. And it remembers what its health was grown by,
 * so whatever it calls up was found as deep as it was: see {@code SummoningUpdate}.
 */
public final class DepthBonus extends Module implements DamageModifier {

    private final float multiplier;
    private final float healthMultiplier;

    public DepthBonus(GameObject owner, float multiplier) {
        this(owner, multiplier, 1f);
    }

    public DepthBonus(GameObject owner, float multiplier, float healthMultiplier) {
        super(owner);
        this.multiplier = multiplier;
        this.healthMultiplier = healthMultiplier;
    }

    @Override
    public float damageMultiplier() {
        return multiplier;
    }

    /** What its health was grown by for its depth. */
    public float healthMultiplier() {
        return healthMultiplier;
    }
}
