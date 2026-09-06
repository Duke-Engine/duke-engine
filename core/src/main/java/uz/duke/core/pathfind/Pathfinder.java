package uz.duke.core.pathfind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import uz.duke.core.math.Coord3D;

/**
 * A* search over a {@link PathGrid}, ported from SAGE's {@code Pathfinder}.
 *
 * <p>Finds a short cell path between two world positions over an 8-connected
 * grid (orthogonal step cost 10, diagonal 14, matching SAGE's integer cost
 * scale), then returns it as world-space {@link Path} waypoints.
 *
 * <p>Determinism is essential for lock-step: the open set is ordered by
 * {@code f = g + h} and ties are broken by cell index, neighbours are always
 * visited in the same fixed order, and all costs are integers — so the same
 * grid and endpoints always yield the same path on every machine.
 */
public final class Pathfinder {

    private static final int ORTHOGONAL_COST = 10;
    private static final int DIAGONAL_COST = 14;

    // Fixed neighbour order: orthogonals first, then diagonals.
    private static final int[][] NEIGHBOURS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private Pathfinder() {
    }

    /** Find a path from {@code from} to {@code to}, or {@link Path#EMPTY} if none. */
    public static Path findPath(PathGrid grid, Coord3D from, Coord3D to) {
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
                return reconstruct(grid, cameFrom, current, startIndex, to);
            }
            if (closed[current]) {
                continue; // stale entry from a superseded g-score
            }
            closed[current] = true;

            int cx = current % width;
            int cy = current / width;
            for (var step : NEIGHBOURS) {
                expand(grid, gScore, fScore, cameFrom, closed, open,
                        cx, cy, step[0], step[1], goalX, goalY);
            }
        }
        return Path.EMPTY;
    }

    private static void expand(PathGrid grid, int[] gScore, int[] fScore, int[] cameFrom,
            boolean[] closed, PriorityQueue<Integer> open,
            int cx, int cy, int dx, int dy, int goalX, int goalY) {
        int nx = cx + dx;
        int ny = cy + dy;
        if (grid.isBlocked(nx, ny)) {
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

    private static Path reconstruct(PathGrid grid, int[] cameFrom, int goal, int start, Coord3D exactTo) {
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
        return new Path(waypoints);
    }
}
