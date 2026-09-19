package uz.duke.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;

/**
 * The run loop: a dead hero ends the run and a new dungeon begins, with the hero
 * back at full health and nothing carried over.
 */
class DungeonRunTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Long enough for the death pause to elapse, whatever the file says it is. */
    private static int pastTheDeathPause() {
        return SETTINGS.run().respawnDelayFrames() + 3;
    }

    private static GameObject heroOf(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Rogue"))
                .findFirst()
                .orElse(null);
    }

    @Test
    void aDeadHeroStartsAFreshRun() {
        var session = Dungeon.newSession(1234L);
        var game = session.game();
        var run = session.run();

        game.runHeadless(1); // boot the world; the first tick learns the hero
        assertEquals(DungeonRun.State.RUNNING, run.getState());
        var firstHero = heroOf(game);
        assertNotNull(firstHero);
        var firstHeroId = firstHero.getId();

        // Kill the hero outright — the run must end.
        game.getLogic().destroyObject(firstHero);
        game.runHeadless(1);
        assertEquals(DungeonRun.State.DEAD, run.getState(), "losing the hero ends the run");

        // After the death pause, a new dungeon is generated and play resumes.
        game.runHeadless(pastTheDeathPause());
        assertEquals(DungeonRun.State.RUNNING, run.getState(), "a new run should have begun");
        assertEquals(1, run.getRunCount(), "exactly one new dungeon after one death");

        var secondHero = heroOf(game);
        assertNotNull(secondHero, "the new run has its own hero");
        assertNotEquals(firstHeroId, secondHero.getId(), "it is a freshly spawned hero");
    }

    @Test
    void theNewRunStartsAtFullHealth() {
        var session = Dungeon.newSession(9L);
        var game = session.game();
        var run = session.run();

        game.runHeadless(1);
        var hero = heroOf(game);
        // Wound the hero, then kill him, so we can tell a fresh spawn from a survivor.
        game.getLogic().destroyObject(hero);
        game.runHeadless(pastTheDeathPause());

        assertEquals(1, run.getRunCount());
        var revived = heroOf(game);
        assertNotNull(revived);
        assertEquals(revived.getBody().getMaxHealth(), revived.getBody().getHealth(), 0.001f,
                "the hero begins the new run at full health");
    }

    /**
     * A death takes what he had learnt with it.
     *
     * <p>The third thing a run keeps and must not: his level, his cards, and what
     * he put his levels INTO. The first two were let go of when the run loop was
     * written and the third was missed, so a hero who died came back at the first
     * level with all four skills still open — twelve points he had not earned and
     * no decisions left to make for the whole of the next run.
     *
     * <p>Asked of the ranks rather than of the panel, because that is where it
     * went wrong: every part of the reset was correct except that one thing was
     * not in it.
     */
    @Test
    void deathTakesWhatHeHadLearnt() {
        var session = Dungeon.newSession(9L);
        var game = session.game();
        game.runHeadless(1);
        var learnt = session.run().getLearnt();

        assertTrue(learnt.raise('Q', 1), "the premise: he opened a skill");
        assertEquals(1, learnt.rankOf('Q'));

        kill(game, session);

        assertEquals(0, learnt.spent(),
                "he came back with points he had not earned this run");
        assertEquals(0, learnt.rankOf('Q'), "and a skill he had not chosen");
    }

    /** Every hero the file has starts a fresh run with nothing learnt. */
    @Test
    void aNewHeroStartsWithNothing() {
        var settings = uz.duke.dungeon.content.DungeonSettings.load();
        var session = Dungeon.newSession(9L, settings);
        var game = session.game();
        game.runHeadless(1);
        var learnt = session.run().getLearnt();
        learnt.raise('Q', 1);

        session.run().startWith(game, "Knight");

        assertEquals(0, learnt.spent(), "a knight inherited an archer's points");
        assertEquals(4, learnt.getSkills().size());
        assertTrue(learnt.getSkills().stream().allMatch(
                        skill -> skill.heroTemplate().equals("Knight")),
                "and they should be his own four");
    }

    /** Run the world on until the hero has died and the loop has started him over. */
    private static void kill(uz.duke.game.DukeGame game, Dungeon.Session session) {
        var hero = uz.duke.dungeon.skill.Skills.heroOf(game.getLogic(),
                game.getLocalPlayerIndex());
        hero.getBody().damage(hero.getBody().getMaxHealth() * 2f);
        // Long enough for the death to be noticed and the next run to be laid.
        game.runHeadless(uz.duke.core.GameConstants.LOGICFRAMES_PER_SECOND * 12);
    }
}
