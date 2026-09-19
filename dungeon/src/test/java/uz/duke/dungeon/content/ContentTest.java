package uz.duke.dungeon.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * then works in one place and not the other — {@code indexOf("Name = Stalker\n")}
 * quietly finds nothing, a map row picks up an extra cell of {@code \r} on its
 * end — and what it looks like from outside is a test that is green for the whole
 * team except whoever is on Windows. Which is exactly how it turned up: CI red on
 * Windows, green on macOS, green on the machine it was written on.
 */
class ContentTest {

    /**
     * No data file carries a carriage return by the time anything reads it.
     *
     * <p>This is the whole guarantee. On a checkout that gave the files LF it
     * passes either way and says nothing; on one that gave them CRLF it is the
     * difference between a working game and a puzzling one.
     */
    @Test
    void everyDataFileIsReadWithUnixLines() {
        var every = new java.util.ArrayList<>(gameFiles());
        every.add(Content.FIXTURE_CREATURES);
        every.add(Content.FIXTURE_ROOM);
        for (var name : every) {
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
        var creatures = Content.units();

        assertTrue(creatures.contains("Hero\n  Name = Rogue\n"), "the hero's block went missing");
        assertTrue(creatures.contains("\nEnd\n"), "the blocks lost their ends");
        assertFalse(creatures.contains("\n\n\n\n"), "lines were doubled up somewhere");
    }

    /**
     * Every block of every file reaches the readers it is meant for, once each.
     *
     * <p>The files are cut into blocks by where their lines start, and a block the
     * cutting lost or read twice would be a creature missing or a sound playing twice
     * with nothing to say so. Counting the ends catches either: every top-level block
     * closes on an {@code End} in the first column. The settings read every block, and
     * a world every template.
     */
    @Test
    void everyBlockReachesItsReadersOnce() {
        long written = 0;
        long templates = 0;
        for (var name : Content.files()) {
            var text = Content.read(name);
            written += ends(text);
            templates += text.lines().filter(line -> line.matches("Object|Monster|Hero|Projectile|Prop")).count();
        }

        assertTrue(templates > 0, "no unit blocks at all");
        assertEquals(written, ends(Content.data()), "the settings read every block once");
        assertEquals(templates, ends(Content.units()), "a world builds every template once");
    }

    /** The world, the list of files, and every file it lists. */
    private static java.util.List<String> gameFiles() {
        var files = new java.util.ArrayList<String>();
        files.add(Content.WORLD);
        files.add(Content.MANIFEST);
        files.addAll(Content.files());
        return files;
    }

    private static long ends(String text) {
        return text.lines().filter(line -> line.matches("(?i)End\\b.*")).count();
    }

    /**
     * The words the settings read module blocks with are the modules a world builds.
     *
     * <p>Two lists, because the settings are read before any world exists: one the game's
     * factory registers, one {@link Content#MODULES} names. A module in one and not the other
     * is a unit block one of them cannot read.
     */
    @Test
    void theSettingsKnowEveryModuleAWorldBuilds() {
        var game = uz.duke.dungeon.Dungeon.world(".....\n.....\n.....\n", DungeonSettings.load()).game();
        game.runHeadless(1);
        var built = game.getLogic().getThingFactory().getModuleFactory().vocabulary();

        assertEquals(built, uz.duke.core.module.ModuleFactory.vocabularyOf(Content.MODULES));
    }

    /** A file nobody ships fails by name rather than by null. */
    @Test
    void aMissingFileSaysWhichOne() {
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> Content.read("no-such-file.ini"));

        assertTrue(thrown.getMessage().contains("no-such-file.ini"), thrown.getMessage());
    }
}
