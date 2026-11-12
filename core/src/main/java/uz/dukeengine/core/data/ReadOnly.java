package uz.dukeengine.core.data;

import java.util.Collections;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * A sorted collection handed out read-only. A browser build swaps this class for one that hands the collection
 * out as it is, since TeaVM's class library has no sorted read-only views.
 */
public final class ReadOnly {

    private ReadOnly() {
    }

    public static <T> SortedSet<T> of(SortedSet<T> set) {
        return Collections.unmodifiableSortedSet(set);
    }

    public static <K, V> SortedMap<K, V> of(SortedMap<K, V> map) {
        return Collections.unmodifiableSortedMap(map);
    }
}
