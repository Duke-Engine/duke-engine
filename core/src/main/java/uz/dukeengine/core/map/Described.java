package uz.dukeengine.core.map;

/** A map with something to say for itself on the screen where one is chosen. */
public interface Described extends MapTemplate {

    /** Its name for people, rather than for files. */
    String displayName();

    /** A line or two about it, for the same screen. */
    String description();

    /** What a person reads for any map: its display name, or its own name when it has none. */
    static String titleOf(MapTemplate map) {
        return map instanceof Described described && described.displayName() != null && !described.displayName().isBlank()
                ? described.displayName() : map.name();
    }
}
