package uz.duke.dungeon.level;

/**
 * One hero's attributes as his {@code DungeonHero} block writes them: what he starts
 * with, what every level adds, and which of the three is his primary.
 *
 * @param primary  the attribute that is also his attack, or {@code null} for a hero the
 *                 file gives no attributes at all — he is then exactly his creature block
 * @param base     at the first level, in tenths
 * @param perLevel added by each level after the first, in tenths
 */
public record HeroAttributes(Attribute primary, Attributes base, Attributes perLevel) {

    public static final HeroAttributes NONE =
            new HeroAttributes(null, Attributes.NONE, Attributes.NONE);

    /**
     * What he has at this level.
     *
     * <p>Computed from the level in one step, never added up level by level: a hero at
     * level seven is the same hero however he got there, and a jump of two levels at once
     * is worth exactly two.
     */
    public Attributes atLevel(int level) {
        return base.plus(perLevel.times(Math.max(0, level - Levelling.FIRST_LEVEL)));
    }
}
