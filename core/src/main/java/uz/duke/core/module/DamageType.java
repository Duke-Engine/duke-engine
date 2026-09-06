package uz.duke.core.module;

/**
 * The kind of damage a weapon deals, ported in spirit from SAGE's
 * {@code DamageType}.
 *
 * <p>Damage type interacts with a target's {@link Armor}: the same weapon hurts
 * different targets differently (armor-piercing shreds tanks but barely scratches
 * infantry, flame is brutal to infantry, etc.). This is a representative subset
 * of SAGE's larger list.
 */
public enum DamageType {
    NORMAL,
    ARMOR_PIERCING,
    EXPLOSION,
    FLAME,
    SNIPER
}
