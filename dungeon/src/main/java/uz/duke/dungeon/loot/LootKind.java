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
 * {@code dungeon.ini}. This is the part that needs Java.
 */
public enum LootKind {

    /** Adds to his weapon damage, as a percentage of what it was. */
    ATTACK,

    /** Adds flat maximum health, and the health to go with it. */
    HEALTH,

    /** Takes a percentage off what reaches him. */
    ARMOUR,

    /**
     * Adds flat maximum mana, and the mana to go with it.
     *
     * <p>A bigger pool rather than a draught out of a flask, which is the shape
     * everything else in this bag has: what the dungeon drops is permanent, it
     * goes in the grid and it stays there. A potion would be a thing he carries
     * and chooses to drink, and there is nothing in the game he carries and
     * chooses to do anything with — the bag is a record of what made him
     * stronger, not an inventory.
     *
     * <p>So it is a bigger purse, and it fills by the amount it grows because
     * {@code SkillBook.poolOf} gifts what it adds. Finding one in a fight is
     * therefore a draught as well, which is the part a potion was wanted for.
     */
    MANA
}
