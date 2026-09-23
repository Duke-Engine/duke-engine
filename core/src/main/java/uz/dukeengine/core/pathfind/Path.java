package uz.dukeengine.core.pathfind;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;

/**
 * An ordered list of world-space waypoints, ported from SAGE's {@code Path}.
 *
 * <p>The result of a {@link Pathfinder} search: follow the waypoints in order to
 * get from the start to the goal. An empty path means no route exists.
 *
 * <p>A path may also end short of what was asked for: sent somewhere that cannot
 * be reached, a mover is given a route to the nearest place that can
 * ({@link Pathfinder#findPathOrNearest}), and {@link #reachesGoal} says so — the
 * one thing an errand needs to tell "got as close as it could" from "arrived".
 */
public final class Path {

    public static final Path EMPTY = new Path(List.of(), false);

    private final List<Coord3D> waypoints;
    private final boolean reachesGoal;

    /** A route that ends where it was asked to. */
    public Path(List<Coord3D> waypoints) {
        this(waypoints, !waypoints.isEmpty());
    }

    private Path(List<Coord3D> waypoints, boolean reachesGoal) {
        this.waypoints = List.copyOf(waypoints);
        this.reachesGoal = reachesGoal;
    }

    /** A route that ends as near as it can get, rather than where it was asked to — possibly where it starts. */
    public static Path partial(List<Coord3D> waypoints) {
        return new Path(waypoints, false);
    }

    /** Whether the last waypoint is the goal that was asked for, rather than the nearest place to it. */
    public boolean reachesGoal() {
        return reachesGoal;
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
