package uz.dukeengine.core.pathfind;

import uz.dukeengine.core.math.Coord3D;

/** Where can be walked to from a point: a flood by exactly the search's own {@link Steps}. */
final class Reach {

    /** The cells a flood reached, in the order it reached them: {@code cells[0..count)}. */
    private record Flood(int[] cells, int count) {
    }

    private Reach() {
    }

    /** See {@link Pathfinder#reachableFrom}. */
    static boolean[] reachableFrom(PathGrid grid, Coord3D from, float clearance) {
        var reached = new boolean[grid.getWidth() * grid.getHeight()];
        var flood = flood(grid, from, clearance);
        for (int at = 0; at < flood.count(); at++) {
            reached[flood.cells()[at]] = true;
        }
        return reached;
    }

    /** See {@link Pathfinder#nearestReachable}. */
    static Coord3D nearestReachable(PathGrid grid, Coord3D from, Coord3D wanted) {
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
            long away = Steps.away(cx, cy, goalX, goalY);
            if (away < nearest) {
                nearest = away;
                best = cell;
            }
        }
        var reached = grid.cellCenter(best);
        return new Coord3D(reached.x(), reached.y(), wanted.z());
    }

    /**
     * Outward from {@code from}, a ring at a time and in the search's fixed neighbour order, by exactly the steps
     * the search would take — so a cell this reaches is a cell a route can be found to, on every machine alike.
     */
    private static Flood flood(PathGrid grid, Coord3D from, float clearance) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        if (grid.isBlocked(startX, startY)) {
            var way = Room.nearestOpenCell(grid, from);
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
            for (var step : Steps.NEIGHBOURS) {
                int nx = cx + step[0];
                int ny = cy + step[1];
                if (!grid.inBounds(nx, ny) || seen[grid.index(nx, ny)] || !Steps.canTake(grid, cx, cy, step,
                        clearance)) {
                    continue;
                }
                seen[grid.index(nx, ny)] = true;
                queue[tail++] = grid.index(nx, ny);
            }
            // Across an open deck, end to end: its every entry is reached from any of them.
            for (var deck : grid.decks()) {
                if (!deck.isOpen() || deck.entries().stream().noneMatch(entry -> entry[0] == cell)) {
                    continue;
                }
                for (var entry : deck.entries()) {
                    if (!seen[entry[0]] && !grid.isBlocked(entry[0] % width, entry[0] / width)) {
                        seen[entry[0]] = true;
                        queue[tail++] = entry[0];
                    }
                }
            }
        }
        return new Flood(queue, tail);
    }
}
