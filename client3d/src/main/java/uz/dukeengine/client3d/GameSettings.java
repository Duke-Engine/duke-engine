package uz.dukeengine.client3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * What the player set, kept between one session and the next — and what he is in
 * the middle of setting, kept until he says.
 *
 * <p>Two layers rather than one, because a settings screen with Save and Cancel
 * on it is making a promise: nothing is decided until you say so. A volume that
 * has to be heard to be chosen must change while the slider moves, and must go
 * back if the answer is no — so a reading consults the draft first and the saved
 * values behind it, and cancelling is throwing the draft away.
 *
 * <p>A file rather than {@code java.util.prefs}, which on Windows is the registry:
 * a game's settings should be somewhere a player can see, copy between machines
 * and delete when something has gone wrong with them. It lives beside the user
 * rather than beside the game, because a game that writes into its own folder
 * cannot be installed where it belongs.
 */
final class GameSettings {

    private static final Logger LOG = Logger.getLogger(GameSettings.class.getName());

    private final Path file;
    private final Properties saved = new Properties();
    private final Map<String, String> draft = new LinkedHashMap<>();

    GameSettings() {
        this(Path.of(System.getProperty("user.home"), ".duke-engine", "settings.properties"));
    }

    GameSettings(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try (var in = Files.newInputStream(file)) {
            saved.load(in);
        } catch (IOException e) {
            LOG.warning(() -> "could not read " + file + " (" + e.getMessage()
                    + ") — starting from the defaults");
        }
    }

    /**
     * Take over whatever an older version left in the registry.
     *
     * <p>Only once, and only when there is no file: a player who has already been
     * playing should not lose the volume he set, and should not have it come back
     * after he changes it.
     */
    void inheritFrom(java.util.prefs.Preferences older, String... keys) {
        if (Files.exists(file) || older == null) {
            return;
        }
        boolean found = false;
        for (var key : keys) {
            var value = older.get(key, null);
            if (value != null) {
                saved.setProperty(key, value);
                found = true;
            }
        }
        if (found) {
            write();
        }
    }

    // ---- reading ----

    int number(String key, int fallback) {
        var text = draft.getOrDefault(key, saved.getProperty(key));
        try {
            return text == null ? fallback : Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    boolean flag(String key, boolean fallback) {
        var text = draft.getOrDefault(key, saved.getProperty(key));
        return text == null ? fallback : Boolean.parseBoolean(text.trim());
    }

    // ---- writing, into the draft ----

    void set(String key, int value) {
        set(key, Integer.toString(value));
    }

    void set(String key, boolean value) {
        set(key, Boolean.toString(value));
    }

    private void set(String key, String value) {
        if (value.equals(saved.getProperty(key))) {
            draft.remove(key); // set back to what it already was; nothing is pending
        } else {
            draft.put(key, value);
        }
    }

    /** Whether anything is waiting on a Save. */
    boolean dirty() {
        return !draft.isEmpty();
    }

    /** Keep it, and write it down. */
    void save() {
        draft.forEach(saved::setProperty);
        draft.clear();
        write();
    }

    /** Forget it. The caller puts back whatever it had already applied. */
    void cancel() {
        draft.clear();
    }

    /** Every key with something pending, for a caller putting the old values back. */
    java.util.Set<String> pending() {
        return java.util.Set.copyOf(draft.keySet());
    }

    private void write() {
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                saved.store(out, "Duke Engine — what the player set");
            }
        } catch (IOException e) {
            LOG.warning(() -> "could not write " + file + " (" + e.getMessage()
                    + ") — the settings hold for this session only");
        }
    }

    Path file() {
        return file;
    }
}
