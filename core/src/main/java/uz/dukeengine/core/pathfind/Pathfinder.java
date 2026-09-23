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
            return escape(grid, from);
        }
        var path = search(grid, from, to, clearance, false);
        if (path.isEmpty() && clearance > 0f) {
            path = search(grid, from, to, 0f, false);
        }
        return path;
    }

    /**
     * A route to {@code to}, or — where there is none — to the place nearest it that there is one to, as the
     * reference game's pathfinder does ({@code Pathfinder::findClosestPath}). {@link Path#reachesGoal} says
     * which it is.
     *
     * <p>What a mover is given. Sent at a spot it cannot reach — a cliff, the far side of a wall, a pocket
     * with no way in — it used to be handed no route at all, and stood where it was with its goal still set:
     * an order that did nothing, and an errand waiting on it that never finished. Now it goes as near as it
     * can and stops there, and whatever sent it can tell that from arriving.
     *
     * <p>Nearest by the grid's straight-line distance to the goal's cell, a tie going to the cell that is the
     * shorter walk and then to the lower cell index, so every machine settles on the same spot. Width is
     * treated as {@link #findPath} treats it: a route with room first, one that squeezes if that is the only
     * way there, and of two routes that both stop short, the one that stops nearer — room winning a tie.
     */
    public static Path findPathOrNearest(PathGrid grid, Coord3D from, Coord3D to, float clearance) {
        if (grid.isBlocked(grid.toCellX(from), grid.toCellY(from))) {
            return escape(grid, from);
        }
        var path = search(grid, from, to, clearance, true);
        if (path.reachesGoal() || clearance <= 0f) {
            return path;
        }
        var squeezed = search(grid, from, to, 0f, true);
        if (squeezed.reachesGoal()) {
            return squeezed;
        }
        return awayFrom(squeezed, from, to) < awayFrom(path, from, to) ? squeezed : path;
    }

    /** How far from {@code to} a route leaves its mover, across the ground. */
    private static float awayFrom(Path path, Coord3D from, Coord3D to) {
        var end = path.isEmpty() ? from : path.getDestination();
        float dx = end.x() - to.x();
        float dy = end.y() - to.y();
        return dx * dx + dy * dy;
    }

    /**
     * Off the blocked cell it stands on: the only useful first move from there, and a route that ends short
     * of wherever it was going, since it is only the way out.
     */
    private static Path escape(PathGrid grid, Coord3D from) {
        var way = nearestOpenCell(grid, from);
        return way == null ? Path.EMPTY : Path.partial(List.of(way));
    }

    // ---- where can be walked to ----

    /**
     * Every cell a body of {@code clearance} can walk to from {@code from}, by the search's own steps: one flag
     * a cell, indexed {@code cy * width + cx}. From a blocked cell, from the nearest open one — the way it would
     * step out first.
     */
    public static boolean[] reachableFrom(PathGrid grid, Coord3D from, float clearance) {
        var reached = new boolean[grid.getWidth() * grid.getHeight()];
        var flood = flood(grid, from, clearance);
        for (int at = 0; at < flood.count(); at++) {
            reached[flood.cells()[at]] = true;
        }
        return reached;
    }

    /**
     * {@code wanted} itself where it can be walked to from {@code from}; otherwise the middle of the reachable
     * cell nearest it, the first found walking outward from {@code from} winning a tie — the nearest place to a
     * point that cannot be reached.
     */
    public static Coord3D nearestReachable(PathGrid grid, Coord3D from, Coord3D wanted) {
        var flood = flood(grid, from, 0f);
        if (flood.count() == 0) {
            return wanted;
        }
        int width = grid.getWidth();
        int goalX = grid.toCellX(wanted);
        int goalY = grid.toCellY(wanted);
        int best = flood.cells()[0];
        long nearest = Long.MAX_VALUE;
        for (int at = 0; at < flood.count(); at++) {
            int cell = flood.cells()[at];
            int cx = cell % width;
            int cy = cell / width;
            if (cx == goalX && cy == goalY) {
                return wanted;
            }
            long away = away(cx, cy, goalX, goalY);
            if (away < nearest) {
                nearest = away;
                best = cell;
            }
        }
        var reached = grid.cellCenter(best % width, best / width);
        return new Coord3D(reached.x(), reached.y(), wanted.z());
    }

    /** The cells a flood reached, in the order it reached them: {@code cells[0..count)}. */
    private record Flood(int[] cells, int count) {
    }

    /**
     * Outward from {@code from}, a ring at a time and in the search's fixed neighbour order, by exactly the steps
     * the search would take — so a cell this reaches is a cell a route can be found to, on every machine alike.
     */
    private static Flood flood(PathGrid grid, Coord3D from, float clearance) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        if (grid.isBlocked(startX, startY)) {
            var way = nearestOpenCell(grid, from);
            if (way == null) {
                return new Flood(new int[0], 0);
            }
            startX = grid.toCellX(way);
            startY = grid.toCellY(way);
        }
        int width = grid.getWidth();
        var seen = new boolean[width * grid.getHeight()];
        var queue = new int[seen.length];
        int head = 0;
        int tail = 0;
        int start = grid.index(startX, startY);
        seen[start] = true;
        queue[tail++] = start;
        while (head < tail) {
            int cell = queue[head++];
            int cx = cell % width;
            int cy = cell / width;
            for (var step : NEIGHBOURS) {
                int nx = cx + step[0];
                int ny = cy + step[1];
                if (!grid.inBounds(nx, ny) || seen[grid.index(nx, ny)] || !canTake(grid, cx, cy, step, clearance)) {
                    continue;
                }
                seen[grid.index(nx, ny)] = true;
                queue[tail++] = grid.index(nx, ny);
            }
        }
        return new Flood(queue, tail);
    }

    /** Whether the search would take this step: room at the far end, a step the grid allows, no corner cut. */
    private static boolean canTake(PathGrid grid, int cx, int cy, int[] step, float clearance) {
        int nx = cx + step[0];
        int ny = cy + step[1];
        if (!fits(grid, nx, ny, clearance) || !grid.canStep(cx, cy, nx, ny)) {
            return false;
        }
        boolean diagonal = step[0] != 0 && step[1] != 0;
        return !diagonal || !grid.isBlocked(cx + step[0], cy) && !grid.isBlocked(cx, cy + step[1]);
    }

    /** How far apart two cells are, squared, which is enough to compare them. */
    private static long away(int cx, int cy, int goalX, int goalY) {
        long dx = cx - goalX;
        long dy = cy - goalY;
        return dx * dx + dy * dy;
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

    /**
     * A* from {@code from} to {@code to}. With {@code orNearest}, a goal that cannot be reached — blocked, or
     * walled off — gives a {@linkplain Path#partial partial} route to the searched cell nearest it rather than
     * none: the search has walked every cell it could reach by the time it gives up, so the answer costs
     * nothing more than the failure did.
     */
    private static Path search(PathGrid grid, Coord3D from, Coord3D to, float clearance, boolean orNearest) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);

        if (grid.isBlocked(startX, startY) || grid.isBlocked(goalX, goalY) && !orNearest) {
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

        int nearest = startIndex;
        long nearestAway = away(startX, startY, goalX, goalY);
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
            long away = away(cx, cy, goalX, goalY);
            if (away < nearestAway || away == nearestAway && (gScore[current] < gScore[nearest]
                    || gScore[current] == gScore[nearest] && current < nearest)) {
                nearest = current;
                nearestAway = away;
            }
            for (var step : NEIGHBOURS) {
                expand(grid, gScore, fScore, cameFrom, closed, open,
                        cx, cy, step[0], step[1], goalX, goalY, clearance);
            }
        }
        if (!orNearest) {
            return Path.EMPTY;
        }
        if (nearest == startIndex) {
            return Path.partial(List.of()); // nowhere nearer than where it stands
        }
        var there = grid.cellCenter(nearest % width, nearest / width);
        return Path.partial(reconstruct(grid, cameFrom, nearest, startIndex, from, there, clearance).getWaypoints());
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
