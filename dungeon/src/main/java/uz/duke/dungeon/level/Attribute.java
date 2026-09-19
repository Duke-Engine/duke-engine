package uz.duke.dungeon.level;

/**
 * One attribute a hero may have: how his block names it, how the panel shows it, and what a
 * point of it is worth.
 *
 * <p>Data rather than a list in Java. A new attribute is an {@code Attribute} block, a line in
 * each hero's block and a picture, and nothing here changes. What a point can be worth is any of
 * the three figures an attribute moves at all; whichever attribute is a hero's primary is also
 * his blow, and that is {@link AttributeRules#damagePerPrimary}'s, not the attribute's. A figure
 * the block leaves out, a point of it adds none of.
 *
 * @param name           the block's name — Strength
 * @param shortName      how a hero's block names it — STR; its name when the block gives none
 * @param word           what the panel calls it; its name when the block gives none
 * @param icon           the picture beside it on the panel
 * @param healthPerPoint maximum health a point adds
 * @param speedPerPoint  movement speed a point adds
 * @param manaPerPoint   maximum mana a point adds
 */
public record Attribute(String name, String shortName, String word, String icon, Hundredths healthPerPoint,
        Hundredths speedPerPoint, Hundredths manaPerPoint) {

    /** What a block leaves out. */
    public static final Attribute DEFAULTS = new Attribute("", "", "", "", Hundredths.ZERO, Hundredths.ZERO,
            Hundredths.ZERO);

    public Attribute {
        shortName = shortName == null || shortName.isBlank() ? name : shortName;
        word = word == null || word.isBlank() ? name : word;
        icon = icon == null ? "" : icon;
    }

    /** Whether the file means this one by {@code word}: its short name or its whole one. */
    public boolean isNamed(String word) {
        return word != null && (shortName.equalsIgnoreCase(word) || name.equalsIgnoreCase(word));
    }
}
