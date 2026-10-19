package uz.dukeengine.core.map;

import java.util.List;

/** A map with named places on it — see {@link MapZone}. */
public interface Marked extends MapTemplate {

    /** Its places, in the order the file writes them. */
    List<MapZone> zones();
}
