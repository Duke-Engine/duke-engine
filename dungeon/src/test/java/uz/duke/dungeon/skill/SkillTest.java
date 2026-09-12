package uz.duke.dungeon.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * What a skill is worth at a level — arithmetic, asked without a dungeon.
 *
 * <p>Kept apart from the tests that cast anything, for the same reason
 * {@code Levelling} is: "what does level 7 give?" should be answerable without
 * starting a game, and the answer should not depend on how the hero got there.
 */
class SkillTest {

    private static Skill skill(float damage, float perLevel, int cooldown, int cooldownPerLevel) {
        return new Skill("Rogue", 'Q', SkillEffect.STRIKE, damage, perLevel, 0f, 40f, 0f,
                0, 0, 0, 0, 0, cooldown, cooldownPerLevel, 1, 0, "", "", "");
    }

    /** Level one is the file as written: a skill is not the file minus a level. */
    @Test
    void theFirstLevelIsWhatTheFileSays() {
        var q = skill(45f, 7f, 90, -3);

        assertEquals(45f, q.damageAt(1), 0.001f);
        assertEquals(90, q.cooldownAt(1));
    }

    /** Damage grows by the file's step, once per level earned. */
    @Test
    void itGrowsByTheStepInTheFile() {
        var q = skill(45f, 7f, 90, 0);

        assertEquals(52f, q.damageAt(2), 0.001f);
        assertEquals(87f, q.damageAt(7), 0.001f, "45 + 6 x 7");
    }

    /**
     * Computed from the level in one step, never accumulated. Repeated addition
     * drifts, and a hero who reached level 7 by two routes has to have the same
     * skills — which is the same rule levelling itself follows.
     */
    @Test
    void aLevelIsWorthTheSameHoweverItWasReached() {
        var q = skill(45f, 0.1f, 90, 0);
        float inOneStep = q.damageAt(10);

        float accumulated = 45f;
        for (int level = 2; level <= 10; level++) {
            accumulated += 0.1f;
        }

        assertEquals(q.damageAt(10), inOneStep, 0f, "the same question twice");
        assertEquals(45f + 9 * 0.1f, inOneStep, 0.0001f,
                "and equal to the closed form, not to " + accumulated);
    }

    /** A cooldown sharpens with level, because its step is negative. */
    @Test
    void aCooldownShortensAsTheStepIsNegative() {
        var q = skill(45f, 0f, 90, -3);

        assertTrue(q.cooldownAt(5) < q.cooldownAt(1));
        assertEquals(78, q.cooldownAt(5), "90 - 4 x 3");
    }

    /**
     * But never to nothing. A file is free to sharpen a cooldown past zero by
     * accident, and a skill castable every frame is not a skill.
     */
    @Test
    void aCooldownNeverReachesZero() {
        var q = skill(45f, 0f, 30, -20);

        assertTrue(q.cooldownAt(9) >= Skill.MIN_COOLDOWN_FRAMES,
                "sharpened past nothing, it stopped at " + q.cooldownAt(9));
    }

    /** An ultimate is a skill with a level on it, and nothing else. */
    @Test
    void anUltimateIsLockedUntilItsLevel() {
        var r = new Skill("Rogue", 'R', SkillEffect.EMPOWER, 0f, 0f, 0f, 0f, 0f,
                80, 12, 180, 0, 0, 900, -30, 5, 0, "", "", "");

        assertFalse(r.unlockedAt(4));
        assertTrue(r.unlockedAt(5));
        assertTrue(r.unlockedAt(9), "and stays unlocked");
    }

    // ---- what the shipped file actually says ----

    /** The hero really has four skills, on the four keys, from the file. */
    @Test
    void theShippedHeroHasFourSkills() {
        var his = DungeonSettings.load().skillsFor("Rogue");

        assertEquals(4, his.size());
        assertEquals("QWER", his.stream()
                .map(skill -> String.valueOf(skill.key()))
                .reduce("", String::concat), "in the order the file lists them");
    }

    /** And they are four different things, not one thing four times. */
    @Test
    void theyDoFourDifferentThings() {
        var effects = DungeonSettings.load().skillsFor("Rogue").stream()
                .map(Skill::effect)
                .distinct()
                .count();

        assertEquals(4, effects, "each key should be worth pressing for its own reason");
    }

    /** Exactly one of them is an ultimate — the one that waits for a level. */
    @Test
    void oneOfThemIsAnUltimate() {
        var locked = DungeonSettings.load().skillsFor("Rogue").stream()
                .filter(skill -> skill.unlockLevel() > 1)
                .toList();

        assertEquals(1, locked.size());
        assertEquals('R', locked.get(0).key());
    }
}
