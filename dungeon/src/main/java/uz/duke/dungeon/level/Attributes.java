package uz.duke.dungeon.level;

/**
 * Strength, agility and intelligence, held in <b>tenths</b> of a point.
 *
 * <p>Tenths because a level adds 1.8 of something and a hero is fifteen levels of it.
 * Summed as floats that is a number two machines can disagree about in its last bit, and
 * what it becomes — his health — is inside the checksum. As whole tenths it is the same
 * integer everywhere, and every operation here is exact or fails loudly.
 */
public record Attributes(int strength, int agility, int intelligence) {

    /** A point is this many of the units these are held in. */
    public static final int TENTHS = 10;

    public static final Attributes NONE = new Attributes(0, 0, 0);

    /** Whole points, as an item or a test would name them. */
    public static Attributes ofWhole(int strength, int agility, int intelligence) {
        return new Attributes(Math.multiplyExact(strength, TENTHS),
                Math.multiplyExact(agility, TENTHS), Math.multiplyExact(intelligence, TENTHS));
    }

    public int of(Attribute attribute) {
        return switch (attribute) {
            case STRENGTH -> strength;
            case AGILITY -> agility;
            case INTELLIGENCE -> intelligence;
        };
    }

    public Attributes plus(Attributes other) {
        return new Attributes(Math.addExact(strength, other.strength),
                Math.addExact(agility, other.agility),
                Math.addExact(intelligence, other.intelligence));
    }

    /** This many times over, as one multiplication rather than a running sum. */
    public Attributes times(int count) {
        return new Attributes(Math.multiplyExact(strength, count),
                Math.multiplyExact(agility, count), Math.multiplyExact(intelligence, count));
    }

    /** What the panel shows: whole points, rounded down. */
    public int whole(Attribute attribute) {
        return Math.floorDiv(of(attribute), TENTHS);
    }
}
