package uz.dukeengine.rts.module;

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
 * @param damage  what it deals before the victim's armour, every modifier and bonus already applied
 * @param radius  how wide its blast is, the game's bonuses for the shooter applied as it fired
 */
public record Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage, float radius) {

    /** A shot whose blast is its weapon's own, with no bonus to it. */
    public Shot(ObjectId shooter, int side, Weapon weapon, int slot, float damage) {
        this(shooter, side, weapon, slot, damage, weapon == null ? 0f : weapon.splashRadius());
    }
}
