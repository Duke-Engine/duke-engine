package uz.dukeengine.dungeon.world;

import uz.dukeengine.core.math.Hundredths;
import uz.dukeengine.dungeon.level.Levelling;

/**
 * How a hero grows: what each level costs and gives, and what a point of his primary attribute
 * adds to his blow.
 *
 * @param skillSpread         how far the ordinary skills may drift apart, in ranks — what stops
 *     one of them being taken four times running while the other two sit at nothing; see
 *     {@code SkillRanks}
 * @param manaPerKill         mana given back for a kill; 0 is off
 * @param levelUpBannerFrames how long "Level 2!" stays on screen, in logic frames
 * @param damagePerPrimary    what a point of a hero's primary attribute adds to his blow
 */
public record Progression(int maxLevel, int skillSpread, int xpBase, int xpStep, int armourPercentPerLevel,
        int minDamageTakenPercent, int manaPerKill, int levelUpBannerFrames, Hundredths damagePerPrimary) {

    /** What a block leaves out. */
    public static final Progression DEFAULTS = new Progression(10, 2, 30, 15, 5, 40, 0, 60, Hundredths.ZERO);

    /** The progression rules, as one value the levelling code can be handed. */
    public Levelling levelling() {
        return new Levelling(maxLevel, xpBase, xpStep, armourPercentPerLevel, minDamageTakenPercent);
    }
}
