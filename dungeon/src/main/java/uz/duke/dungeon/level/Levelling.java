package uz.duke.dungeon.level;

/**
 * How much killing a level takes, and the one thing a level still gives directly.
 *
 * <p>The rules and nothing else: no game object, no simulation, no engine. That is
 * deliberate — progression is the part of a roguelike players argue about and
 * designers retune constantly, so it should be possible to ask it a question ("what
 * does level 6 cost?") without starting a dungeon.
 *
 * <p>What a level is <em>worth</em> — health, speed, mana, the weight of his blow — is
 * no longer here. It is his attributes growing; see {@link HeroAttributes} and
 * {@link AttributeRules}. Two sources for one figure would be two dials a balance pass
 * has to keep in step, so the old per-level health, damage and mana are gone rather
 * than left beside the attributes. Armour is the exception and stays: it belongs to no
 * attribute, and grows with the level as it always did.
 *
 * <p>Everything is integers from the data file. Levels are counted from 1, and level 1
 * grants nothing on top of his attributes at level 1.
 *
 * @param maxLevel              the level past which nothing more is earned
 * @param xpBase                experience to get from level 1 to level 2
 * @param xpStep                how much more each following level costs
 * @param armourPercentPerLevel damage taken removed per level, in percent
 * @param minDamageTakenPercent the floor on damage taken, so armour never reaches
 *                              immortality
 */
public record Levelling(
        int maxLevel,
        int xpBase,
        int xpStep,
        int armourPercentPerLevel,
        int minDamageTakenPercent) {

    public static final int FIRST_LEVEL = 1;

    /** Total experience needed to have reached {@code level}. */
    public int totalXpFor(int level) {
        int total = 0;
        for (int step = FIRST_LEVEL; step < level; step++) {
            total += xpBase + (step - FIRST_LEVEL) * xpStep;
        }
        return total;
    }

    /** The level this much experience has earned, never past {@link #maxLevel}. */
    public int levelFor(int experience) {
        int level = FIRST_LEVEL;
        while (level < maxLevel && experience >= totalXpFor(level + 1)) {
            level++;
        }
        return level;
    }

    /** Experience earned since reaching the current level. */
    public int xpIntoLevel(int experience) {
        return experience - totalXpFor(levelFor(experience));
    }

    /** Experience the current level needs in total, or 0 at the maximum level. */
    public int xpToNextLevel(int experience) {
        int level = levelFor(experience);
        return level >= maxLevel ? 0 : totalXpFor(level + 1) - totalXpFor(level);
    }

    /**
     * What incoming damage is multiplied by at this level — below 1 is armour.
     *
     * <p>Floored, because armour that reaches zero is not a tough hero but a game
     * with no way to lose.
     */
    public float damageTakenMultiplier(int level) {
        return damageTakenWith(level, 0);
    }

    /**
     * The same, with armour found on the floor added to armour earned by levelling.
     *
     * <p>Here rather than at the call site so that the floor is applied once, to
     * the total. Applied twice — once to the level's share and once to the loot's
     * — it would stop being a floor and start being a discount.
     */
    public float damageTakenWith(int level, int extraArmourPercent) {
        float taken = 1f - ((level - FIRST_LEVEL) * armourPercentPerLevel + extraArmourPercent)
                / 100f;
        return Math.max(minDamageTakenPercent / 100f, taken);
    }
}
