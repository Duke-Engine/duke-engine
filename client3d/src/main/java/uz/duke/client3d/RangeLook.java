package uz.duke.client3d;

/**
 * What a skill's reach looks like while the player is deciding.
 *
 * <p>One look for every skill in the game, on purpose. A player learns this
 * drawing once — a dashed ring is how far, a solid disc is what it covers, red
 * means the click will be refused — and from then on a skill he has never used
 * before still answers his questions. Giving each skill its own colours would
 * make him learn four pictures instead of one language.
 *
 * <p>Everything here is a number in the game's data file and nothing is in Java,
 * because these are the values that have to be found by eye. See
 * {@code DungeonIndicator}.
 *
 * @param bandWidth   how thick the dashed ring is, in world units. Thin enough to
 *                    be a line and thick enough to survive being far away
 * @param dashes      how many dashes go round the ring. The dash is what stops it
 *                    reading as a solid wall the hero cannot cross
 * @param dashShare   how much of each dash's slot is drawn rather than skipped
 * @param fillAlpha   how strongly the inside of a ring is washed in. Faint: it is
 *                    there to say "inside", not to hide the floor
 * @param edgeAlpha   how strongly the ring itself is drawn
 * @param height      how far above the floor it lies, like every other flat mark
 * @param openSeconds how long it takes to open out to its full size when it
 *                    appears. A ring that pops into place reads as a screenshot;
 *                    one that opens reads as the hero preparing
 * @param spinPerSecond how fast the dashes travel round, in degrees. Slow: the
 *                    movement is what keeps it alive on screen while the player
 *                    thinks, and anything quick turns into a siren
 * @param pulseDepth  how much the ring breathes, as a share of its brightness
 * @param pulsePerSecond how often it breathes
 * @param segments    how many straight pieces the circle is really made of
 * @param allowColour packed RGB for a cast that will go through
 * @param denyColour  packed RGB for one that will not — stone, the dark, or out
 *                    of reach
 * @param areaColour  packed RGB for the blast itself, which is a different
 *                    question from whether it is allowed
 * @param brightness  what every colour is multiplied by; over 1 because these are
 *                    drawn additively, like the order mark
 */
public record RangeLook(float bandWidth, int dashes, float dashShare, float fillAlpha,
        float edgeAlpha, float height, float openSeconds, float spinPerSecond,
        float pulseDepth, float pulsePerSecond, int segments, int allowColour,
        int denyColour, int areaColour, float brightness) {

    /** What a game that asks for nothing gets. */
    public static final RangeLook DEFAULT = new RangeLook(
            1.6f, 48, 0.55f, 0.10f, 0.85f, 0.3f, 0.18f, 9f, 0.25f, 1.4f, 96,
            0x53E0FF, 0xFF4436, 0xFFB347, 1.5f);

    public RangeLook {
        bandWidth = Math.max(0.05f, bandWidth);
        dashes = Math.max(1, dashes);
        dashShare = Math.clamp(dashShare, 0.05f, 1f);
        fillAlpha = Math.clamp(fillAlpha, 0f, 1f);
        edgeAlpha = Math.clamp(edgeAlpha, 0f, 1f);
        openSeconds = Math.max(0.001f, openSeconds);
        pulseDepth = Math.clamp(pulseDepth, 0f, 1f);
        pulsePerSecond = Math.max(0f, pulsePerSecond);
        segments = Math.clamp(segments, 12, 512);
        brightness = Math.max(0f, brightness);
    }

    /**
     * How far open the ring is, {@code age} seconds after it appeared.
     *
     * <p>The same ease the order mark uses, for the same reason and so that the
     * two read as one game: fast at first and settling rather than arriving.
     */
    public float openness(float age) {
        float t = Math.clamp(age / openSeconds, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** How bright it is this instant, with the breathing in it. */
    public float breath(float seconds) {
        if (pulseDepth <= 0f || pulsePerSecond <= 0f) {
            return 1f;
        }
        double phase = seconds * pulsePerSecond * 2 * Math.PI;
        return 1f - pulseDepth * 0.5f * (1f - (float) Math.cos(phase));
    }

    /** How far round the dashes have travelled by now, in radians. */
    public float spinAt(float seconds) {
        return (float) Math.toRadians(spinPerSecond * seconds);
    }
}
