package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** What a game puts in front of itself is the game's to say. */
class ShellTest {

    private static List<Shell.Entry> kinds(Shell shell) {
        return shell.entries().stream().map(java.util.Map.Entry::getKey).toList();
    }

    private static List<String> labels(Shell shell) {
        return shell.entries().stream().map(java.util.Map.Entry::getValue).toList();
    }

    @Test
    void theStandardShellIsWhatEveryGameUsedToGet() {
        var shell = Shell.standard();

        assertEquals(List.of(Shell.Entry.values()), kinds(shell),
                "the default must keep behaving as it always did");
        assertFalse(shell.startsImmediately());
    }

    @Test
    void aGameChoosesItsOwnEntriesAndTheirOrder() {
        var shell = Shell.create()
                .entry(Shell.Entry.PLAY, "Enter the dungeon")
                .entry(Shell.Entry.QUIT);

        assertEquals(List.of(Shell.Entry.PLAY, Shell.Entry.QUIT), kinds(shell),
                "entries appear in the order the game added them");
        assertEquals(List.of("Enter the dungeon", "Quit"), labels(shell),
                "worded by the game, or by the client when the game does not care");
        assertFalse(kinds(shell).contains(Shell.Entry.HOST_LAN),
                "a single-player game should not be offering LAN games");
    }

    @Test
    void aGameCanRefuseToHaveAMenuAtAll() {
        var shell = Shell.none();

        assertTrue(shell.startsImmediately());
        assertTrue(shell.entries().isEmpty());
    }

    @Test
    void anEntryAddedTwiceIsStillOneEntry() {
        var shell = Shell.create()
                .entry(Shell.Entry.PLAY)
                .entry(Shell.Entry.PLAY, "Begin");

        assertEquals(List.of(Shell.Entry.PLAY), kinds(shell));
        assertEquals(List.of("Begin"), labels(shell), "the later wording wins");
    }

    @Test
    void anEmptyLabelFallsBackToTheClientsWording() {
        var shell = Shell.create().entry(Shell.Entry.SETTINGS, "  ");

        assertEquals(List.of("Settings"), labels(shell));
    }

    // ---- something the game wants settled before it starts ----

    /** A game that asks nothing starts on Play, which is every game but one. */
    @Test
    void aGameThatAsksNothingIsAskedNothing() {
        var shell = Shell.create().entry(Shell.Entry.PLAY);

        assertNull(shell.question(), "a shell invented a question nobody asked for");
    }

    @Test
    void aGameCanAskSomethingFirst() {
        var taken = new int[] {-1};
        var shell = Shell.create()
                .entry(Shell.Entry.PLAY)
                .asking(new Shell.Question("Who", "pick one",
                        List.of(new Shell.Option("Archer", "shoots"),
                                new Shell.Option("Knight", "swings")),
                        answer -> taken[0] = answer));

        var question = shell.question();
        assertNotNull(question);
        assertEquals(2, question.options().size());
        assertEquals("Knight", question.options().get(1).label());
        assertEquals("swings", question.options().get(1).blurb());

        question.taken().accept(1);
        assertEquals(1, taken[0], "taking a row did not reach the game");
    }

    /**
     * A question with nothing to pick is not a question.
     *
     * <p>The safety catch, and the one that matters: the options come out of a
     * data file, so a file that named no heroes would otherwise put the player in
     * front of an empty column with no way forward and no way to know why. An
     * empty roster falls back to starting, which is what the game did before it
     * could ask anything.
     */
    @Test
    void aQuestionWithNothingToPickIsNotAsked() {
        var shell = Shell.create()
                .entry(Shell.Entry.PLAY)
                .asking(new Shell.Question("Who", "pick one", List.of(), answer -> { }));

        assertNull(shell.question(), "an empty roster would have locked the player out");
    }

    /** And nor is one with nobody listening for the answer. */
    @Test
    void aQuestionNobodyIsListeningToIsNotAsked() {
        var shell = Shell.create()
                .entry(Shell.Entry.PLAY)
                .asking(new Shell.Question("Who", "pick one",
                        List.of(new Shell.Option("Archer", "")), null));

        assertNull(shell.question());
    }

    /** An option is a label and a line, and neither is ever null. */
    @Test
    void anOptionIsNeverHalfThere() {
        var bare = new Shell.Option(null, null);

        assertEquals("", bare.label());
        assertEquals("", bare.blurb());
    }
}
