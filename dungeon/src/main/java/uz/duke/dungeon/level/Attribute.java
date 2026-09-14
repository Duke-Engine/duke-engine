package uz.duke.dungeon.level;

/**
 * One attribute the file describes: how a hero's block names it, and what a point of it
 * is worth.
 *
 * <p>Data rather than a list in Java. A new attribute is a {@code DungeonAttribute} block,
 * a line in each hero's block and a picture, and nothing here changes — see
 * {@code dungeon.ini}. What a point can be worth is any of the three figures an attribute
 * moves at all; whichever attribute is a hero's primary is also his blow, and that is
 * {@link AttributeRules#damagePerPrimary}'s, not the attribute's.
 *
 * @param name           the block's name — Strength
 * @param shortName      how a hero's block names it — STR
 * @param healthPerPoint maximum health a point adds, in hundredths
 * @param speedPerPoint  movement speed a point adds, in hundredths
 * @param manaPerPoint   maximum mana a point adds, in hundredths
 */
public record Attribute(String name, String shortName, int healthPerPoint, int speedPerPoint,
        int manaPerPoint) {

    /** Whether the file means this one by {@code word}: its short name or its whole one. */
    public boolean isNamed(String word) {
        return word != null && (shortName.equalsIgnoreCase(word) || name.equalsIgnoreCase(word));
    }
}
