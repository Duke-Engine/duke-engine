package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.IntBinaryOperator;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.GridSearch.Allowed;
import uz.dukeengine.core.pathfind.Pathfinder.Tally;
import uz.dukeengine.core.pathfind.Pathfinder.Traffic;

/**
 * A route across a wide world by its {@link Sectors}: the sectors a way goes through found first, piece by piece, and
 * the cells looked at only in them and the ring of sectors round each, by the search's own {@link Steps}.
 */
final class SectorSearch {

    /** How many sectors of the way a stretch of a route across a wide world crosses. */
    private static final int SECTORS_A_STRETCH = 2;

    private SectorSearch() {
    }

    /**
     * A route along the way the sectors give ({@link Sectors#way}), a stretch at a time: from where it stands to the
     * first cell of the way's piece {@link #SECTORS_A_STRETCH} sectors on, then on from there, the last stretch to the
     * goal itself — each looked for only in its own sectors and the ring round them, by the search's own steps and
     * costs, then the whole pulled straight as any route is. A stretch examines the ground near it, so a route across
     * the world costs what its length does. A short way, one with no stretch that finds its end — no room for the
     * mover's width, a mover in the way — and ends no way joins, are looked for in one search kept to the way's sectors.
     */
    static Path stretchByStretch(PathGrid grid, Sectors sectors, Coord3D from, Coord3D to, float clearance,
            boolean orNearest, Tally tally, Traffic traffic) {
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
        int startCell = startY * width + startX;
        if (sectors.clearance() > 0f && !Room.fits(grid, startX, startY, sectors.clearance())) {
            int room = Room.nearestWithRoom(grid, startX, startY, sectors.clearance(), null, -1);
            startCell = room >= 0 ? room : startCell; // pressed against a trunk: its way read from beside it
        }
        var way = grid.inBounds(goalX, goalY) ? sectors.way(startCell, goalY * width + goalX) : null;
        if (way == null || way.length <= SECTORS_A_STRETCH + 1) {
            return inCorridor(grid, sectors, from, to, clearance, orNearest, tally, traffic);
        }
        var cells = new ArrayList<Integer>();
        int at = startY * width + startX;
        int stretchFrom = 0;
        while (stretchFrom + SECTORS_A_STRETCH < way.length - 1) {
            int stretchTo = stretchFrom + SECTORS_A_STRETCH;
            int target = way[stretchTo];
            var box = sectorBox(sectors, sectors.sectorOfPiece(target));
            var stretch = stretch(grid, sectors, at, cell -> sectors.pieceAt(cell % width, cell / width) == target,
                    (cx, cy) -> toBox(cx, cy, box), clearance, tally, traffic,
                    keptTo(sectors, way, stretchFrom, stretchTo), -1, -1);
            if (stretch == null) {
                return inCorridor(grid, sectors, from, to, clearance, orNearest, tally, traffic);
            }
            cells.addAll(stretch);
            at = cells.getLast();
            stretchFrom = stretchTo;
        }
        int goal = goalY * width + goalX;
        var last = stretch(grid, sectors, at, cell -> cell == goal, (cx, cy) -> Steps.heuristic(cx, cy, goalX, goalY),
                clearance, tally, traffic, keptTo(sectors, way, stretchFrom, way.length - 1), goalX, goalY);
        if (last == null) {
            return inCorridor(grid, sectors, from, to, clearance, orNearest, tally, traffic);
        }
        cells.addAll(last);
        boolean reaches = !cells.isEmpty() && cells.getLast() == goal;
        if (!reaches && !orNearest) {
            return Path.EMPTY;
        }
        var waypoints = Lines.waypoints(grid, cells, reaches ? to : null);
        var straightened = Lines.straighten(grid, from, waypoints, clearance, traffic, GridSearch.STRAIGHTENED_AHEAD);
        return reaches ? new Path(straightened) : Path.partial(straightened);
    }

    /** One search from {@code from} to {@code to} kept to the sectors a way between them goes through. */
    private static Path inCorridor(PathGrid grid, Sectors sectors, Coord3D from, Coord3D to, float clearance,
            boolean orNearest, Tally tally, Traffic traffic) {
        return GridSearch.search(grid, from, to, clearance, orNearest, tally, traffic, null,
                corridorOf(sectors, grid, from, to));
    }

    /** The sectors a stretch of the way from its {@code from}-th piece to its {@code to}-th crosses, and the ring round. */
    private static int[] keptTo(Sectors sectors, int[] way, int from, int to) {
        return around(sectors, IntStream.rangeClosed(from, to).map(i -> sectors.sectorOfPiece(way[i])))
                .stream().toArray();
    }

    /**
     * The sectors a way from {@code from} to {@code to} goes through ({@link Sectors#way}) and the ring of sectors
     * round each; where no way through the sectors joins them — an end in no piece — the sectors round the two ends.
     */
    static Allowed corridorOf(Sectors sectors, PathGrid grid, Coord3D from, Coord3D to) {
        int width = grid.getWidth();
        int startX = Math.clamp(grid.toCellX(from), 0, width - 1);
        int startY = Math.clamp(grid.toCellY(from), 0, grid.getHeight() - 1);
        int goalX = Math.clamp(grid.toCellX(to), 0, width - 1);
        int goalY = Math.clamp(grid.toCellY(to), 0, grid.getHeight() - 1);
        var way = sectors.way(startY * width + startX, goalY * width + goalX);
        var crossed = way == null
                ? IntStream.of(sectors.sectorOf(startX, startY), sectors.sectorOf(goalX, goalY))
                : Arrays.stream(way).map(sectors::sectorOfPiece);
        var kept = around(sectors, crossed);
        return (cx, cy) -> grid.inBounds(cx, cy) && kept.get(sectors.sectorOf(cx, cy));
    }

    /** The sectors {@code crossed} and the ring of sectors round each. */
    private static BitSet around(Sectors sectors, IntStream crossed) {
        int across = sectors.across();
        int down = sectors.down();
        var kept = new BitSet(across * down);
        crossed.forEach(sector -> {
            int sx = sector % across;
            int sy = sector / across;
            for (int y = Math.max(0, sy - 1); y <= Math.min(down - 1, sy + 1); y++) {
                for (int x = Math.max(0, sx - 1); x <= Math.min(across - 1, sx + 1); x++) {
                    kept.set(y * across + x);
                }
            }
        });
        return kept;
    }

    /** A sector's cells, {x0, y0, x1, y1}. */
    private static int[] sectorBox(Sectors sectors, int sector) {
        int size = sectors.size();
        int x0 = (sector % sectors.across()) * size;
        int y0 = (sector / sectors.across()) * size;
        var grid = sectors.grid();
        return new int[] {x0, y0, Math.min(x0 + size, grid.getWidth()) - 1, Math.min(y0 + size, grid.getHeight()) - 1};
    }

    /** The search's estimate from a cell to the nearest cell of a box. */
    private static int toBox(int cx, int cy, int[] box) {
        int dx = cx < box[0] ? box[0] - cx : cx > box[2] ? cx - box[2] : 0;
        int dy = cy < box[1] ? box[1] - cy : cy > box[3] ? cy - box[3] : 0;
        return Steps.estimate(dx, dy);
    }

    /**
     * A* from cell {@code from} to the first cell {@code reached} takes, by the search's own steps, costs and turns,
     * {@code estimate} its guess of the rest, kept to the sectors {@code kept}: the cells after {@code from} to it, or —
     * given a goal cell ({@code goalX}, {@code goalY}) it cannot reach — to the cell nearest it the search came to, as
     * {@link GridSearch#search} with {@code orNearest}; null where it reaches nothing it was sent for. Its open cells
     * are taken the cheapest guess first, a tie to the lower cell.
     */
    private static List<Integer> stretch(PathGrid grid, Sectors sectors, int from, IntPredicate reached,
            IntBinaryOperator estimate, float clearance, Tally tally, Traffic traffic, int[] kept, int goalX,
            int goalY) {
        int width = grid.getWidth();
        var window = SectorWindow.over(sectors, kept);
        try {
            int fromX = from % width;
            int fromY = from / width;
            window.set(window.at(fromX, fromY), 0, -1);
            var open = window.open;
            open.push(estimate.applyAsInt(fromX, fromY), from);
            boolean toACell = goalX >= 0;
            int nearest = from;
            long nearestAway = toACell ? Steps.away(fromX, fromY, goalX, goalY) : Long.MAX_VALUE;
            int end = -1;
            while (!open.isEmpty()) {
                int current = open.popValue();
                int cx = current % width;
                int cy = current / width;
                int here = window.at(cx, cy);
                if (current != from && reached.test(current)) {
                    end = current;
                    break;
                }
                if (window.closed(here)) {
                    continue;
                }
                Tally.examined(tally);
                window.close(here);
                int cost = window.g(here);
                if (toACell) {
                    long away = Steps.away(cx, cy, goalX, goalY);
                    int nearestCost = window.g(window.at(nearest % width, nearest / width));
                    if (away < nearestAway || away == nearestAway
                            && (cost < nearestCost || cost == nearestCost && current < nearest)) {
                        nearest = current;
                        nearestAway = away;
                    }
                }
                int cameFrom = window.came(here);
                for (var step : Steps.NEIGHBOURS) {
                    int nx = cx + step[0];
                    int ny = cy + step[1];
                    if (!grid.inBounds(nx, ny)) {
                        continue;
                    }
                    int there = window.at(nx, ny);
                    if (there < 0 || window.closed(there) || !Steps.canTake(grid, cx, cy, step, clearance)) {
                        continue;
                    }
                    int tentative = Steps.costVia(grid, cost, cameFrom, cx, cy, step, traffic);
                    if (tentative == Traffic.CLOSED || tentative >= window.g(there)) {
                        continue;
                    }
                    window.set(there, tentative, current);
                    open.push(tentative + estimate.applyAsInt(nx, ny), ny * width + nx);
                }
            }
            if (end < 0) {
                if (!toACell || nearest == from) {
                    return toACell ? List.of() : null;
                }
                end = nearest;
            }
            var cells = new ArrayList<Integer>();
            for (int cell = end; cell != -1 && cell != from; cell = window.came(window.at(cell % width, cell / width))) {
                cells.add(cell);
            }
            Collections.reverse(cells);
            return cells;
        } finally {
            window.release();
        }
    }
}
