package uz.dukeengine.client3d;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import uz.dukeengine.client3d.platform.ClientPlatform;
import uz.dukeengine.client3d.platform.SettingsStore;

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
 * <p>Kept through a {@link SettingsStore}, which on a desktop is a file a player
 * can see, copy between machines and delete when something has gone wrong with it.
 */
final class GameSettings {

    private static final Logger LOG = Logger.getLogger(GameSettings.class.getName());

    private final SettingsStore store;
    private final Properties saved = new Properties();
    private final Map<String, String> draft = new LinkedHashMap<>();

    GameSettings() {
        this(ClientPlatform.get().settings());
    }

    GameSettings(SettingsStore store) {
        this.store = store;
        load();
    }

    private void load() {
        try {
            var bytes = store.read();
            if (bytes != null) {
                saved.load(new ByteArrayInputStream(bytes));
            }
        } catch (IOException e) {
            LOG.warning(() -> "could not read the settings (" + e.getMessage()
                    + ") — starting from the defaults");
        }
    }

    // ---- reading ----

    int number(String key, int fallback) {
        var text = draft.getOrDefault(key, saved.getProperty(key));
        try {
            return text == null ? fallback : Integer.parseInt(text.trim());
        } catch (NumberFormatException _) {
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

    /**
     * What he has seen applied, kept as he leaves — by Escape, or by closing the window — and written only where there
     * is anything to write: Cancel is the one way back.
     */
    void keep() {
        if (dirty()) {
            save();
        }
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
            var out = new ByteArrayOutputStream();
            saved.store(out, "Duke Engine — what the player set");
            store.write(out.toByteArray());
        } catch (IOException e) {
            LOG.warning(() -> "could not write the settings (" + e.getMessage()
                    + ") — the settings hold for this session only");
        }
    }
}
