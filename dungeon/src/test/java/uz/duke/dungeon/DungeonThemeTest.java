package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * A floor's look is a look and nothing else.
 *
 * <p>This is the promise the whole idea rests on. Every floor is the same fight
 * however it is dressed: the same rooms in the same places, the same creatures
 * with the same numbers, the same fight the same seed always gave. If choosing a
 * theme moved the world by so much as one draw, then two players down one seed
 * would be playing two different games and every claim this project makes about
 * reproducibility would be worth nothing.
 *
 * <p>So the test is a checksum. The same seed is played twice, under themes that
 * could hardly differ more, and the two runs have to come out bit-identical —
 * which is the same shape as the one that holds fog out of the simulation, and
 * for the same reason.
 */
class DungeonThemeTest {

    /** The shipped file with its theme order rewritten. */
    private static DungeonSettings withOrder(String order) {
        var text = Content.read(Content.SETTINGS);
        var edited = new StringBuilder();
        for (var line : text.split("\n", -1)) {
            edited.append(line.trim().startsWith("Order =") ? "  Order = " + order : line)
                    .append('\n');
        }
        return DungeonSettings.parse(edited.toString());
    }

    /**
     * A fixed run of one world, reduced to what the simulation ended up as.
     *
     * <p>Deep enough to cross several floors, because the theme changes with the
     * depth and a run that never left the first one would prove nothing.
     */
    private static String playedOut(DungeonSettings settings) {
        var session = Dungeon.newSession(20250910L, settings);
        var game = session.game();
        game.runHeadless(1);
        var signature = new StringBuilder();
        for (int step = 0; step < 12; step++) {
            game.runHeadless(120);
            // Read the view every step, exactly as the client does — looking at
            // the game must not disturb it either.
            game.getSnapshot();
            signature.append(game.getLogic().getObjectCount()).append(':')
                    .append(game.getLogic().checksum()).append('|');
        }
        return signature.toString();
    }

    /** Play it in stone, play it in space: the same game happens either way. */
    @Test
    void whatAFloorLooksLikeChangesNothingThatHappens() {
        var stone = playedOut(withOrder("Kenney"));
        var space = playedOut(withOrder("SciFi"));

        assertNotEquals("", stone, "something has to have happened for this to say anything");
        assertEquals(stone, space,
                "the run depended on what it was made of, which makes it a rule and not a look");
    }

    /** And a game with the themes taken out entirely plays the same run too. */
    @Test
    void aGameWithNoThemesPlaysTheSameRun() {
        var themed = playedOut(withOrder("Kenney Dungeon Ruins SciFi"));
        var bare = playedOut(withOrder(""));

        assertEquals(themed, bare, "having themes at all changed the game");
    }

    // ---- what the shipped file actually describes ----

    private static final DungeonSettings SHIPPED = DungeonSettings.load();

    /** Every theme the order names is a theme the file describes. */
    @Test
    void everyThemeInTheOrderExists() {
        var themes = SHIPPED.themes();

        assertTrue(themes.all().size() >= 2, "there should be more than one way to look");
        for (var name : themes.order()) {
            assertNotNull(themes.themeNamed(name),
                    "the order names " + name + ", which nothing describes");
        }
    }

    /** And every theme has something to draw with, at every depth it is worn. */
    @Test
    void everyDepthGetsAFloorItCanBuild() {
        var themes = SHIPPED.themes();

        for (int depth = 1; depth <= 20; depth++) {
            var chosen = themes.pick(7L, depth);
            assertNotNull(chosen, "depth " + depth + " has no look at all");
            var tone = chosen.tone();
            assertNotNull(tone.floor(), "depth " + depth + " has no ground to stand on");
            assertNotNull(tone.wall(), "depth " + depth + " has no walls");
        }
    }

    /**
     * The run tells the client which look to wear, and says it in the one place
     * the client reads.
     *
     * <p>Nothing else connects the two: the game writes this field and the client
     * looks for it, and neither compiler sees the other.
     */
    @Test
    void theRunNamesItsLookInTheStatusLine() {
        var session = Dungeon.newSession(11L);
        session.game().runHeadless(2);

        var status = session.game().getSnapshot().status();
        assertTrue(status.contains("|look="), "the client is never told what to build: " + status);
        var look = status.substring(status.indexOf("|look=") + "|look=".length());
        int end = look.indexOf('|');
        look = end < 0 ? look : look.substring(0, end);
        assertEquals(SHIPPED.themes().pick(11L, 1).asStatus(), look,
                "it named a different floor from the one it drew");
    }
}
