package uz.duke.dungeon.level;

/**
 * What a level is worth, and how much killing it takes to get one.
 *
 * <p>The rules and nothing else: no game object, no simulation, no engine. That
 * is deliberate — progression is the part of a roguelike players argue about and
 * designers retune constantly, so it should be possible to ask it a question
 * ("what does level 6 give?") without starting a dungeon.
 *
 * <p>Everything is integers from the data file. Levels are counted from 1, and
 * level 1 grants nothing: a hero who has killed nothing is exactly the hero the
 * creature file describes. That keeps {@code creatures.ini} readable as the
 * starting hero rather than as a hero minus his first level.
 *
 * <p>The two multipliers come out as floats because that is what the engine's
 * hooks take, but each is computed from integers in one step rather than
 * accumulated level by level — repeated multiplication would drift, and a hero
 * who reached level 7 by two different routes must be the same hero.
 *
 * @param maxLevel            the level past which nothing more is earned
 * @param xpBase              experience to get from level 1 to level 2
 * @param xpStep              how much more each following level costs
 * @param healthPerLevel      flat maximum health added per level
 * @param damagePercentPerLevel   damage added per level, in percent of the base
 * @param armourPercentPerLevel   damage taken removed per level, in percent
 * @param minDamageTakenPercent   the floor on damage taken, so armour never
 *                                reaches immortality
 */
public record Levelling(
        int maxLevel,
        int xpBase,
        int xpStep,
        int healthPerLevel,
        int damagePercentPerLevel,
        int armourPercentPerLevel,
        int minDamageTakenPercent,
        int manaPerLevel,
        int manaRegenPerLevel) {

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

    /** Maximum health added by everything earned so far. */
    public int bonusHealth(int level) {
        return (level - FIRST_LEVEL) * healthPerLevel;
    }

    /**
     * Maximum mana added by everything earned so far.
     *
     * <p>Counted exactly as health is, and for the same reason: a level is worth
     * the same thing every time, so the sum is a multiplication rather than a
     * running total somebody has to keep.
     */
    public int bonusMana(int level) {
        return (level - FIRST_LEVEL) * manaPerLevel;
    }

    /**
     * Mana a second added by everything earned so far.
     *
     * <p>A whole number of points a second, never a fraction. What makes a
     * fraction unwelcome is not the arithmetic but where it ends up: regeneration
     * is a sum over frames in the simulation, and a sum of floats is a sum that
     * two machines can disagree about. See {@code SkillBook.update}, which counts
     * this out in whole points against a frame carry.
     */
    public int bonusManaRegen(int level) {
        return (level - FIRST_LEVEL) * manaRegenPerLevel;
    }

    /** What the hero's weapon is multiplied by at this level. */
    public float damageMultiplier(int level) {
        return 1f + (level - FIRST_LEVEL) * damagePercentPerLevel / 100f;
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
