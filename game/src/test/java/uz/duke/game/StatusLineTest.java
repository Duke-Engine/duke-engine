package uz.duke.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.game.view.WorldSnapshot;

/**
 * A line on the HUD that belongs to the game rather than to the engine.
 *
 * <p>The snapshot carries money, power and a banner because the engine knows what
 * those are. It cannot know what else a particular game counts — a hero's level, a
 * wave number, a countdown — and enumerating them in advance would be guessing at
 * games nobody has written yet. So it carries a string it never reads.
 *
 * <p>This is not the banner. The banner interrupts and is meant to be noticed
 * once; this reports, and sits there while the player plays.
 */
class StatusLineTest {

    private static DukeGame game() {
        return DukeGame.create("Test")
                .loadUnits(DukeGame.STARTER_UNITS)
                .map(20, 20);
    }

    @Test
    void aGameWithNothingToSayHasAnEmptyLine() {
        var game = game();
        game.addPlayer("One", java.awt.Color.RED);
        game.runHeadless(1);

        assertFalse(game.getSnapshot().hasStatus());
        assertEquals("", game.getSnapshot().status());
    }

    @Test
    void whatTheGameSetsReachesTheSnapshot() {
        var game = game();
        game.addPlayer("One", java.awt.Color.RED);
        game.runHeadless(1);

        game.setStatus("Level 4    xp 120/180");
        game.runHeadless(1);

        assertTrue(game.getSnapshot().hasStatus());
        assertEquals("Level 4    xp 120/180", game.getSnapshot().status());
    }

    @Test
    void theLineCanBeChangedAndCleared() {
        var game = game();
        game.addPlayer("One", java.awt.Color.RED);
        game.runHeadless(1);

        game.setStatus("first");
        game.runHeadless(1);
        assertEquals("first", game.getSnapshot().status());

        game.setStatus("second");
        game.runHeadless(1);
        assertEquals("second", game.getSnapshot().status());

        game.setStatus("");
        game.runHeadless(1);
        assertFalse(game.getSnapshot().hasStatus());
    }

    /** The status and the banner are separate channels and do not overwrite each other. */
    @Test
    void theStatusAndTheBannerAreIndependent() {
        var game = game();
        game.addPlayer("One", java.awt.Color.RED);
        game.runHeadless(1);

        game.setStatus("Level 2");
        game.setBanner("VICTORY");
        game.runHeadless(1);

        assertEquals("Level 2", game.getSnapshot().status());
        assertEquals("VICTORY", game.getSnapshot().banner());
    }

    @Test
    void nothingIsNeverNull() {
        var snapshot = new WorldSnapshot(0, 0f, false, 0, 0,
                java.util.List.of(), java.util.List.of(), null, null);

        assertEquals("", snapshot.status());
        assertEquals("", snapshot.banner());
        assertFalse(snapshot.hasStatus());
    }
}
