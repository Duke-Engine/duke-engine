package uz.duke.dungeon.level;

/**
 * The three things a hero is made of: strength is how much of him there is, agility
 * how fast he moves, and intelligence how much he casts out of.
 *
 * <p>One of the three is his primary, and that one is also what he hits for — see
 * {@link AttributeRules#attack}.
 */
public enum Attribute {

    STRENGTH("STR"),
    AGILITY("AGI"),
    INTELLIGENCE("INT");

    private final String shortName;

    Attribute(String shortName) {
        this.shortName = shortName;
    }

    /** The way the file writes it: STR, AGI or INT, or the whole word. */
    public static Attribute named(String word) {
        for (var attribute : values()) {
            if (attribute.shortName.equalsIgnoreCase(word)
                    || attribute.name().equalsIgnoreCase(word)) {
                return attribute;
            }
        }
        throw new IllegalArgumentException("'" + word + "' is not an attribute: STR, AGI or INT");
    }
}
