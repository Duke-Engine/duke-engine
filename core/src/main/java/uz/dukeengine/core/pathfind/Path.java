package uz.dukeengine.core.pathfind;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;

/**
 * An ordered list of world-space waypoints, ported from SAGE's {@code Path}.
 *
 * <p>The result of a {@link Pathfinder} search: follow the waypoints in order to
 * get from the start to the goal. An empty path means no route exists.
 */
public final class Path {

    public static final Path EMPTY = new Path(List.of());

    private final List<Coord3D> waypoints;

    public Path(List<Coord3D> waypoints) {
        this.waypoints = List.copyOf(waypoints);
    }

    public List<Coord3D> getWaypoints() {
        return waypoints;
    }

    public boolean isEmpty() {
        return waypoints.isEmpty();
    }

    public int size() {
        return waypoints.size();
    }

    public Coord3D get(int i) {
        return waypoints.get(i);
    }

    /** The final waypoint (the destination), or {@code null} if empty. */
    public Coord3D getDestination() {
        return waypoints.isEmpty() ? null : waypoints.get(waypoints.size() - 1);
    }
}
