package uz.duke.dungeon.level;

/**
 * One hero's attributes as his {@code Hero} block writes them: what he starts
 * with, what every level adds, and which of them is his primary.
 *
 * @param primary  where his primary stands in the file's list of attributes, or -1 for a
 *                 hero the file gives no attributes at all — he is then exactly his
 *                 creature block
 * @param base     at the first level, in tenths
 * @param perLevel added by each level after the first, in tenths
 */
public record HeroAttributes(int primary, Attributes base, Attributes perLevel) {

    public static final HeroAttributes NONE =
            new HeroAttributes(-1, Attributes.NONE, Attributes.NONE);

    public boolean hasPrimary() {
        return primary >= 0;
    }

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
