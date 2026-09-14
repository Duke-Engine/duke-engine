package uz.duke.dungeon.level;

/**
 * What a point of each attribute is worth — {@code DungeonAttributes} in the file.
 *
 * <p>Every coefficient is held in <b>hundredths</b> (0.15 of speed a point is 15) and
 * every attribute in tenths, so each figure is one product of two integers divided once.
 * Nothing is accumulated and nothing depends on the order things happened in: the same
 * attributes give the same bits on every machine.
 *
 * @param healthPerStrength   maximum health a point of strength adds
 * @param speedPerAgility     movement speed a point of agility adds
 * @param manaPerIntelligence maximum mana a point of intelligence adds
 * @param damagePerPrimary    attack a point of his primary attribute adds
 */
public record AttributeRules(int healthPerStrength, int speedPerAgility,
        int manaPerIntelligence, int damagePerPrimary) {

    /** A coefficient's unit is this many of the numbers it is held in. */
    public static final int HUNDREDTHS = 100;

    public static final AttributeRules NONE = new AttributeRules(0, 0, 0, 0);

    private static final long SCALE = (long) Attributes.TENTHS * HUNDREDTHS;

    /** Maximum health his strength is worth, in whole points, rounded down. */
    public int health(Attributes attributes) {
        return Math.toIntExact(Math.floorDiv(
                Math.multiplyExact((long) attributes.strength(), healthPerStrength), SCALE));
    }

    /** Maximum mana his intelligence is worth, in whole points, rounded down. */
    public int mana(Attributes attributes) {
        return Math.toIntExact(Math.floorDiv(
                Math.multiplyExact((long) attributes.intelligence(), manaPerIntelligence), SCALE));
    }

    /** Movement speed his agility is worth: one exact integer, divided once. */
    public float speed(Attributes attributes) {
        return Math.multiplyExact((long) attributes.agility(), speedPerAgility) / (float) SCALE;
    }

    /**
     * What his primary attribute adds to his attack.
     *
     * <p>Only the primary. Strength on an archer is health and nothing else; strength on
     * a knight is health and his swing both, which is the whole of what being his
     * primary means.
     */
    public float attack(Attributes attributes, Attribute primary) {
        if (primary == null) {
            return 0f;
        }
        return Math.multiplyExact((long) attributes.of(primary), damagePerPrimary) / (float) SCALE;
    }
}
