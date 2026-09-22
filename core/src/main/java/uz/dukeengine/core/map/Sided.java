package uz.dukeengine.core.map;

import java.util.List;

/** A map that names its sides rather than only counting them: see {@link MapSide}. */
public interface Sided extends MapTemplate {

    /** Its sides, in the order the file wrote them — which is the order a player is given one. */
    List<? extends MapSide> sides();
}
