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
 * @param alertRadius  how far its shout carries when it starts fighting -- anything
 *                     of its own within that and in plain sight of it joins in.
 *                     Zero for something that fights alone
 * @param repathFrames frames between re-planning its route
 * @param minDepth     the first depth it appears at, so the dungeon gets nastier
 *                     without ever showing everything at once
 * @param weight       how often it is picked relative to the other kinds
 * @param colour       what to draw it as, packed {@code 0xRRGGBB} — with no models
 *                     yet, colour and size are all a player has to tell them apart
 * @param scale        drawn size relative to its geometry, when drawn as a shape
 * @param look         the model, skin and animations to draw it with — empty when
 *                     this kind has no art, and then it falls back to a shape
 * @param skillKey     which of its own skills it decides to cast, by key: a
 *                     {@code DungeonSkill} block headed by its name, as a hero's are.
 *                     Zero for a thing with no skill
 * @param skillNearest the nearest it casts from, surface to surface
 * @param skillFurthest and the furthest
 * @param keepNearest  the nearest it lets him come before it backs away
 * @param keepFurthest and the furthest it lets him get before it comes after him.
 *                     Zero for a thing that closes to {@code closeDistance} instead
 * @param maxPerRoom   how many of it one room may hold, or zero for no limit
 */
public record MonsterKind(
        String name,
        float senseRadius,
        float chaseRadius,
        float closeDistance,
        float alertRadius,
        int repathFrames,
        int swingFrames,
        int minDepth,
        int weight,
        int colour,
        float scale,
        MonsterLook look,
        char skillKey,
        float skillNearest,
        float skillFurthest,
        float keepNearest,
        float keepFurthest,
        int maxPerRoom) {

    /** The behaviour tag a creature definition references: {@code Script:<name>Brain}. */
    public String brainTag() {
        return name + "Brain";
    }

    public java.awt.Color awtColour() {
        return new java.awt.Color(colour);
    }

    /** Whether it has a skill of its own to decide about. */
    public boolean hasSkill() {
        return skillKey != 0;
    }

    /** Whether it holds a band of distance from him rather than closing to fight. */
    public boolean keepsItsDistance() {
        return keepFurthest > 0f;
    }
}
