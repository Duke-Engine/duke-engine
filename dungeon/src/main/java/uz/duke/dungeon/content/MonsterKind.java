package uz.duke.dungeon.content;

/**
 * One kind of monster, as the dungeon's data file describes it.
 *
 * <p>Everything that makes a runner different from a brute is here or in
 * {@code creatures.ini} — nothing is in Java. Health, speed and damage are unit
 * stats and belong in the creature file where the engine's template loader reads
 * them; what is left is how the thing <em>behaves</em>, which no template field
 * can express, and how the dungeon decides to use it.
 *
 * <p>There is one brain, not one per kind. An archer is not a different mind
 * from a brute — it is the same mind that stops further away and shoots from
 * there. Writing a class per monster would mean the fifth kind costs a class and
 * the twentieth costs twenty; naming the parameters means a new kind costs a
 * block of INI and nothing else.
 *
 * @param name         the creature template, and the behaviour tag derived from it
 * @param senseRadius  how far it notices the hero
 * @param chaseRadius  how far it follows once roused
 * @param closeDistance how close it walks before stopping to attack — the whole
 *                     difference between something that closes and something that
 *                     shoots from a distance
 * @param repathFrames frames between re-planning its route
 * @param minDepth     the first depth it appears at, so the dungeon gets nastier
 *                     without ever showing everything at once
 * @param weight       how often it is picked relative to the other kinds
 * @param colour       what to draw it as, packed {@code 0xRRGGBB} — with no models
 *                     yet, colour and size are all a player has to tell them apart
 * @param scale        drawn size relative to its geometry, when drawn as a shape
 * @param look         the model, skin and animations to draw it with — empty when
 *                     this kind has no art, and then it falls back to a shape
 */
public record MonsterKind(
        String name,
        float senseRadius,
        float chaseRadius,
        float closeDistance,
        int repathFrames,
        int minDepth,
        int weight,
        int colour,
        float scale,
        MonsterLook look) {

    /** The behaviour tag a creature definition references: {@code Script:<name>Brain}. */
    public String brainTag() {
        return name + "Brain";
    }

    public java.awt.Color awtColour() {
        return new java.awt.Color(colour);
    }
}
