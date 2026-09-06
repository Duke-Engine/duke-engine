package uz.duke.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Interns arbitrary strings into unique {@link NameKeyType} handles, ported
 * from SAGE's {@code NameKeyGenerator}.
 *
 * <p>The same string always maps to the same key within one generator, and
 * every key is unique to that generator's catalogue. This lets the rest of the
 * engine compare names (object types, INI field names, script symbols) by int
 * instead of by string. A single global generator is the norm in SAGE; multiple
 * instances give independent namespaces.
 *
 * <p>Determinism: ids are handed out in first-seen order, so as long as data is
 * loaded in a fixed order the same string gets the same id across runs. Nothing
 * here reads wall-clock time or iterates unordered structures.
 */
public final class NameKeyGenerator extends SubsystemInterface {

    private final Map<String, NameKeyType> nameToKey = new HashMap<>();
    private final List<String> keyToName = new ArrayList<>();
    private int nextId;

    @Override
    public void init() {
        reset();
    }

    @Override
    public void reset() {
        nameToKey.clear();
        keyToName.clear();
        keyToName.add(null); // index 0 is reserved for NAMEKEY_INVALID
        nextId = 1;
    }

    @Override
    public void update() {
        // Nothing to service per frame; the catalogue is built on demand.
    }

    /** Convert a string to its unique key, creating one on first sight. */
    public NameKeyType nameToKey(String name) {
        var existing = nameToKey.get(name);
        if (existing != null) {
            return existing;
        }
        if (nextId >= NameKeyType.MAX) {
            throw new IllegalStateException("NameKey catalogue exhausted (>= " + NameKeyType.MAX + ")");
        }
        var key = new NameKeyType(nextId++);
        nameToKey.put(name, key);
        keyToName.add(name);
        return key;
    }

    /** Convert the lowercase form of a string to its key. */
    public NameKeyType nameToLowercaseKey(String name) {
        return nameToKey(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Recover the string for a key. Rare — used for serialization and
     * diagnostics. Unlike SAGE's linear search this is an O(1) lookup.
     */
    public String keyToName(NameKeyType key) {
        if (!key.isValid() || key.id() >= keyToName.size()) {
            return "";
        }
        return keyToName.get(key.id());
    }
}
