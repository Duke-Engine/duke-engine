package uz.dukeengine.client3d;

/**
 * The ring a skill draws on the floor while it is being aimed.
 *
 * @param bandWidth      how thick the ring is drawn
 * @param segments       how many straight pieces the circle is really made of
 * @param fillAlpha      how strongly the inside of a ring is washed in
 * @param edgeAlpha      how strongly the ring itself is drawn
 * @param height         how far above the floor it lies
 * @param pulseDepth     how much the ring breathes
 * @param pulsePerSecond how often it breathes
 * @param selfRadius     how wide the ring hugging the hero is when a skill only affects him — a self
 *     buff has no reach to draw, so the size that says "only me" is his own width and a little more
 * @param brightness     what every ring colour is multiplied by; over 1, because it is added
 * @param allowColour    the colour of a cast that will go through
 * @param denyColour     the colour of one that will not
 * @param areaColour     the colour of the blast itself
 */
public record SkillRing(float bandWidth, int segments, float fillAlpha, float edgeAlpha, float height,
        float pulseDepth, float pulsePerSecond, float selfRadius, float brightness, int allowColour,
        int denyColour, int areaColour) {

    /** What a block leaves out. */
    public static final SkillRing DEFAULTS = new SkillRing(1.6f, 96, 0.10f, 0.85f, 0.3f, 0.25f, 1.4f, 5f, 1.5f,
            0x53E0FF, 0xFF4436, 0xFFB347);
}
