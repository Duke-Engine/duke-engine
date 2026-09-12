package uz.duke.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The data files, as everything that reads them sees them.
 *
 * <p>One rule, and it is worth a test of its own because breaking it produces a
 * failure that looks like anything but its cause: <b>a line ends with
 * {@code \n}</b>, whatever the machine this was checked out on decided to write.
 *
 * <p>Git is commonly set to hand a Windows working copy CRLF, so one commit is LF
 * on one machine and CRLF on another. Everything that reads these files as TEXT
 * then works in one place and not the other — {@code indexOf("Object Stalker\n")}
 * quietly finds nothing, a map row picks up an extra cell of {@code \r} on its
 * end — and what it looks like from outside is a test that is green for the whole
 * team except whoever is on Windows. Which is exactly how it turned up: CI red on
 * Windows, green on macOS, green on the machine it was written on.
 */
class ContentTest {

    private static final String[] EVERY_FILE = {
        Content.CREATURES, Content.SETTINGS, Content.MONSTERS, Content.PROPS,
        Content.FIXTURE_CREATURES, Content.FIXTURE_ROOM,
    };

    /**
     * No data file carries a carriage return by the time anything reads it.
     *
     * <p>This is the whole guarantee. On a checkout that gave the files LF it
     * passes either way and says nothing; on one that gave them CRLF it is the
     * difference between a working game and a puzzling one.
     */
    @Test
    void everyDataFileIsReadWithUnixLines() {
        for (var name : EVERY_FILE) {
            var text = Content.read(name);
            assertFalse(text.contains("\r"), name + " still has a carriage return in it once"
                    + " read. Everything that looks for a line in this file — a block header,"
                    + " a map row — is now looking for something that is not there, and only"
                    + " on the machines whose checkout did this");
            assertTrue(text.contains("\n"), name + " has no lines at all");
        }
    }

    /**
     * And the normalising does not otherwise alter a file.
     *
     * <p>A guard on the guard: replacing line endings must not disturb the
     * content around them, so the text still reads as the file it is.
     */
    @Test
    void normalisingLeavesTheContentAlone() {
        var creatures = Content.read(Content.CREATURES);

        assertTrue(creatures.contains("Object Rogue\n"), "the hero's block went missing");
        assertTrue(creatures.contains("\nEnd\n"), "the blocks lost their ends");
        assertFalse(creatures.contains("\n\n\n\n"), "lines were doubled up somewhere");
    }

    /** A file nobody ships fails by name rather than by null. */
    @Test
    void aMissingFileSaysWhichOne() {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> Content.read("no-such-file.ini"));

        assertTrue(thrown.getMessage().contains("no-such-file.ini"), thrown.getMessage());
    }
}
