package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.Pathfinder.Tally;

/**
 * Routes over the ground and the grid's decks together, a search over (floor, cell): a step along a floor, or onto
 * another only at a deck's entry — see {@link PathGrid#enters}.
 */
final class DeckSearch {

    private DeckSearch() {
    }

    /** See {@link Pathfinder#findPathOrNearest(PathGrid, Coord3D, int, Coord3D, int, float, Zones, Tally)}. */
    static Path findPathOrNearest(PathGrid grid, Coord3D from, int fromFloor, Coord3D to, int toFloor,
            float clearance, Zones zones, Tally tally) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);
        int zone = zones == null ? -1 : zoneOn(grid, zones, fromFloor, startX, startY);
        if (zone >= 0 && zone != zoneOn(grid, zones, toFloor, goalX, goalY)) {
            // Out of reach, known at once as on the ground: straight for the ground of its zone nearest the goal.
            int nearest = zones.nearestIn(zone, goalX, goalY, startX, startY);
            if (nearest < 0 || fromFloor == 0 && nearest == startY * grid.getWidth() + startX) {
                return Path.partial(List.of());
            }
            var there = grid.cellCenter(nearest);
            var way = layered(grid, from, fromFloor, there, 0, clearance, tally);
            if (way.isEmpty() && clearance > 0f) {
                way = layered(grid, from, fromFloor, there, 0, 0f, tally);
            }
            return Path.partial(way.getWaypoints());
        }
        var path = layered(grid, from, fromFloor, to, toFloor, clearance, tally);
        if (path.reachesGoal() || clearance <= 0f) {
            return path;
        }
        var squeezed = layered(grid, from, fromFloor, to, toFloor, 0f, tally);
        return squeezed.reachesGoal() || NearestRoute.awayFrom(squeezed, from, to) < NearestRoute.awayFrom(path,
                from, to) ? squeezed : path;
    }

    /**
     * The zone of a cell on a floor: the ground's own, or — on a deck — that of the ground at its entries, which its
     * zones join; -1 for none.
     */
    private static int zoneOn(PathGrid grid, Zones zones, int floor, int cx, int cy) {
        if (floor == 0) {
            return zones.zoneOf(cx, cy);
        }
        int width = grid.getWidth();
        for (var deck : grid.decks()) {
            if (deck.floor() != floor) {
                continue;
            }
            for (var entry : deck.entries()) {
                int z = zones.zoneOf(entry[0] % width, entry[0] / width);
                if (z >= 0) {
                    return z;
                }
            }
        }
        return -1;
    }

    /**
     * A* over (floor, cell): the ground's cells and each deck's, a step along a floor or onto another at an entry.
     * The arrays of a floor are made the first time the search touches it, so a map of many decks costs what the decks
     * a route goes near cost. Nearest the goal where it cannot be reached, as {@link GridSearch#search} with {@code
     * orNearest}.
     */
    private static Path layered(PathGrid grid, Coord3D from, int fromFloor, Coord3D to, int toFloor, float clearance,
            Tally tally) {
        int width = grid.getWidth();
        int cells = width * grid.getHeight();
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);
        if (!grid.inBounds(startX, startY)) {
            return Path.EMPTY;
        }
        if (fromFloor == 0 && grid.isBlocked(startX, startY)) {
            return Room.escape(grid, from);
        }
        int start = fromFloor * cells + grid.index(startX, startY);
        int goal = grid.inBounds(goalX, goalY) ? toFloor * cells + grid.index(goalX, goalY) : -1;
        if (start == goal) {
            return new Path(List.of(to));
        }
        int floors = grid.decks().size() + 1;
        var scratch = SearchScratch.take(floors * cells);
        try {
            scratch.set(start, 0, Steps.heuristic(startX, startY, goalX, goalY), -1);
            var open = scratch.open;
            open.add(start);
            int nearest = start;
            long nearestAway = Steps.away(startX, startY, goalX, goalY);
            while (!open.isEmpty()) {
                int current = open.poll();
                if (current == goal) {
                    return layeredPath(grid, scratch, cells, current, start, from, fromFloor, to, clearance, true);
                }
                int floor = current / cells;
                int cell = current % cells;
                if (scratch.closed(current)) {
                    continue;
                }
                Tally.examined(tally);
                scratch.close(current);
                int cx = cell % width;
                int cy = cell / width;
                long away = Steps.away(cx, cy, goalX, goalY);
                if (away < nearestAway || away == nearestAway && current < nearest) {
                    nearest = current;
                    nearestAway = away;
                }
                for (var step : Steps.NEIGHBOURS) {
                    int nx = cx + step[0];
                    int ny = cy + step[1];
                    if (!grid.inBounds(nx, ny)) {
                        continue;
                    }
                    boolean diagonal = Steps.isDiagonal(step);
                    int cost = scratch.g(current) + Steps.stepCost(grid, diagonal, nx, ny);
                    boolean along = floor == 0
                            ? Steps.canTake(grid, cx, cy, step, clearance)
                            : grid.walks(floor, cx, cy, nx, ny) && (!diagonal
                                    || grid.walks(floor, cx, cy, nx, cy) || grid.walks(floor, cx, cy, cx, ny));
                    if (along) {
                        relax(scratch, floor, nx, ny, cells, width, cost, current, goalX, goalY);
                    }
                    // Onto another floor: a deck from the ground at its entry, the ground from a deck's end.
                    if (floor != 0) {
                        if (grid.enters(floor, 0, cx, cy, nx, ny) && Room.fits(grid, nx, ny, clearance)) {
                            relax(scratch, 0, nx, ny, cells, width, cost, current, goalX, goalY);
                        }
                        continue;
                    }
                    for (var deck : grid.decks()) {
                        if (grid.enters(0, deck.floor(), cx, cy, nx, ny)) {
                            relax(scratch, deck.floor(), nx, ny, cells, width, cost, current, goalX, goalY);
                        }
                    }
                }
            }
            if (nearest == start) {
                return Path.partial(List.of());
            }
            var there = grid.cellCenter(nearest % cells);
            var there3 = new Coord3D(there.x(), there.y(), grid.heightOn(nearest / cells, there));
            return layeredPath(grid, scratch, cells, nearest, start, from, fromFloor, there3, clearance, false);
        } finally {
            scratch.release();
        }
    }

    /** A step onto (floor, cell) at {@code cost}: kept where it is the cheapest way there yet. */
    private static void relax(SearchScratch scratch, int onto, int nx, int ny, int cells, int width, int cost,
            int current, int goalX, int goalY) {
        int next = onto * cells + ny * width + nx;
        if (scratch.closed(next) || cost >= scratch.g(next)) {
            return;
        }
        scratch.set(next, cost, cost + Steps.heuristic(nx, ny, goalX, goalY), current);
        scratch.open.add(next);
    }

    /**
     * The route the search found, as waypoints at the height of each one's floor, straightened a floor at a time: a
     * step onto another floor is kept, where it is, since the line past it crosses from one floor to another.
     */
    private static Path layeredPath(PathGrid grid, SearchScratch scratch, int cells, int goal, int start,
            Coord3D from, int fromFloor, Coord3D to, float clearance, boolean reaches) {
        var nodes = scratch.cellsTo(goal, start);
        int count = nodes.size();
        var points = new Coord3D[count];
        var floors = new int[count];
        for (int i = 0; i < count; i++) {
            int node = nodes.get(i);
            floors[i] = node / cells;
            var centre = grid.cellCenter(node % cells);
            points[i] = i == count - 1 ? to
                    : new Coord3D(centre.x(), centre.y(), grid.heightOn(floors[i], centre));
        }
        var straightened = new ArrayList<Coord3D>();
        var onFloor = new ArrayList<Integer>();
        var anchor = from;
        int i = 0;
        while (i < count) {
            int runEnd = i;
            while (runEnd + 1 < count && floors[runEnd + 1] == floors[i]) {
                runEnd++;
            }
            int floor = floors[i];
            int k = i;
            if (i > 0 || floor != fromFloor) {
                straightened.add(points[i]); // where it stepped onto this floor: kept
                onFloor.add(floor);
                anchor = points[i];
                k = i + 1;
            }
            while (k <= runEnd) {
                int furthest = k;
                for (int j = runEnd; j > k; j--) {
                    if (Lines.clearOn(grid, floor, anchor, points[j], clearance)) {
                        furthest = j;
                        break;
                    }
                }
                anchor = points[furthest];
                straightened.add(anchor);
                onFloor.add(floor);
                k = furthest + 1;
            }
            i = runEnd + 1;
        }
        return Path.onFloors(straightened, onFloor.stream().mapToInt(Integer::intValue).toArray(),
                reaches && !straightened.isEmpty());
    }
}
