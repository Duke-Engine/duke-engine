package uz.dukeengine.dungeon.world;

/**
 * What the game sounds like as a whole, where no one sound has a say.
 *
 * @param voiceGapSeconds the least time between two of the hero's lines — here rather than on each
 *     cue, because what is being prevented is two voices at once, and it is no better when they are
 *     saying different things
 */
public record Audio(float voiceGapSeconds) {

    /** What a block leaves out. */
    public static final Audio DEFAULTS = new Audio(1.5f);
}
