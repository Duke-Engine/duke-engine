package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.Pathfinder.Tally;
import uz.dukeengine.core.pathfind.Pathfinder.Traffic;

/**
 * A* over the ground's cells from one point to another, by {@link Steps}: the open set ordered by {@code f = g + h},
 * ties to the lower cell index, the route then pulled straight ({@link Lines#straighten}).
 */
final class GridSearch {

    /** How many cells ahead a route kept to some sectors is pulled straight from each of its corners. */
    static final int STRAIGHTENED_AHEAD = 48;

    /** The cells a search may step onto, where it is kept to some of them — see {@link SectorSearch#corridorOf}. */
    @FunctionalInterface
    interface Allowed {
        boolean allows(int cx, int cy);
    }

    private GridSearch() {
    }

    /**
     * A* from {@code from} to {@code to}. With {@code orNearest}, a goal that cannot be reached — blocked, or
     * walled off — gives a {@linkplain Path#partial partial} route to the searched cell nearest it rather than
     * none: the search has walked every cell it could reach by the time it gives up, so the answer costs
     * nothing more than the failure did.
     */
    static Path search(PathGrid grid, Coord3D from, Coord3D to, float clearance, boolean orNearest, Tally tally,
            Traffic traffic) {
        return search(grid, from, to, clearance, orNearest, tally, traffic, null, null);
    }

    /** The same, the cells it chose added to {@code cellsChosen} where given, and kept to the {@code allowed} cells. */
    static Path search(PathGrid grid, Coord3D from, Coord3D to, float clearance, boolean orNearest, Tally tally,
            Traffic traffic, List<Integer> cellsChosen, Allowed allowed) {
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
        int ahead = allowed == null ? Integer.MAX_VALUE : STRAIGHTENED_AHEAD;
        var scratch = SearchScratch.take(width * grid.getHeight());
        try {
            int startIndex = grid.index(startX, startY);
            int goalIndex = grid.index(goalX, goalY);
            scratch.set(startIndex, 0, Steps.heuristic(startX, startY, goalX, goalY), -1);
            var open = scratch.open;
            open.add(startIndex);

            int nearest = startIndex;
            long nearestAway = Steps.away(startX, startY, goalX, goalY);
            while (!open.isEmpty()) {
                int current = open.poll();
                if (current == goalIndex) {
                    if (cellsChosen != null) {
                        cellsChosen.addAll(scratch.cellsTo(current, startIndex));
                    }
                    return reconstruct(grid, scratch, current, startIndex, from, to, clearance, traffic, ahead);
                }
                if (scratch.closed(current)) {
                    continue; // stale entry from a superseded g-score
                }
                Tally.examined(tally);
                scratch.close(current);

                int cx = current % width;
                int cy = current / width;
                long away = Steps.away(cx, cy, goalX, goalY);
                if (away < nearestAway || away == nearestAway && (scratch.g(current) < scratch.g(nearest)
                        || scratch.g(current) == scratch.g(nearest) && current < nearest)) {
                    nearest = current;
                    nearestAway = away;
                }
                for (var step : Steps.NEIGHBOURS) {
                    expand(grid, scratch, cx, cy, step, goalX, goalY, clearance, traffic, allowed);
                }
            }
            if (!orNearest) {
                return Path.EMPTY;
            }
            if (nearest == startIndex) {
                return Path.partial(List.of()); // nowhere nearer than where it stands
            }
            var there = grid.cellCenter(nearest);
            return Path.partial(reconstruct(grid, scratch, nearest, startIndex, from, there, clearance, traffic,
                    ahead).getWaypoints());
        } finally {
            scratch.release();
        }
    }

    private static void expand(PathGrid grid, SearchScratch scratch, int cx, int cy, int[] step, int goalX,
            int goalY, float clearance, Traffic traffic, Allowed allowed) {
        int nx = cx + step[0];
        int ny = cy + step[1];
        if (allowed != null && !allowed.allows(nx, ny) || !Steps.canTake(grid, cx, cy, step, clearance)) {
            return;
        }
        int neighbour = grid.index(nx, ny);
        if (scratch.closed(neighbour)) {
            return;
        }
        int here = grid.index(cx, cy);
        int tentative = Steps.costVia(grid, scratch.g(here), scratch.came(here), cx, cy, step, traffic);
        if (tentative == Traffic.CLOSED || tentative >= scratch.g(neighbour)) {
            return;
        }
        scratch.set(neighbour, tentative, tentative + Steps.heuristic(nx, ny, goalX, goalY), here);
        scratch.open.add(neighbour);
    }

    /**
     * The route the search came to {@code goal} by, its last waypoint {@code exactTo} rather than the cell's centre,
     * pulled straight from {@code exactFrom} with lines tried {@code ahead} waypoints on.
     */
    static Path reconstruct(PathGrid grid, SearchScratch scratch, int goal, int start, Coord3D exactFrom,
            Coord3D exactTo, float clearance, Traffic traffic, int ahead) {
        var waypoints = Lines.waypoints(grid, scratch.cellsTo(goal, start), exactTo);
        return new Path(Lines.straighten(grid, exactFrom, waypoints, clearance, traffic, ahead));
    }

    /**
     * The cells a route from {@code from} to {@code to} goes through, in order, before it is pulled straight — what
     * the search itself chose, which straightening hides on open ground.
     */
    static List<Integer> cellsOf(PathGrid grid, Coord3D from, Coord3D to) {
        var cells = new ArrayList<Integer>();
        search(grid, from, to, 0f, false, null, null, cells, null);
        return cells;
    }

    /**
     * What walking from {@code from} to {@code to} costs a body with no width, in world units — a cell for a straight
     * step, 1.4 for a diagonal, and the turns — the search giving up after {@code mostCells} cells, with {@link
     * Integer#MAX_VALUE} then or where there is no way ({@code Pathfinder::checkPathCost}, which gives up at 500).
     */
    static int walkCost(PathGrid grid, Coord3D from, Coord3D to, int mostCells) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);
        if (grid.isBlocked(startX, startY) || grid.isBlocked(goalX, goalY)) {
            return Integer.MAX_VALUE;
        }
        int width = grid.getWidth();
        int start = grid.index(startX, startY);
        int goal = grid.index(goalX, goalY);
        var cost = new HashMap<Integer, Integer>();
        var cameFrom = new HashMap<Integer, Integer>();
        var closed = new HashSet<Integer>();
        var open = new PriorityQueue<int[]>((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0])
                : Integer.compare(a[1], b[1]));
        cost.put(start, 0);
        open.add(new int[] {Steps.heuristic(startX, startY, goalX, goalY), start});
        while (!open.isEmpty() && closed.size() < mostCells) {
            int current = open.poll()[1];
            if (current == goal) {
                return Math.round(cost.get(current) * grid.getCellSize() / Steps.ORTHOGONAL_COST);
            }
            if (!closed.add(current)) {
                continue;
            }
            int cx = current % width;
            int cy = current / width;
            for (var step : Steps.NEIGHBOURS) {
                int next = grid.index(cx + step[0], cy + step[1]);
                if (!Steps.canTake(grid, cx, cy, step, 0f) || closed.contains(next)) {
                    continue;
                }
                int tentative = Steps.costVia(grid, cost.get(current), cameFrom.getOrDefault(current, -1), cx, cy,
                        step, null);
                if (tentative >= cost.getOrDefault(next, Integer.MAX_VALUE)) {
                    continue;
                }
                cost.put(next, tentative);
                cameFrom.put(next, current);
                open.add(new int[] {tentative + Steps.heuristic(cx + step[0], cy + step[1], goalX, goalY), next});
            }
        }
        return Integer.MAX_VALUE;
    }
}
