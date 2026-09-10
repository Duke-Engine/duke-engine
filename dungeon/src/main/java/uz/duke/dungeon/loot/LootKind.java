package uz.duke.dungeon.loot;

/**
 * What a thing found on the floor is worth.
 *
 * <p>Three, and deliberately the same three a level gives: what he hits for, how
 * much of him there is, and how much of a blow gets through. A dungeon's whole
 * economy is those numbers, and a fourth kind would be a fourth thing for the
 * player to hold in his head for no more decision than he already has.
 *
 * <p>Which items exist, what they are called and what each is worth is written in
 * {@code dungeon.ini}. This is the part that needs Java, in the same way
 * {@link uz.duke.dungeon.power.PowerEffect} is.
 */
public enum LootKind {

    /** Adds to his weapon damage, as a percentage of what it was. */
    ATTACK,

    /** Adds flat maximum health, and the health to go with it. */
    HEALTH,

    /** Takes a percentage off what reaches him. */
    ARMOUR
}
