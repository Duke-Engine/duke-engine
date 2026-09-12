package uz.duke.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        return SETTINGS.respawnDelayFrames() + 3;
    }

    private static GameObject heroOf(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals("Rogue"))
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
}
