package uz.dukeengine.dungeon.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What a level costs, and the armour that is the one thing it still gives directly.
 *
 * <p>What else a level is worth is his attributes growing — see {@code AttributesTest}.
 */
class LevellingTest {

    /** Ten levels; 30 xp for the second, 15 more for each after that. */
    private static final Levelling RULES = new Levelling(10, 30, 15, 5, 40);

    @Test
    void aHeroWhoHasKilledNothingIsLevelOneAndUnchanged() {
        assertEquals(1, RULES.levelFor(0));
        assertEquals(1f, RULES.damageTakenMultiplier(1), 0.0001f,
                "level one wears nothing a level gave him");
    }

    @Test
    void levelsCostMoreAsTheyGoUp() {
        assertEquals(0, RULES.totalXpFor(1));
        assertEquals(30, RULES.totalXpFor(2), "30 for the second");
        assertEquals(75, RULES.totalXpFor(3), "then 45 more");
        assertEquals(135, RULES.totalXpFor(4), "then 60 more");
    }

    @Test
    void experienceBuysTheLevelItHasPaidFor() {
        assertEquals(1, RULES.levelFor(29));
        assertEquals(2, RULES.levelFor(30), "exactly enough is enough");
        assertEquals(2, RULES.levelFor(74));
        assertEquals(3, RULES.levelFor(75));
    }

    @Test
    void progressWithinALevelIsReported() {
        assertEquals(10, RULES.xpIntoLevel(40), "40 xp is 10 into level 2");
        assertEquals(45, RULES.xpToNextLevel(40), "and level 2 costs 45 to leave");
    }

    @Test
    void nothingIsEarnedPastTheMaximum() {
        int far = RULES.totalXpFor(RULES.maxLevel()) * 10;

        assertEquals(RULES.maxLevel(), RULES.levelFor(far), "the cap holds");
        assertEquals(0, RULES.xpToNextLevel(far), "and there is nothing left to buy");
    }

    /** Armour belongs to no attribute, so it is still a level's to give. */
    @Test
    void everyLevelStillGivesArmour() {
        for (int level = 2; level <= RULES.maxLevel(); level++) {
            assertTrue(RULES.damageTakenMultiplier(level) <= RULES.damageTakenMultiplier(level - 1),
                    "no more damage taken at level " + level);
        }
        assertTrue(RULES.damageTakenMultiplier(2) < RULES.damageTakenMultiplier(1),
                "and the first level up is worth some");
    }

    /**
     * Armour has a floor. Without one, enough levels would make the hero take no
     * damage at all — which is not a tough hero but a game that cannot be lost.
     */
    @Test
    void armourNeverReachesImmortality() {
        var relentless = new Levelling(100, 10, 0, 20, 40);

        assertEquals(0.4f, relentless.damageTakenMultiplier(50), 0.0001f,
                "however many levels, the floor holds");
        assertTrue(relentless.damageTakenMultiplier(50) > 0f);
    }

    /** A level is worth the same however it was reached. */
    @Test
    void aLevelIsTheSameHoweverItWasReached() {
        assertEquals(RULES.damageTakenMultiplier(5), RULES.damageTakenMultiplier(5), 0f);
        // Reached in one jump (enough xp for 5 at once) or step by step: same level.
        assertEquals(5, RULES.levelFor(RULES.totalXpFor(5)));
    }
}
