package uz.dukeengine.core.map;

import java.util.List;
import uz.dukeengine.core.pathfind.TraversalLink;

/** A map with ways across what no walk crosses — gaps, low walls, ledges — each end in cells. */
public interface Linked extends MapTemplate {

    List<TraversalLink> traversalLinks();
}
