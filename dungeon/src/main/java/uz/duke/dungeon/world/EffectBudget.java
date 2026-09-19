package uz.duke.dungeon.world;

/**
 * What the client may spend on effects. Ceilings, not targets: a fight is not one arrow — fifty in
 * the air, each with a hundred sparks and a light of its own, is five thousand particles and fifty
 * dynamic lights, and dynamic lights are the expensive kind. Past a ceiling a shot flies plainer,
 * never differently.
 *
 * @param maxRings     how many skill rings may be open across the floor at once. Its own ceiling
 *     rather than a share of the bursts', because a ring is a vertex buffer and two materials while
 *     a burst is particles and a light
 * @param maxDistance  how far from the camera a thing is still worth the trouble; 0 for no limit
 * @param maxParticles how many particles may burn at once across every effect — past it a layer is
 *     drawn thinner, and past that it is not drawn; 0 for no limit
 */
public record EffectBudget(int maxLights, int maxRings, int maxPerEffect, int maxBursts, float maxDistance,
        int maxParticles) {

    /** What a block leaves out. */
    public static final EffectBudget DEFAULTS = new EffectBudget(4, 6, 8, 8, 0f, 0);
}
