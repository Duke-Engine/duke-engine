package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the player set, and the promise a Cancel button makes.
 *
 * <p>The promise is the interesting half. A volume has to be heard to be chosen,
 * so it changes while the slider moves — and if the answer is no it has to go all
 * the way back, including out of the file. A settings screen that half-keeps what
 * you told it to forget is worse than one with no Cancel at all.
 */
class GameSettingsTest {

    @TempDir
    Path folder;

    private GameSettings settings() {
        return new GameSettings(folder.resolve("settings.properties"));
    }

    /** Nothing set is the caller's own default, not zero. */
    @Test
    void whatWasNeverSetIsWhateverTheCallerSaysItIs() {
        var settings = settings();

        assertEquals(75, settings.number("volume", 75));
        assertTrue(settings.flag("fullscreen", true));
        assertFalse(settings.dirty());
    }

    /** A change is felt at once, so it can be judged, and is not yet written down. */
    @Test
    void aChangeIsHeardBeforeItIsKept() {
        var settings = settings();

        settings.set("volume", 40);

        assertEquals(40, settings.number("volume", 100), "it must apply while being chosen");
        assertTrue(settings.dirty());
        assertFalse(Files.exists(settings.file()), "and nothing written until he says");
    }

    /** Cancelled, it goes back — not to zero, to what it was. */
    @Test
    void cancelPutsItBack() {
        var settings = settings();
        settings.set("volume", 40);
        settings.save();
        settings.set("volume", 10);
        assertEquals(10, settings.number("volume", 100));

        settings.cancel();

        assertEquals(40, settings.number("volume", 100));
        assertFalse(settings.dirty());
    }

    /** Set back to what it already was, there is nothing pending. */
    @Test
    void settingItBackToItselfIsNotAChange() {
        var settings = settings();
        settings.set("volume", 40);
        settings.save();

        settings.set("volume", 10);
        settings.set("volume", 40);

        assertFalse(settings.dirty(), "it ends where it started; there is nothing to save");
    }

    /** Saved, it survives the game being closed. */
    @Test
    void whatIsSavedComesBack() {
        var first = settings();
        first.set("volume", 55);
        first.set("fullscreen", true);
        first.save();

        var second = settings();

        assertEquals(55, second.number("volume", 100));
        assertTrue(second.flag("fullscreen", false));
    }

    /** And it is a file, in a place a person can find. */
    @Test
    void itIsAFileAndNotSomethingHidden() throws Exception {
        var settings = settings();
        settings.set("volume", 30);
        settings.save();

        assertTrue(Files.exists(settings.file()));
        assertTrue(Files.readString(settings.file()).contains("volume=30"),
                "readable enough to fix by hand when something has gone wrong");
    }

    /**
     * A player who has been playing keeps what he set.
     *
     * <p>The settings used to live in {@code java.util.prefs}. Moving them to a
     * file should not cost him his volume — and should not put it back after he
     * has since changed it, which is why this happens once and only into an empty
     * house.
     */
    @Test
    void whatAnOlderVersionKeptIsInherited() {
        var older = java.util.prefs.Preferences.userRoot()
                .node("duke-engine-test/" + System.nanoTime());
        older.putInt("volume", 25);

        var settings = settings();
        settings.inheritFrom(older, "volume", "fullscreen");

        assertEquals(25, settings.number("volume", 100));

        older.putInt("volume", 90);
        var again = settings();
        again.inheritFrom(older, "volume");
        assertEquals(25, again.number("volume", 100), "the file is the truth once it exists");
    }

    /** Every pending key is nameable, for a caller putting live values back. */
    @Test
    void itSaysWhatIsPending() {
        var settings = settings();
        settings.set("volume", 10);
        settings.set("volMusic", 20);

        assertEquals(java.util.Set.of("volume", "volMusic"), settings.pending());

        settings.cancel();
        assertEquals(java.util.Set.of(), settings.pending());
    }
}
