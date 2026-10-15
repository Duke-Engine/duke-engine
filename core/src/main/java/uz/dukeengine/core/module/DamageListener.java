package uz.dukeengine.core.module;

import uz.dukeengine.core.thing.ObjectId;

/**
 * A module told of each blow its thing's body takes and each heal it gets, at once, on the simulation thread, in the
 * thing's module order — where a building starts mending a while after its last blow, a thing catches fire, or a
 * hidden one may not hide this frame. The reference's damage and healing hooks, which its BaseRegenerate, Flammable
 * and Poisoned behaviours answer. Inferring a blow from health lower than last frame sees one landing after the
 * thing's own turn a frame late, and one smaller than what heals it not at all.
 */
public interface DamageListener {

    /**
     * A blow took health: {@code amount} after armour, of {@code type}, dealt by {@code attacker} (null for no one's),
     * on {@code frame}. A blow that takes none is not told, nor one landing on a body already dead.
     */
    default void onDamage(DamageType type, float amount, ObjectId attacker, int frame) {
    }

    /**
     * The same, told the whole blow — who dealt it, and whether it was critical ({@link Death#critical}) — where it was
     * told one; by default what the form above hears.
     */
    default void onDamage(DamageType type, float amount, Death blow, int frame) {
        onDamage(type, amount, blow == null ? null : blow.killer(), frame);
    }

    /** It was healed by {@code amount}. Nothing by default. */
    default void onHealing(float amount) {
    }
}
