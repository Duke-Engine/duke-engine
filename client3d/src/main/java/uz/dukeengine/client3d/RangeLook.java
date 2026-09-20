package uz.dukeengine.client3d;

/**
 * What a skill's reach looks like while the player is deciding.
 *
 * <p>One look for every skill in the game, on purpose. A player learns this
 * drawing once — a ring is how far, a filled disc is what it covers, red means
 * the click will be refused — and from then on a skill he has never used before
 * still answers his questions. Giving each skill its own colours would make him
 * learn four pictures instead of one language.
 *
 * <p><b>It does not move.</b> The first version of this opened out when it
 * appeared and turned slowly while it was up, which is what the genre does and
 * which was wrong here: an indicator is a ruler, and a ruler that is still
 * settling is a ruler you have to wait for. It appears at its size, at once, and
 * stands still until the decision is made. What is left of the movement is a slow
 * breath, so that it reads as live rather than as painted on — and that can be
 * turned off too.
 *
 * <p>Everything here is a number in the game's data file and nothing is in Java,
 * because these are the values that have to be found by eye. See
 * {@code SkillRing}.
 *
 * @param bandWidth   how thick the ring is, in world units. Thin enough to be a
 *                    line and thick enough to survive being far away
 * @param fillAlpha   how strongly the inside of a ring is washed in. Faint: it is
 *                    there to say "inside", not to hide the floor
 * @param edgeAlpha   how strongly the ring itself is drawn
 * @param height      how far above the floor it lies, like every other flat mark
 * @param pulseDepth  how much the ring breathes, as a share of its brightness;
 *                    zero for a ring that does not move at all
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
public record RangeLook(float bandWidth, float fillAlpha, float edgeAlpha, float height,
        float pulseDepth, float pulsePerSecond, int segments, int allowColour,
        int denyColour, int areaColour, float brightness) {

    /** What a game that asks for nothing gets. */
    public static final RangeLook DEFAULT = new RangeLook(
            1.6f, 0.10f, 0.85f, 0.3f, 0.25f, 1.4f, 96, 0x53E0FF, 0xFF4436, 0xFFB347, 1.5f);

    public RangeLook {
        bandWidth = Math.max(0.05f, bandWidth);
        fillAlpha = Math.clamp(fillAlpha, 0f, 1f);
        edgeAlpha = Math.clamp(edgeAlpha, 0f, 1f);
        pulseDepth = Math.clamp(pulseDepth, 0f, 1f);
        pulsePerSecond = Math.max(0f, pulsePerSecond);
        segments = Math.clamp(segments, 12, 512);
        brightness = Math.max(0f, brightness);
    }

    /** How bright it is this instant, with the breathing in it. */
    public float breath(float seconds) {
        if (pulseDepth <= 0f || pulsePerSecond <= 0f) {
            return 1f;
        }
        double phase = seconds * pulsePerSecond * 2 * Math.PI;
        return 1f - pulseDepth * 0.5f * (1f - (float) Math.cos(phase));
    }
}
