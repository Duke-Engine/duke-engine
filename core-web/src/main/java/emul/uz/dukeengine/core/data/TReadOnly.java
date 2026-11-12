package emul.uz.dukeengine.core.data;

import java.util.SortedMap;
import java.util.SortedSet;

/**
 * {@code ReadOnly} in a browser: TeaVM has no sorted read-only views, so the collection is handed out as it is.
 * shortcut: a caller that writes to it is not stopped here as it is on the desktop; wrap it if one ever does.
 */
public final class TReadOnly {

    private TReadOnly() {
    }

    public static <T> SortedSet<T> of(SortedSet<T> set) {
        return set;
    }

    public static <K, V> SortedMap<K, V> of(SortedMap<K, V> map) {
        return map;
    }
}
