package uz.duke.client3d;

/**
 * The numbers that come off a creature as it is hurt or healed.
 *
 * <p>A health bar says how much is left; it does not say <em>how hard that
 * landed</em>, and in a fight that is the question. A player who can see 34 come
 * off a skeleton and 9 come off him is being told which of them is winning and by
 * how much, and he is being told it in the half second he has to act on it. It is
 * the oldest readable thing in the genre for exactly that reason.
 *
 * <p><b>It punches rather than drifts.</b> The first version floated the number
 * upward and faded it out, which is what a dozen games do and which is wrong for
 * a number: something moving across the screen has to be tracked to be read, and
 * something translucent has to be read off whatever is behind it. Both cost the
 * player the fraction of a second the number was for. So it appears
 * <em>large</em>, on the spot, and shrinks to its own size — the eye catches the
 * change of size without following anything — and then it is simply gone. Solid
 * the whole time: it is a number, not a ghost.
 *
 * <p>And it is quick. A number that is still on screen while the next blow lands
 * is in the way of the thing it exists to announce.
 *
 * <p><b>Read off the snapshot rather than sent.</b> A number is not an event this
 * game posts: it is the difference between what a creature's health was last
 * frame and what it is now — see {@link HealthWatch}. That covers every way
 * health can move, including the ones this game does not own: a monster's swing
 * lands inside the engine's own weapon, and a game that had to post an event for
 * each source would be silent for exactly the blows the player is being hit by.
 *
 * <p>Everything here is a number in the game's data file: a {@code HitNumbers} block, which
 * the game reads straight into this record.
 *
 * @param seconds     how long one stays up. Short on purpose — see above
 * @param popScale    how much bigger than its final size it appears, as a
 *                    multiple. The whole of the movement is this coming back to 1
 * @param textScale   how big it settles at, against the interface font. Small:
 *                    the number is read from its size changing, not from its bulk
 * @param spread      how far out from the creature a number bursts before it
 *                    stops, in pixels. Several landing together each take a
 *                    different direction out of the same point, so two or five
 *                    can be read rather than one being drawn over another
 * @param fanDegrees  how far apart those directions are, fanned either side of
 *                    straight up. Upward on purpose: below the creature is where
 *                    its health bar and its feet are
 * @param height      how far above the floor the creature's number sits, in world
 *                    units — over its head rather than at its feet
 * @param leastWorth  the smallest number worth drawing. A trickle of regeneration
 *                    one point at a time is not news and would never stop
 * @param hurtColour  packed RGB for damage to somebody else — what he is dealing
 * @param takenColour packed RGB for damage to one of his — what he is taking,
 *                    which is the one he has to notice
 * @param healColour  packed RGB for health going back on
 * @param brightness  what every colour is multiplied by
 */
public record HitNumbers(float seconds, float popScale, float textScale, float spread,
        float fanDegrees, float height, float leastWorth, int hurtColour, int takenColour,
        int healColour, float brightness) {

    /** What a game that asks for nothing gets. */
    public static final HitNumbers DEFAULTS = new HitNumbers(
            0.40f, 2.1f, 0.85f, 34f, 38f, 14f, 1f, 0xFFE0A0, 0xC22A22, 0x4FBF4F, 1f);

    public HitNumbers {
        seconds = Math.max(0.05f, seconds);
        popScale = Math.max(1f, popScale);
        textScale = Math.max(0.1f, textScale);
        leastWorth = Math.max(0f, leastWorth);
        brightness = Math.max(0f, brightness);
    }

    /**
     * How big the number is, {@code age} seconds after it landed.
     *
     * <p>Large at once and settling — the shrink eases <em>out</em>, so nearly all
     * of it is over in the first third of the number's life and the rest is the
     * number sitting still at its own size. That is the shape the eye reads as a
     * hit; a linear shrink over the whole life reads as something deflating.
     */
    public float scaleAt(float age) {
        float t = Math.clamp(age / seconds, 0f, 1f);
        float left = 1f - t;
        return 1f + (popScale - 1f) * left * left;
    }

    /**
     * How far along its own direction a number has burst, {@code age} seconds in.
     *
     * <p>All of it is over early -- it leaves the creature and stops, rather than
     * travelling for as long as it is up. What the player follows is the size
     * changing; this is only what keeps two numbers off each other.
     */
    public float outAt(float age) {
        float t = Math.clamp(age / seconds, 0f, 1f);
        return spread * (1f - (1f - t) * (1f - t));
    }

    /**
     * Which way the {@code nth} number to come off one creature goes, in radians
     * from straight up.
     *
     * <p>Straight up first, then alternating either side of it, so the first is
     * where the eye already is and the rest open out from it. Two blows in one
     * instant are two numbers a player can read; two numbers in the same place are
     * one number he cannot.
     */
    public float directionOf(int nth) {
        int step = (nth + 1) / 2;
        float sign = nth % 2 == 1 ? 1f : -1f;
        return (float) Math.toRadians(fanDegrees * step) * sign;
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
