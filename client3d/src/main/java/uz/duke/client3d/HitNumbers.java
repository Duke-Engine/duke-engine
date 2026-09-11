package uz.duke.client3d;

/**
 * The numbers that float off a creature as it is hurt or healed.
 *
 * <p>A health bar says how much is left; it does not say <em>how hard that
 * landed</em>, and in a fight that is the question. A player who can see 34 come
 * off a skeleton and 9 come off him is being told which of them is winning and by
 * how much, and he is being told it in the half second he has to act on it. It is
 * the oldest readable thing in the genre for exactly that reason.
 *
 * <p><b>Read off the snapshot rather than sent.</b> A number is not an event this
 * game posts: it is the difference between what a creature's health was last
 * frame and what it is now — see {@link HealthWatch}. That covers every way
 * health can move, including the ones this game does not own: a monster's swing
 * lands inside the engine's own weapon, and a game that had to post an event for
 * each source would be silent for exactly the blows the player is being hit by.
 * It also means nothing about the simulation changes to draw these.
 *
 * <p>Everything here is a number in the game's data file. See
 * {@code DungeonHitNumbers}.
 *
 * @param seconds      how long one stays up. Short: a fight makes them faster
 *                     than they can be read one at a time, and what the player
 *                     reads is the <em>size</em> of what is going past
 * @param rise         how far up the screen it drifts over its life, in pixels
 * @param drift        how far sideways, alternating left and right so that two
 *                     landing together do not sit on top of each other
 * @param textScale    how much bigger than the interface font it is drawn
 * @param fadeFrom     the share of its life it is at full strength before going
 *                     out — the same late fade the order mark uses
 * @param height       how far above the floor the creature's number starts, in
 *                     world units: over its head rather than at its feet
 * @param leastWorth   the smallest number worth drawing. A trickle of regeneration
 *                     one point at a time is not news and would never stop
 * @param hurtColour   packed RGB for damage to somebody else — what he is dealing
 * @param takenColour  packed RGB for damage to one of his — what he is taking,
 *                     which is the one he has to notice
 * @param healColour   packed RGB for health going back on
 * @param brightness   what every colour is multiplied by, as these are drawn
 *                     against a lit scene
 */
public record HitNumbers(float seconds, float rise, float drift, float textScale,
        float fadeFrom, float height, float leastWorth, int hurtColour, int takenColour,
        int healColour, float brightness) {

    /** What a game that asks for nothing gets. */
    public static final HitNumbers DEFAULT = new HitNumbers(
            0.9f, 70f, 18f, 1.35f, 0.45f, 14f, 1f, 0xFFE9A8, 0xFF5348, 0x6BE86B, 1f);

    public HitNumbers {
        seconds = Math.max(0.05f, seconds);
        textScale = Math.max(0.1f, textScale);
        fadeFrom = Math.clamp(fadeFrom, 0f, 0.99f);
        leastWorth = Math.max(0f, leastWorth);
        brightness = Math.max(0f, brightness);
    }

    /**
     * Where one is and how bright, {@code age} seconds after it appeared.
     *
     * @param up    how far it has risen, in pixels
     * @param aside how far it has drifted sideways
     * @param alpha 1 while it is being read, falling to 0 as it goes
     */
    public record Step(float up, float aside, float alpha) {
    }

    /**
     * One number at one instant.
     *
     * <p>The rise eases <em>out</em> — quick off the creature and slowing as it
     * goes — for the same reason the order mark's arrowheads do: a number that
     * travels at a constant speed reads as a thing being moved rather than a thing
     * that was knocked loose. The sideways drift is linear, because it is only
     * there to keep two numbers apart.
     *
     * @param side which way this one leans, {@code -1} or {@code 1}
     */
    public Step at(float age, int side) {
        float t = Math.clamp(age / seconds, 0f, 1f);
        float eased = 1f - (1f - t) * (1f - t);
        return new Step(rise * eased, drift * t * side,
                t <= fadeFrom ? 1f : 1f - (t - fadeFrom) / (1f - fadeFrom));
    }

    /** Whether a number that old has finished and should not be drawn. */
    public boolean spent(float age) {
        return age >= seconds;
    }

    /** Which colour a change of this kind is drawn in. */
    public int colourOf(boolean healed, boolean his) {
        if (healed) {
            return healColour;
        }
        return his ? takenColour : hurtColour;
    }
}
