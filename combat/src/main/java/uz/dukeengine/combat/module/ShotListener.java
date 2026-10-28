package uz.dukeengine.combat.module;

import uz.dukeengine.core.thing.GameObject;

/**
 * A module told what its thing's weapons do: each shot as it is fired, and each blow a shot of its deals — to its
 * victim, where it hits, and to what its blast catches — how much after armour, the amount the blow's {@code
 * ObjectHurt} says. On the simulation thread, in the order they happen, a shot's firing before its blows: a swing's
 * frame for the look of it, a lifesteal's share of what was dealt. A shot carried by a {@link ProjectileLauncher} deals
 * its blows the frame it lands; a thing gone by then hears nothing of them.
 */
public interface ShotListener {

    /** A weapon of its thing fired {@code shot} at {@code victim}. */
    default void onFired(Shot shot, GameObject victim) {
    }

    /** A shot of its thing's dealt {@code damage}, after armour, to {@code victim}. */
    default void onDealt(Shot shot, GameObject victim, float damage) {
    }

    /**
     * A weapon of its thing counted {@code shot} — at a thing, the ground or a point — and its slot now waits {@code
     * frames} before it may fire again: its delay between shots, or, where the shot {@code emptied} its clip, the
     * clip's reload (0 for a clip that does not reload itself), each divided by its rate of fire. What the reference's
     * {@code ShareWeaponReloadTime} hands every other slot ({@link WeaponUpdate#delaySlots}).
     */
    default void onSpent(Shot shot, int frames, boolean emptied) {
    }
}
