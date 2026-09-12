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
        var taken = new String[] {null};
        var shell = Shell.create()
                .entry(Shell.Entry.PLAY)
                .asking(new Shell.Question("Who", "pick one",
                        List.of(new Shell.Option("Archer", "shoots", () -> taken[0] = "Archer"),
                                new Shell.Option("Knight", "swings", () -> taken[0] = "Knight"))));

        var question = shell.question();
        assertNotNull(question);
        assertEquals(2, question.options().size());
        assertEquals("Knight", question.options().get(1).label());
        assertEquals("swings", question.options().get(1).blurb());

        question.options().get(1).taken().run();
        assertEquals("Knight", taken[0], "taking a row did not reach the game");
        assertNull(question.options().get(1).next(), "that row should have started the game");
    }

    /**
     * A row may narrow the choice instead of starting, and as often as it likes.
     *
     * <p>Which is what makes a handful of these a path rather than a screen: how
     * you are playing, then which stage, then who you are. The client walks it
     * without knowing what a stage or a hero is — it only knows that a row either
     * leads somewhere or is the last one.
     */
    @Test
    void aRowCanLeadToAnotherQuestion() {
        var walked = new java.util.ArrayList<String>();
        var hero = new Shell.Question("Who", "", List.of(
                new Shell.Option("Archer", "", () -> walked.add("archer"))));
        var stage = new Shell.Question("Which", "", List.of(
                new Shell.Option("First", "", () -> walked.add("first"), hero)));
        var shell = Shell.create().entry(Shell.Entry.PLAY)
                .asking(new Shell.Question("How", "", List.of(
                        new Shell.Option("Endless", "", () -> walked.add("endless")),
                        new Shell.Option("Stage", "", null, stage))));

        var how = shell.question();
        var toStages = how.options().get(1);
        assertEquals(stage, toStages.next(), "the stage row should open the stage list");
        assertNull(toStages.taken(), "a row that only leads somewhere need do nothing");

        // Walk it: stage, then the first stage, then the archer.
        toStages.next().options().getFirst().taken().run();
        var thenHero = toStages.next().options().getFirst().next();
        thenHero.options().getFirst().taken().run();

        assertEquals(List.of("first", "archer"), walked);
        assertNull(thenHero.options().getFirst().next(), "the last row starts the game");
    }

    /** And the two branches need not be the same shape. */
    @Test
    void oneBranchMayAskSomethingTheOtherDoesNot() {
        var hero = new Shell.Question("Who", "", List.of(new Shell.Option("Archer", "", null)));
        var how = new Shell.Question("How", "", List.of(
                new Shell.Option("Endless", "", null, hero),
                new Shell.Option("Stage", "", null,
                        new Shell.Question("Which", "", List.of(
                                new Shell.Option("First", "", null, hero))))));

        assertEquals(hero, how.options().get(0).next(), "the endless branch goes straight to who");
        assertEquals("Which", how.options().get(1).next().title(),
                "the stage branch asks one more thing first");
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
                .asking(new Shell.Question("Who", "pick one", List.of()));

        assertNull(shell.question(), "an empty roster would have locked the player out");
    }

    /** An option is a label and a line, and neither is ever null. */
    @Test
    void anOptionIsNeverHalfThere() {
        var bare = new Shell.Option(null, null, null);

        assertEquals("", bare.label());
        assertEquals("", bare.blurb());
    }
}
