package uz.duke.dungeon.run;

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
 */
final class DepthBonus extends Module implements DamageModifier {

    private final float multiplier;

    DepthBonus(GameObject owner, float multiplier) {
        super(owner);
        this.multiplier = multiplier;
    }

    @Override
    public float damageMultiplier() {
        return multiplier;
    }
}
