package uz.dukeengine.client3d.desktop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import uz.dukeengine.client3d.platform.SettingsStore;

/**
 * The settings as a file a player can see, copy between machines and delete when something has gone wrong with it. It
 * lives beside the user rather than beside the game, because a game that writes into its own folder cannot be
 * installed where it belongs.
 */
public final class FileSettingsStore implements SettingsStore {

    private static final Logger LOG = Logger.getLogger(FileSettingsStore.class.getName());

    /** What older versions kept in {@code java.util.prefs} (on Windows, the registry). */
    private static final String[] OLDER_KEYS = {"volume", "fullscreen", "volEffects", "volVoice", "volMusic",
            "volAmbience", "musicTrack", "shake"};

    private final Path file;

    /** The player's own file, taking over what an older version left in the registry. */
    public FileSettingsStore() {
        this(Path.of(System.getProperty("user.home"), ".duke-engine", "settings.properties"));
        inheritFrom(Preferences.userRoot().node("duke-engine/game"), OLDER_KEYS);
    }

    public FileSettingsStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    @Override
    public byte[] read() throws IOException {
        if (!Files.exists(file)) {
            return null;
        }
        return Files.readAllBytes(file);
    }

    @Override
    public void write(byte[] settings) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, settings);
    }

    /**
     * Take over whatever an older version left in the registry.
     *
     * <p>Only once, and only when there is no file: a player who has already been playing should not lose the volume
     * he set, and should not have it come back after he changes it.
     */
    void inheritFrom(Preferences older, String... keys) {
        if (Files.exists(file) || older == null) {
            return;
        }
        var found = new Properties();
        for (var key : keys) {
            var value = older.get(key, null);
            if (value != null) {
                found.setProperty(key, value);
            }
        }
        if (found.isEmpty()) {
            return;
        }
        try {
            var out = new ByteArrayOutputStream();
            found.store(out, "Duke Engine — what the player set");
            write(out.toByteArray());
        } catch (IOException e) {
            // could not write it down: the player starts from the defaults, as with no registry to inherit
            LOG.warning(() -> "could not keep the settings an older version left (" + e.getMessage() + ")");
        }
    }
}
