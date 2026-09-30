package uz.dukeengine.combat.module;

import uz.dukeengine.core.thing.ObjectId;

/**
 * One shot a weapon took, as a {@link ProjectileLauncher} is handed it and {@link WeaponUpdate#land lands} it:
 * who fired it and for which side, which weapon from which slot, and the damage it carries.
 *
 * <p>Everything a landing needs, carried rather than looked up again when it arrives — by then the shooter may
 * be dead and gone, and the side it fired for is still the side its blast spares.
 *
 * @param shooter what fired it
 * @param side    the player it fired for: whose enemies its blast catches
 * @param weapon  the weapon that fired, with its name, splash radius and damage type
 * @param slot    which of the set's slots it fired from, the first 0
 * @param damage          what it deals before the victim's armour, every modifier and bonus already applied — and
 *                        its blast within {@code radius}
 * @param radius          how wide its blast is, the game's bonuses for the shooter applied as it fired
 * @param secondaryDamage what its blast deals beyond {@code radius} and within {@code secondaryRadius}, applied as
 *                        {@code damage} is
 * @param secondaryRadius how far its blast's second ring reaches, as {@code radius} is; 0 for none
 * @param wentOff         the template of what went off where it is not the shooter — a shell the shooter launched —
 *                        or null: its blast spares things of that template allied to it ({@code NOT_SIMILAR}), and
 *                        its launcher rather than the shooter's maker
 */
public record Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage, float radius,
        float secondaryDamage, float secondaryRadius, String wentOff) {

    /** A shot the shooter itself goes off with, as every shot was before one could say otherwise. */
    public Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage, float radius,
            float secondaryDamage, float secondaryRadius) {
        this(shooter, side, weapon, slot, damage, radius, secondaryDamage, secondaryRadius, null);
    }

    /** This shot gone off as {@code template} — the shell a launcher made of it — rather than as its shooter. */
    public Shot wentOffAs(String template) {
        return new Shot(shooter, side, weapon, slot, damage, radius, secondaryDamage, secondaryRadius, template);
    }

    /** A shot whose blast is its weapon's own, with no bonus to it. */
    public Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage) {
        this(shooter, side, weapon, slot, damage, weapon == null ? 0f : weapon.splashRadius());
    }

    /** A shot whose second ring is its weapon's own, with no bonus to it. */
    public Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage, float radius) {
        this(shooter, side, weapon, slot, damage, radius, weapon == null ? 0f : weapon.secondaryDamage(),
                weapon == null ? 0f : weapon.secondaryRadius());
    }
}
