package uz.dukeengine.core.map;

import java.util.List;

/**
 * A map dressed with scenery: models placed to be looked at — grass, bushes, banners, the rocks and trees round its edge
 * — none of them a thing of the simulation's. See {@link MapScenery}.
 */
public interface Dressed extends MapTemplate {

    /** Its scenery, in the order the map gives it. */
    List<? extends MapScenery> scenery();
}
