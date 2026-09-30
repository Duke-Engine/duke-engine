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
}
