package uz.dukeengine.core.view;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;

/**
 * A rally point of one of the viewer's things, as it is shown while that thing is selected: the flag on {@code
 * rallyPoint}, and the line through {@code line} with a node on each of {@code nodes} — see {@code
 * ProductionUpdate.rallyLine}.
 */
public record RallyView(int id, Coord3D rallyPoint, List<Coord3D> line, List<Coord3D> nodes) {
    public RallyView {
        line = List.copyOf(line);
        nodes = List.copyOf(nodes);
    }
}
