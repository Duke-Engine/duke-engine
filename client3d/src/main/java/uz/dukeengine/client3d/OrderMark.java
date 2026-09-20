package uz.dukeengine.client3d;

/**
 * The flash of arrowheads that answers a click, and everything about how it moves.
 *
 * <p>An order marker is the only thing in an RTS that talks back to the player in
 * the instant between his click and anything happening, so it is worth more care
 * than its size suggests. Warcraft III's is three arrowheads set round the spot
 * he pointed at, closing on it and going out as they arrive: the shape says
 * <em>here</em> by pointing at it from three sides, and the movement says
 * <em>now</em> without lasting long enough to clutter a fight.
 *
 * <p><b>Three numbers do nearly all of the work</b>, and all three are the kind
 * that look like fussing until you see them wrong:
 *
 * <ul>
 *   <li><b>The ease.</b> Arrowheads that close at a constant speed read as
 *       machinery. Real things arrive: fast at first and slowing as they land.
 *       {@code easePower} is how strongly — 1 is the straight line, 3 is about
 *       right.
 *   <li><b>The late fade.</b> Fading from the first frame gives a mark that is
 *       half gone before it has said anything. It should be at full strength
 *       while it travels and go out over the last stretch, which is what
 *       {@code fadeFrom} is: the point in its life the fading starts.
 *   <li><b>The turn.</b> A few degrees of rotation over the whole flight, which
 *       nobody consciously sees and everybody notices the absence of. It is the
 *       difference between three shapes sliding and three things moving.
 * </ul>
 *
 * <p>Numbers rather than art, so this costs no asset and can be re-tuned by eye
 * without a rebuild — see {@code OrderMark} in the game's own data file.
 * The client draws it; what it should look like is the game's, like the fog and
 * the pointer.
 *
 * @param startRadius  how far out the arrowheads begin, in world units
 * @param endRadius    how near the middle they have closed to when they go out;
 *                     not zero, or the last frame is three shapes on top of each
 *                     other
 * @param seconds      the whole flight. Short: this is an acknowledgement, not an
 *                     animation
 * @param size         each arrowhead from its point to its back edge
 * @param width        how wide across the back edge
 * @param height       how far above the floor it lies — enough to clear the floor
 *                     it is drawn on and not enough to sit visibly above it
 * @param easePower    how strongly it slows as it arrives; 1 is a constant speed
 * @param fadeFrom     the share of its life that passes at full strength before
 *                     it starts going out
 * @param spinDegrees  how far the whole set turns over the flight
 * @param brightness   what the colour is multiplied by. Over 1 on purpose: the
 *                     mark is drawn additively, so this is what lets it read
 *                     through torchlight and fog
 * @param ringRadius   how wide the circle round a creature an attack was ordered
 *                     on is drawn. An order given <em>to</em> something is not the
 *                     same kind of answer as an order given to a piece of floor:
 *                     the arrowheads say "there", and a creature needs "that one"
 * @param blinks       how many times that circle goes out and comes back over its
 *                     life. Two is the whole of it — one reads as a glitch and
 *                     three is a warning light
 * @param moveColour   packed RGB for "go there"
 * @param attackColour packed RGB for "kill that"
 */
public record OrderMark(float startRadius, float endRadius, float seconds, float size,
        float width, float height, float easePower, float fadeFrom, float spinDegrees,
        float brightness, float ringRadius, int blinks, int moveColour, int attackColour) {

    /** What a game that asks for nothing gets. Tuned by eye at a dungeon's scale. */
    public static final OrderMark DEFAULTS = new OrderMark(
            7f, 1f, 0.40f, 3.5f, 3f, 0.25f, 3f, 0.6f, 22f, 1.6f, 7f, 2, 0x3CFF6E, 0xFF4436);

    public OrderMark {
        startRadius = Math.max(0f, startRadius);
        endRadius = Math.max(0f, endRadius);
        seconds = Math.max(0.01f, seconds);
        size = Math.max(0.01f, size);
        width = Math.max(0.01f, width);
        easePower = Math.max(1f, easePower);
        fadeFrom = Math.clamp(fadeFrom, 0f, 0.99f);
        brightness = Math.max(0f, brightness);
        ringRadius = Math.max(0.01f, ringRadius);
        blinks = Math.max(1, blinks);
    }

    /**
     * Where the arrowheads are and how bright, at one instant of one mark.
     *
     * @param radius      how far each is from the spot that was clicked
     * @param alpha       1 while it travels, falling to 0 as it arrives
     * @param spinRadians how far the set has turned from where it started
     */
    public record Step(float radius, float alpha, float spinRadians) {
    }

    /**
     * The mark {@code age} seconds after it was made.
     *
     * <p>All of the movement, and none of the drawing — so the shape of the thing
     * can be checked by arithmetic rather than by watching for it.
     */
    public Step at(float age) {
        float t = Math.clamp(age / seconds, 0f, 1f);
        // Ease-out: most of the distance is covered early and the arrival is slow.
        float eased = 1f - (float) Math.pow(1f - t, easePower);
        return new Step(
                startRadius + (endRadius - startRadius) * eased,
                t <= fadeFrom ? 1f : 1f - (t - fadeFrom) / (1f - fadeFrom),
                (float) Math.toRadians(spinDegrees) * eased);
    }

    /** Whether a mark that old has finished and should not be drawn. */
    public boolean spent(float age) {
        return age >= seconds;
    }

    /**
     * How brightly the ring round an attacked creature is drawn, {@code age}
     * seconds in: on, off, on, off.
     *
     * <p>A flat blink rather than the arrowheads' ease, and rather than a fade.
     * The two orders are not the same kind of answer and should not look alike: a
     * walk is somewhere, and the arrowheads close on it; an attack is
     * <em>somebody</em>, and what the player needs is that creature picked out of
     * a crowd of them. Something that goes hard on and hard off twice is the
     * oldest way of doing that and still the one the eye finds fastest — a gentle
     * fade in the middle of a fight is a thing nobody sees.
     */
    public float blinkAt(float age) {
        if (spent(age) || age < 0f) {
            return 0f;
        }
        float through = Math.clamp(age / seconds, 0f, 1f) * Math.max(1, blinks);
        return through - (float) Math.floor(through) < 0.5f ? 1f : 0f;
    }
}
