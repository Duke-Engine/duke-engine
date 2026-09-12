package uz.duke.dungeon.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What a level costs and what it is worth. */
class LevellingTest {

    /** Ten levels; 30 xp for the second, 15 more for each after that. */
    private static final Levelling RULES = new Levelling(10, 30, 15, 20, 12, 5, 40, 8, 1);

    @Test
    void aHeroWhoHasKilledNothingIsLevelOneAndUnchanged() {
        assertEquals(1, RULES.levelFor(0));
        assertEquals(0, RULES.bonusHealth(1));
        assertEquals(1f, RULES.damageMultiplier(1), 0.0001f, "level one is the creature file");
        assertEquals(1f, RULES.damageTakenMultiplier(1), 0.0001f);
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

    @Test
    void everyLevelIsWorthSomething() {
        for (int level = 2; level <= RULES.maxLevel(); level++) {
            assertTrue(RULES.bonusHealth(level) > RULES.bonusHealth(level - 1), "more health");
            assertTrue(RULES.damageMultiplier(level) > RULES.damageMultiplier(level - 1), "more damage");
            assertTrue(RULES.damageTakenMultiplier(level) <= RULES.damageTakenMultiplier(level - 1),
                    "and no more damage taken");
        }
    }

    /**
     * Armour has a floor. Without one, enough levels would make the hero take no
     * damage at all — which is not a tough hero but a game that cannot be lost.
     */
    @Test
    void armourNeverReachesImmortality() {
        var relentless = new Levelling(100, 10, 0, 0, 0, 20, 40, 0, 0);

        assertEquals(0.4f, relentless.damageTakenMultiplier(50), 0.0001f,
                "however many levels, the floor holds");
        assertTrue(relentless.damageTakenMultiplier(50) > 0f);
    }

    /**
     * A level is worth the same however it was reached. Each multiplier is computed
     * from the level in one step rather than accumulated, so a hero who jumped two
     * levels at once is identical to one who climbed them singly.
     */
    @Test
    void aLevelIsTheSameHoweverItWasReached() {
        assertEquals(RULES.damageMultiplier(5), RULES.damageMultiplier(5), 0f);
        assertEquals(RULES.bonusHealth(5), RULES.bonusHealth(5));

        // Reached in one jump (enough xp for 5 at once) or step by step: same level.
        assertEquals(5, RULES.levelFor(RULES.totalXpFor(5)));
    }
}
