package uz.duke.dungeon.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * What the player may put his levels into, and when.
 *
 * <p>The build rules, and every clause of them is a sentence somebody said out
 * loud before it was code — so each is its own test with the sentence over it.
 * Nothing here starts a dungeon: this is arithmetic about a list of numbers, and
 * "what can he take at level 8?" should be answerable without a world.
 */
class SkillRanksTest {

    private static final int SPREAD = 2;

    /** Three ordinary skills and an ultimate — the shape every hero has. */
    private static SkillRanks four() {
        var ranks = new SkillRanks(SPREAD);
        ranks.startWith(List.of(ordinary('Q'), ordinary('W'), ordinary('E'), ultimate('R')));
        return ranks;
    }

    private static Skill ordinary(char key) {
        return new Skill("Hero", key, SkillEffect.AREA_DAMAGE, 10f, 1f, 10f, 10f, 0f, 0f,
                0, 0, 0, 0, 0, 60, 0, 4, 0, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0);
    }

    private static Skill ultimate(char key) {
        return new Skill("Hero", key, SkillEffect.EMPOWER, 0f, 0f, 0f, 0f, 0f, 0f,
                50, 5, 120, 0, 0, 600, 0, 3, 4, 0, 0, 0, "", "", "", "", 0f, "", "", 0f, 0f, 0);
    }

    // ---- a point a level ----

    /** One point per level, and a hero at his first has exactly one. */
    @Test
    void onePointPerLevel() {
        var ranks = four();

        assertEquals(1, ranks.unspent(1));
        assertEquals(9, ranks.unspent(9), "nine levels, nine points, none spent");
        assertEquals(0, ranks.spent());
    }

    /** Spending one leaves one fewer. */
    @Test
    void spendingOneLeavesOneFewer() {
        var ranks = four();

        assertTrue(ranks.raise('Q', 1));

        assertEquals(1, ranks.rankOf('Q'));
        assertEquals(1, ranks.spent());
        assertEquals(0, ranks.unspent(1), "he had one and it is gone");
        assertFalse(ranks.canRaise('W', 1), "and there is nothing left for anything else");
    }

    /** Nothing is learnt until it is bought, which is what stops it being cast. */
    @Test
    void nothingIsLearntUntilItIsBought() {
        var ranks = four();

        for (char key : new char[] {'Q', 'W', 'E', 'R'}) {
            assertEquals(SkillRanks.UNLEARNT, ranks.rankOf(key));
            assertFalse(ranks.isLearnt(key));
        }
    }

    /** At the first level he picks ONE of the three, and the ultimate is not offered. */
    @Test
    void theFirstLevelIsAChoiceOfThree() {
        var ranks = four();

        assertTrue(ranks.canRaise('Q', 1));
        assertTrue(ranks.canRaise('W', 1));
        assertTrue(ranks.canRaise('E', 1));
        assertFalse(ranks.canRaise('R', 1), "the ultimate waits for level 4");
    }

    /**
     * ★ OPENING A SKILL AND RAISING ONE ARE THE SAME THING.
     *
     * <p>Said out loud because it is a design decision and not an accident of the
     * code: 0 → 1 is a raise, and so is 2 → 3. There is no "unlock" anywhere —
     * no second command, no second rule, no branch on whether the rank happens to
     * be nothing. A skill is opened by putting the first point in it, which is
     * what a player is doing either way.
     *
     * <p>The way this would rot is somebody adding a special case for the first
     * point — a different gate, a different cost, a different button — and then
     * the two drifting. This walks a slot from nothing to full and asks the same
     * question at every step.
     */
    @Test
    void openingASkillIsTheSameThingAsRaisingOne() {
        var ranks = four();

        // The first point: the skill did not exist to the player a moment ago.
        assertTrue(ranks.canRaise('Q', 1), "opening it is a raise like any other");
        assertTrue(ranks.raise('Q', 1));
        assertEquals(1, ranks.rankOf('Q'));

        // And every point after it answers to exactly the same call.
        for (int rank = 1; rank < 4; rank++) {
            raiseTo(ranks, 'Q', rank + 1);
            assertEquals(rank + 1, ranks.rankOf('Q'),
                    "raising from " + rank + " should be the same operation as opening was");
        }
        assertFalse(ranks.canRaise('Q', 15), "and the ceiling is the only thing that stops it");
    }

    // ---- the ceilings ----

    /** An ordinary skill takes four points and no more. */
    @Test
    void anOrdinarySkillStopsAtFour() {
        var ranks = four();
        raiseTo(ranks, 'Q', 4);

        assertEquals(4, ranks.rankOf('Q'));
        assertFalse(ranks.canRaise('Q', 15), "a fifth point has nowhere to go");
    }

    /** And an ultimate takes three. */
    @Test
    void anUltimateStopsAtThree() {
        var ranks = four();
        raiseTo(ranks, 'R', 3);

        assertEquals(3, ranks.rankOf('R'));
        assertFalse(ranks.canRaise('R', 15));
    }

    // ---- the ultimate's gates ----

    /** Its first rank at 4, its second at 8, its third at 12. */
    @Test
    void theUltimateGrowsAtFourEightAndTwelve() {
        var ranks = four();

        assertFalse(ranks.canRaise('R', 3), "one level short of its first");
        assertTrue(ranks.canRaise('R', 4));
        ranks.raise('R', 4);

        assertFalse(ranks.canRaise('R', 7), "one short of its second");
        assertTrue(ranks.canRaise('R', 8));
        ranks.raise('R', 8);

        assertFalse(ranks.canRaise('R', 11), "one short of its third");
        assertTrue(ranks.canRaise('R', 12));
    }

    /**
     * A MINIMUM, not an appointment.
     *
     * <p>The clause that is easiest to get wrong and the one that changes how the
     * game is played: pass on the ultimate at 8, spend the point elsewhere, and
     * it is still waiting at 9. A gate that only opened on the exact level would
     * make every level-up a deadline.
     */
    @Test
    void anUltimatePassedOverIsStillThereNextLevel() {
        var ranks = four();
        ranks.raise('R', 4);
        // At 8 he takes something else instead.
        raiseTo(ranks, 'Q', 2);
        raiseTo(ranks, 'W', 2);
        raiseTo(ranks, 'E', 2);
        assertEquals(7, ranks.spent());

        assertTrue(ranks.canRaise('R', 9), "it did not expire at 8");
        assertTrue(ranks.raise('R', 9));
        assertEquals(2, ranks.rankOf('R'));
    }

    // ---- the drift rule ----

    /**
     * The example, exactly as it was described.
     *
     * <p>Level 1, open a skill. Level 2, raise it. Level 3, and it may NOT be
     * raised again — the others are still at nothing, so a third point in it
     * would put them three apart.
     */
    @Test
    void oneSkillCannotRunAwayFromTheOthers() {
        var ranks = four();

        assertTrue(ranks.raise('Q', 1), "level 1: he opens it");
        assertTrue(ranks.raise('Q', 2), "level 2: he raises it");

        assertFalse(ranks.canRaise('Q', 3),
                "level 3: a third point would leave it three clear of the others");
        assertTrue(ranks.canRaise('W', 3), "but the others are open to him");
        assertTrue(ranks.canRaise('E', 3));
    }

    /** Two apart is allowed; three is not. */
    @Test
    void twoApartIsTheLimit() {
        var ranks = four();
        ranks.raise('Q', 1);

        assertTrue(ranks.canRaise('Q', 2), "1 -> 2 against 0 is two apart, which is the limit");
        ranks.raise('Q', 2);
        assertEquals(2, ranks.rankOf('Q'));
        assertFalse(ranks.canRaise('Q', 3), "and 3 against 0 is over it");
    }

    /** The ultimate is not counted in the drift, or nothing could be raised at all. */
    @Test
    void theUltimateDoesNotCountTowardTheDrift() {
        var ranks = four();
        raiseTo(ranks, 'Q', 2);
        raiseTo(ranks, 'W', 2);
        raiseTo(ranks, 'E', 2);

        // R is still 0 and the others are at 2. If it counted, this would be
        // refused -- and it would have been refused since level 1.
        assertTrue(ranks.canRaise('Q', 15) || ranks.canRaise('W', 15) || ranks.canRaise('E', 15),
                "the ordinary three are frozen by an ultimate nobody could have bought yet");
    }

    // ---- and the whole ladder works out ----

    /**
     * Fifteen levels spend exactly fifteen points and leave nothing unfinished.
     *
     * <p>The arithmetic the whole design rests on: three ordinary skills at four
     * ranks is twelve, an ultimate at three is three, and the hero's ceiling is
     * fifteen. If any of the three numbers moves, a hero either never finishes or
     * has a level with nowhere to put it — so this walks a real ladder rather
     * than asserting the sum.
     */
    @Test
    void fifteenLevelsFillEverySlot() {
        var ranks = four();

        for (int level = 1; level <= 15; level++) {
            assertEquals(1, ranks.unspent(level), "a point per level, at level " + level);
            assertTrue(spendOne(ranks, level), "nothing could be taken at level " + level);
        }

        assertEquals(4, ranks.rankOf('Q'));
        assertEquals(4, ranks.rankOf('W'));
        assertEquals(4, ranks.rankOf('E'));
        assertEquals(3, ranks.rankOf('R'));
        assertEquals(15, ranks.spent(), "fifteen levels, fifteen points, all of them placed");
    }

    /** A death or a new hero wipes it: a roguelike keeps nothing. */
    @Test
    void startingAgainKeepsNothing() {
        var ranks = four();
        raiseTo(ranks, 'Q', 3);

        ranks.startWith(List.of(ordinary('Q'), ordinary('W')));

        assertEquals(0, ranks.spent());
        assertEquals(SkillRanks.UNLEARNT, ranks.rankOf('Q'));
        assertEquals(2, ranks.getSkills().size(), "and it is the new hero's four, not the old's");
    }

    /** A key no hero has is refused rather than answered. */
    @Test
    void aKeyHeDoesNotHaveIsRefused() {
        var ranks = four();

        assertFalse(ranks.canRaise('Z', 9));
        assertFalse(ranks.raise('Z', 9));
        assertEquals(0, ranks.spent());
    }

    // ---- against the file the game actually ships ----

    /**
     * Every shipped hero's four add up to the hero's own ceiling.
     *
     * <p>Asked of the file rather than of the fixture above, because the file is
     * what is played. A hero whose skills sum to fourteen has a level that can
     * buy nothing; one whose skills sum to sixteen can never finish a build.
     */
    @Test
    void everyShippedHeroSpendsExactlyHisLevels() {
        var settings = DungeonSettings.load();
        int ceiling = settings.levelling().maxLevel();

        for (var hero : settings.heroes()) {
            int capacity = settings.skillsFor(hero.name()).stream()
                    .mapToInt(Skill::maxRank).sum();
            assertEquals(ceiling, capacity, hero.name() + " has " + capacity
                    + " ranks to fill and " + ceiling + " levels to fill them with");
        }
    }

    /** And each of them has exactly one skill that waits for the hero to grow. */
    @Test
    void everyShippedHeroHasOneUltimate() {
        var settings = DungeonSettings.load();

        for (var hero : settings.heroes()) {
            var ultimates = settings.skillsFor(hero.name()).stream()
                    .filter(Skill::isUltimate).toList();
            assertEquals(1, ultimates.size(), hero.name() + " has " + ultimates.size());
            assertEquals('R', ultimates.get(0).key(), "and it is on R, as the panel assumes");
        }
    }

    /** Walking a real hero's ladder works as well as the fixture's. */
    @Test
    void aShippedHeroCanBeBuiltToTheTop() {
        var settings = DungeonSettings.load();
        var ranks = new SkillRanks(settings.skillSpread());
        ranks.startWith(settings.skillsFor(settings.playedHero()));

        for (int level = 1; level <= settings.levelling().maxLevel(); level++) {
            assertTrue(spendOne(ranks, level),
                    settings.playedHero() + " had nothing to take at level " + level);
        }

        for (var skill : ranks.getSkills()) {
            assertEquals(skill.maxRank(), ranks.rankOf(skill.key()),
                    skill.key() + " did not fill up");
        }
    }

    // ---- helpers ----

    /**
     * Take the first thing the rules allow, which is enough to prove a ladder
     * exists. Which one a player would take is a question for a player.
     */
    private static boolean spendOne(SkillRanks ranks, int level) {
        for (var skill : ranks.getSkills()) {
            if (ranks.raise(skill.key(), level)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get a slot up to a rank, with the hero as high as he needs to be.
     *
     * <p>It has to bring the others along, which is the drift rule doing its job
     * rather than getting in the way: nothing can be taken to four while the rest
     * sit at nothing, and a helper that pretended otherwise would be testing a
     * game that does not exist.
     */
    private static void raiseTo(SkillRanks ranks, char key, int rank) {
        for (int guard = 0; ranks.rankOf(key) < rank; guard++) {
            if (guard > 100) {
                throw new AssertionError("stuck raising " + key + " at " + ranks.rankOf(key));
            }
            if (ranks.raise(key, 15)) {
                continue;
            }
            // Blocked by the drift: bring up whichever ordinary one is furthest
            // behind, which is exactly what a player would have to do.
            var behind = ranks.getSkills().stream()
                    .filter(skill -> !skill.isUltimate())
                    .min(java.util.Comparator.comparingInt(
                            skill -> ranks.rankOf(skill.key())))
                    .orElseThrow();
            if (!ranks.raise(behind.key(), 15)) {
                throw new AssertionError("nothing can be raised at all");
            }
        }
    }
}
