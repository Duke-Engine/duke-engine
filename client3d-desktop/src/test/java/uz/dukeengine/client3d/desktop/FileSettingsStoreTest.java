package uz.dukeengine.client3d.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSettingsStoreTest {

    @TempDir
    Path folder;

    private FileSettingsStore store() {
        return new FileSettingsStore(folder.resolve("settings.properties"));
    }

    private static int volume(FileSettingsStore store) throws IOException {
        var bytes = store.read();
        if (bytes == null) {
            return 100;
        }
        var p = new Properties();
        p.load(new ByteArrayInputStream(bytes));
        return Integer.parseInt(p.getProperty("volume", "100"));
    }

    @Test
    void nothingWrittenReadsAsNull() throws IOException {
        assertNull(store().read());
    }

    @Test
    void whatIsWrittenIsReadBack() throws IOException {
        store().write("volume=30\n".getBytes());
        assertEquals(30, volume(store()));
    }

    /**
     * A player who has been playing keeps what he set: the settings used to live in {@code java.util.prefs}, and
     * moving them to a file should not cost him his volume, nor put it back after he has since changed it, which is
     * why this happens once and only into an empty house.
     */
    @Test
    void whatAnOlderVersionKeptIsInherited() throws IOException {
        var older = java.util.prefs.Preferences.userRoot().node("duke-engine-test/" + System.nanoTime());
        older.putInt("volume", 25);

        store().inheritFrom(older, "volume", "fullscreen");

        assertEquals(25, volume(store()));

        older.putInt("volume", 90);
        store().inheritFrom(older, "volume");
        assertEquals(25, volume(store()), "the file is the truth once it exists");
    }
}
