package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.Pathfinder.Traffic;

/**
 * Straight lines over the grid: whether a body walks one past nothing that stops it, and a route's cells pulled
 * straight along them. The straightening samples at a fixed fraction of a cell, so every machine pulls a route the
 * same.
 */
final class Lines {

    /** How finely a straight line is sampled when testing whether it is clear. */
    private static final float LINE_SAMPLE_FRACTION = 0.25f;

    private Lines() {
    }

    /** Across the ground from {@code a} to {@code b}. */
    static float across(Coord3D a, Coord3D b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** How many steps a line {@code dx} by {@code dy} long is sampled in. */
    private static int samples(PathGrid grid, float dx, float dy) {
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        return Math.max(1, (int) Math.ceil(distance / (grid.getCellSize() * LINE_SAMPLE_FRACTION)));
    }

    /** The cells' centres as waypoints, the last one {@code last} where given — the caller's exact destination. */
    static List<Coord3D> waypoints(PathGrid grid, List<Integer> cells, Coord3D last) {
        var waypoints = new ArrayList<Coord3D>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            waypoints.add(last != null && i == cells.size() - 1 ? last : grid.cellCenter(cells.get(i)));
        }
        return waypoints;
    }

    /**
     * Drop every waypoint the mover can already see past.
     *
     * <p>The cells are a proof that a route exists, not an instruction to walk
     * their centres. Starting from where the mover stands, take the furthest
     * waypoint still reachable in a straight line, and throw away everything
     * between. Across open floor that collapses the whole staircase into the
     * destination.
     *
     * <p>The line from each waypoint is tried no further than {@code ahead} waypoints on: a route across a wide world
     * is thousands of cells, and a line tried to the end of it from every corner was most of what it cost.
     */
    static List<Coord3D> straighten(PathGrid grid, Coord3D from, List<Coord3D> waypoints, float clearance,
            Traffic traffic, int ahead) {
        if (waypoints.size() < 2) {
            return waypoints;
        }
        var straightened = new ArrayList<Coord3D>();
        var anchor = from;
        int i = 0;
        while (i < waypoints.size()) {
            int furthest = i;
            for (int j = (int) Math.min(waypoints.size() - 1L, (long) i + ahead); j > i; j--) {
                if (isClearLine(grid, anchor, waypoints.get(j), clearance, traffic)) {
                    furthest = j;
                    break;
                }
            }
            anchor = waypoints.get(furthest);
            straightened.add(anchor);
            i = furthest + 1;
        }
        return straightened;
    }

    /**
     * Whether a body of {@code clearance} radius can travel the straight line
     * between two points without touching stone — nor, given {@code traffic}, a
     * cell its routes go round: a mover it is stuck behind, or an ally standing still.
     *
     * <p>Sampled rather than traced exactly. At zero clearance a sample can slip
     * past the very corner of a cell — which is harmless, because something with
     * no width has nothing to catch on it.
     *
     * <p>The line has to obey height as well, and this is the place it is easiest
     * to forget: the search itself climbs only where a ramp lets it, and then the
     * straightening throws away the waypoints that made it do so. A line drawn
     * from one floor to another without a stair under it looks perfectly clear
     * cell by cell — every one of them is open ground. So each time the line
     * crosses into a new cell, that crossing is asked the same question a step
     * would be asked.
     */
    static boolean isClearLine(PathGrid grid, Coord3D a, Coord3D b, float clearance, Traffic traffic) {
        // A route kept to cheap ground is not cut across dearer: no straight line over a cell costed above its start.
        int startShare = grid.stepPercent(grid.toCellX(a), grid.toCellY(a));
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        int samples = samples(grid, dx, dy);
        int lastX = grid.toCellX(a);
        int lastY = grid.toCellY(a);
        for (int i = 0; i <= samples; i++) {
            float t = (float) i / samples;
            float x = a.x() + dx * t;
            float y = a.y() + dy * t;
            if (!Room.isClearAround(grid, x, y, clearance)) {
                return false;
            }
            int cx = (int) Math.floor(x / grid.getCellSize());
            int cy = (int) Math.floor(y / grid.getCellSize());
            boolean crossed = cx != lastX || cy != lastY;
            if (crossed && !grid.canStep(lastX, lastY, cx, cy)) {
                return false;
            }
            if (grid.hasStepCosts() && grid.stepPercent(cx, cy) > startShare) {
                return false;
            }
            if (crossed && traffic != null
                    && (traffic.costOf(cx, cy) == Traffic.CLOSED || traffic.allyStill(cx, cy))) {
                return false; // the route went round a mover: so does the line
            }
            lastX = cx;
            lastY = cy;
        }
        return true;
    }

    /** Whether the straight line between two points of one floor is walkable on it. */
    static boolean clearOn(PathGrid grid, int floor, Coord3D a, Coord3D b, float clearance) {
        if (floor == 0) {
            return isClearLine(grid, a, b, clearance, null);
        }
        var deck = grid.deck(floor);
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        int samples = samples(grid, dx, dy);
        for (int s = 0; s <= samples; s++) {
            float t = (float) s / samples;
            int cx = (int) Math.floor((a.x() + dx * t) / grid.getCellSize());
            int cy = (int) Math.floor((a.y() + dy * t) / grid.getCellSize());
            if (deck == null || !deck.walkable(cx, cy)) {
                return false;
            }
        }
        return true;
    }
}
