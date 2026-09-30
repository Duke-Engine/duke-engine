package uz.dukeengine.combat.module;

import uz.dukeengine.core.thing.GameObject;

/**
 * A module that says whether its thing's weapon is aimed — the reference's attack waiting on its aim
 * ({@code AIAttackAimAtTargetState}): a turret turning to the target ({@code TurretAI}), or the thing itself turning
 * in place until the target is within its weapon's {@code AcceptableAimDelta}.
 *
 * <p>{@link WeaponUpdate} fires a slot only while every such module on the thing says it is aimed. Not aimed, the
 * weapon keeps its target, its reload and its look for a target — so the first shot at a target it picked itself
 * waits for the aim as well. The turning is the game's: {@link WeaponUpdate#slotFor} says which slot would fire at a
 * thing, and so which turret, or the thing, to turn. Walked in module order, fixed when the object is built, as a
 * {@link WeaponHold} is, so every peer gets the same answer.
 */
public interface WeaponAim {

    /** Whether slot {@code slot} of the set in use, carrying {@code weapon}, is aimed at {@code target} now. */
    boolean aimed(int slot, Weapon weapon, GameObject target);
}
