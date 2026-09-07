package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
