package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import uz.dukeengine.core.math.Coord3D;

/**
 * A* search over a {@link PathGrid}, ported from SAGE's {@code Pathfinder}.
 *
 * <p>Finds a short cell path between two world positions over an 8-connected
 * grid (orthogonal step cost 10, diagonal 14, matching SAGE's integer cost
 * scale), then returns it as world-space {@link Path} waypoints.
 *
 * <p>Two things happen after the search, and both matter more than the search
 * itself does to how movement looks.
 *
 * <p><b>The route is pulled straight.</b> A grid search can only answer in cells,
 * so its waypoints are cell centres and a walk across open floor comes out as a
 * staircase — right, diagonal, right, diagonal — even where a straight line was
 * free the whole way. So once the cells are known, the corners are pulled out of
 * them: keep only the waypoints you cannot see past. On open ground that leaves
 * exactly one, and the unit walks straight there.
 *
 * <p><b>The route respects the mover's width.</b> Cells are points to a search
 * and units are not. A path that grazes a corner is fine for a point and wrong
 * for a body, and the body then discovers it by colliding, which comes out as a
 * unit arcing around things for no visible reason. Given a {@code clearance} the
 * search and the straightening both keep that much room from stone.
 *
 * <p>Determinism is essential for lock-step: the open set is ordered by
 * {@code f = g + h} and ties are broken by cell index, neighbours are always
 * visited in the same fixed order, costs are integers, and the straightening
 * samples at a fixed fraction of a cell — so the same grid and endpoints always
 * yield the same path on every machine.
 */
public final class Pathfinder {

    private static final int ORTHOGONAL_COST = 10;
    private static final int DIAGONAL_COST = 14;

    /** How finely a straight line is sampled when testing whether it is clear. */
    private static final float LINE_SAMPLE_FRACTION = 0.25f;

    // Fixed neighbour order: orthogonals first, then diagonals.
    private static final int[][] NEIGHBOURS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private Pathfinder() {
    }

    /** Find a path for something with no width — a marker, a camera, a test. */
    public static Path findPath(PathGrid grid, Coord3D from, Coord3D to) {
        return findPath(grid, from, to, 0f);
    }

    /**
     * Find a path from {@code from} to {@code to} wide enough for a body of
     * {@code clearance} radius, or {@link Path#EMPTY} if there is none.
     *
     * <p>If no route wide enough exists, the search is run again ignoring width
     * rather than reporting no route at all. A unit that has to squeeze is still
     * better off than a unit that refuses to move — and the alternative is a
     * mover that silently stops working the moment it grows.
     */
    public static Path findPath(PathGrid grid, Coord3D from, Coord3D to, float clearance) {
        // Standing on blocked ground, the only useful first move is off it. A
        // search from there finds nothing at all, which used to leave anything
        // that ended up inside a wall unable to plan its way out for the rest of
        // the game — permanently frozen rather than merely wedged.
        if (grid.isBlocked(grid.toCellX(from), grid.toCellY(from))) {
            var escape = nearestOpenCell(grid, from);
            return escape == null ? Path.EMPTY : new Path(List.of(escape));
        }
        var path = search(grid, from, to, clearance);
        if (path.isEmpty() && clearance > 0f) {
            path = search(grid, from, to, 0f);
        }
        return path;
    }

    /**
     * The centre of the closest cell that can be stood on, or {@code null} if the
     * search gives up.
     *
     * <p>Rings outward from the mover, and within a ring in a fixed order, so two
     * machines pick the same way out. Bounded because a world can be solid stone,
     * and a mover in the middle of one should stop rather than scan it all.
     */
    private static Coord3D nearestOpenCell(PathGrid grid, Coord3D from) {
        int cx = grid.toCellX(from);
        int cy = grid.toCellY(from);
        for (int ring = 1; ring <= ESCAPE_RINGS; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue; // only the edge of this ring; the inside was searched
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    if (!grid.isBlocked(x, y)) { // out of bounds counts as blocked
                        return grid.cellCenter(x, y);
                    }
                }
            }
        }
        return null;
    }

    /** How far to look for a way out before deciding there is none. */
    private static final int ESCAPE_RINGS = 8;

    private static Path search(PathGrid grid, Coord3D from, Coord3D to, float clearance) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);

        if (grid.isBlocked(startX, startY) || grid.isBlocked(goalX, goalY)) {
            return Path.EMPTY;
        }
        if (startX == goalX && startY == goalY) {
            return new Path(List.of(to));
        }

        int width = grid.getWidth();
        int cellCount = width * grid.getHeight();
        int[] gScore = new int[cellCount];
        int[] fScore = new int[cellCount];
        int[] cameFrom = new int[cellCount];
        boolean[] closed = new boolean[cellCount];
        Arrays.fill(gScore, Integer.MAX_VALUE);
        Arrays.fill(cameFrom, -1);

        int startIndex = grid.index(startX, startY);
        int goalIndex = grid.index(goalX, goalY);
        gScore[startIndex] = 0;
        fScore[startIndex] = heuristic(startX, startY, goalX, goalY);

        var open = new PriorityQueue<Integer>((a, b) -> {
            int byF = Integer.compare(fScore[a], fScore[b]);
            return byF != 0 ? byF : Integer.compare(a, b); // deterministic tie-break
        });
        open.add(startIndex);

        while (!open.isEmpty()) {
            int current = open.poll();
            if (current == goalIndex) {
                return reconstruct(grid, cameFrom, current, startIndex, from, to, clearance);
            }
            if (closed[current]) {
                continue; // stale entry from a superseded g-score
            }
            closed[current] = true;

            int cx = current % width;
            int cy = current / width;
            for (var step : NEIGHBOURS) {
                expand(grid, gScore, fScore, cameFrom, closed, open,
                        cx, cy, step[0], step[1], goalX, goalY, clearance);
            }
        }
        return Path.EMPTY;
    }

    private static void expand(PathGrid grid, int[] gScore, int[] fScore, int[] cameFrom,
            boolean[] closed, PriorityQueue<Integer> open,
            int cx, int cy, int dx, int dy, int goalX, int goalY, float clearance) {
        int nx = cx + dx;
        int ny = cy + dy;
        if (!fits(grid, nx, ny, clearance) || !grid.canStep(cx, cy, nx, ny)) {
            return;
        }
        boolean diagonal = dx != 0 && dy != 0;
        if (diagonal && (grid.isBlocked(cx + dx, cy) || grid.isBlocked(cx, cy + dy))) {
            return; // no cutting around the corner of an obstacle
        }

        int neighbour = grid.index(nx, ny);
        if (closed[neighbour]) {
            return;
        }
        int tentative = gScore[grid.index(cx, cy)] + (diagonal ? DIAGONAL_COST : ORTHOGONAL_COST);
        if (tentative >= gScore[neighbour]) {
            return;
        }
        cameFrom[neighbour] = grid.index(cx, cy);
        gScore[neighbour] = tentative;
        fScore[neighbour] = tentative + heuristic(nx, ny, goalX, goalY);
        open.add(neighbour);
    }

    /** Octile distance heuristic, on the same integer cost scale as the steps. */
    private static int heuristic(int cx, int cy, int goalX, int goalY) {
        int dx = Math.abs(cx - goalX);
        int dy = Math.abs(cy - goalY);
        int min = Math.min(dx, dy);
        int max = Math.max(dx, dy);
        return DIAGONAL_COST * min + ORTHOGONAL_COST * (max - min);
    }

    private static Path reconstruct(PathGrid grid, int[] cameFrom, int goal, int start,
            Coord3D exactFrom, Coord3D exactTo, float clearance) {
        int width = grid.getWidth();
        var cells = new ArrayList<Integer>();
        for (int cell = goal; cell != -1 && cell != start; cell = cameFrom[cell]) {
            cells.add(cell);
        }
        Collections.reverse(cells);

        var waypoints = new ArrayList<Coord3D>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            int cell = cells.get(i);
            // The final cell uses the caller's exact destination, not the cell centre.
            if (i == cells.size() - 1) {
                waypoints.add(exactTo);
            } else {
                waypoints.add(grid.cellCenter(cell % width, cell / width));
            }
        }
        return new Path(straighten(grid, exactFrom, waypoints, clearance));
    }

    /**
     * Drop every waypoint the mover can already see past.
     *
     * <p>The cells are a proof that a route exists, not an instruction to walk
     * their centres. Starting from where the mover stands, take the furthest
     * waypoint still reachable in a straight line, and throw away everything
     * between. Across open floor that collapses the whole staircase into the
     * destination.
     */
    private static List<Coord3D> straighten(PathGrid grid, Coord3D from,
            List<Coord3D> waypoints, float clearance) {
        if (waypoints.size() < 2) {
            return waypoints;
        }
        var straightened = new ArrayList<Coord3D>();
        var anchor = from;
        int i = 0;
        while (i < waypoints.size()) {
            int furthest = i;
            for (int j = waypoints.size() - 1; j > i; j--) {
                if (isClearLine(grid, anchor, waypoints.get(j), clearance)) {
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
     * between two points without touching stone.
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
    private static boolean isClearLine(PathGrid grid, Coord3D a, Coord3D b, float clearance) {
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        int samples = Math.max(1,
                (int) Math.ceil(distance / (grid.getCellSize() * LINE_SAMPLE_FRACTION)));
        int lastX = grid.toCellX(a);
        int lastY = grid.toCellY(a);
        for (int i = 0; i <= samples; i++) {
            float t = (float) i / samples;
            float x = a.x() + dx * t;
            float y = a.y() + dy * t;
            if (!isClearAround(grid, x, y, clearance)) {
                return false;
            }
            int cx = (int) Math.floor(x / grid.getCellSize());
            int cy = (int) Math.floor(y / grid.getCellSize());
            if ((cx != lastX || cy != lastY) && !grid.canStep(lastX, lastY, cx, cy)) {
                return false;
            }
            lastX = cx;
            lastY = cy;
        }
        return true;
    }

    /** Whether a cell can hold a body of {@code clearance} radius at its centre. */
    private static boolean fits(PathGrid grid, int cx, int cy, float clearance) {
        if (grid.isBlocked(cx, cy)) {
            return false;
        }
        if (clearance <= 0f) {
            return true;
        }
        var center = grid.cellCenter(cx, cy);
        return isClearAround(grid, center.x(), center.y(), clearance);
    }

    /** Whether every cell a body of {@code clearance} radius would touch is free. */
    private static boolean isClearAround(PathGrid grid, float x, float y, float clearance) {
        float cell = grid.getCellSize();
        int minX = (int) Math.floor((x - clearance) / cell);
        int maxX = (int) Math.floor((x + clearance) / cell);
        int minY = (int) Math.floor((y - clearance) / cell);
        int maxY = (int) Math.floor((y + clearance) / cell);
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                if (grid.isBlocked(cx, cy)) {
                    return false;
                }
            }
        }
        return true;
    }
}
