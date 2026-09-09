package uz.duke.rts.module;

/**
 * A module that changes how hard its owner hits.
 *
 * <p>The seam that makes "this particular unit deals more damage" expressible at
 * all. Before it there were exactly two ways to raise damage, and both were
 * someone else's decision: a veterancy rank with multipliers compiled into an
 * enum, and a bonus held on the <em>player</em>, which every unit that player
 * owns shares whether it earned it or not. A hero who has levelled and a
 * conscript standing beside him got the same bonus.
 *
 * <p>Any module can implement this — the engine's own, or one a game writes —
 * and {@link WeaponUpdate} multiplies by all of them. That is what turns
 * veterancy from a rule the engine imposes into one rule a game might choose:
 * Generals-style ranks, Warcraft-style hero levels, or a temporary battle-cry
 * buff are all the same mechanism from here.
 *
 * <p>Modifiers compose by multiplication, applied in the owner's module order,
 * which is fixed at attachment — so the result is the same on every machine.
 */
public interface DamageModifier {

    /**
     * What to multiply this unit's outgoing damage by. 1.0 changes nothing.
     *
     * <p>Read every time a shot lands, so it may vary with the unit's state —
     * but it must be a pure function of simulation state, never of the clock.
     */
    float damageMultiplier();
}
