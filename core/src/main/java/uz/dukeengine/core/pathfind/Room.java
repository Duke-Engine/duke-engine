package uz.dukeengine.core.pathfind;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;

/**
 * Where a body may stand: whether a cell holds a body of a width, the nearest that does, the clearance a body squeezes
 * by, and the way off blocked ground for one standing on it.
 */
final class Room {

    /** How far to look for a way out before deciding there is none. */
    private static final int ESCAPE_RINGS = 8;

    private Room() {
    }

    /**
     * Whether a cell can hold a body of {@code clearance} radius at its centre: open, with room from stone, and clear
     * of the circles standing in the way by its true distance — a body with no width only kept out of them. Kept by
     * the grid cell by cell ({@link PathGrid#fits}).
     */
    static boolean fits(PathGrid grid, int cx, int cy, float clearance) {
        if (!grid.inBounds(cx, cy)) {
            return false;
        }
        var known = grid.fits(clearance);
        int index = cy * grid.getWidth() + cx;
        byte answer = known.cells[index];
        if (answer == 0) {
            answer = fitsNow(grid, cx, cy, clearance) ? (byte) 1 : (byte) 2;
            known.cells[index] = answer;
        }
        return answer == 1;
    }

    /** The same, worked out now. */
    private static boolean fitsNow(PathGrid grid, int cx, int cy, float clearance) {
        if (grid.isBlocked(cx, cy)) {
            return false;
        }
        float x = (cx + 0.5f) * grid.getCellSize();
        float y = (cy + 0.5f) * grid.getCellSize();
        if (clearance < 0f) {
            return grid.clearOfCircles(x, y, -clearance); // squeezed: see squeezed()
        }
        return clearance == 0f ? grid.clearOfCircles(x, y, 0f) : isClearAround(grid, x, y, clearance);
    }

    /** Whether every cell a body of {@code clearance} radius would touch is free, and the circles clear of it. */
    static boolean isClearAround(PathGrid grid, float x, float y, float clearance) {
        if (clearance < 0f) {
            // Squeezed: the cell under its middle open, as a walker's step reads it, and the circles kept off its body.
            return !grid.isBlocked(grid.toCellX(new Coord3D(x, y, 0f)), grid.toCellY(new Coord3D(x, y, 0f)))
                    && grid.clearOfCircles(x, y, -clearance);
        }
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
        return grid.clearOfCircles(x, y, clearance);
    }

    /**
     * The clearance a body of {@code clearance} squeezes by: through stone by its middle, as a walker's step is held only
     * by the cell under it, but kept its whole width off the trunks and round still things, which hold its body (E102).
     * Written as the clearance turned negative, which {@link #fits} and {@link #isClearAround} read so; 0 stays 0, and a
     * body standing in a circle already squeezes by its middle alone.
     */
    static float squeezed(PathGrid grid, Coord3D from, float clearance) {
        // One standing in a circle already is let out of it squeezed by its middle alone, or its first step is refused.
        return clearance > 0f && grid.clearOfCircles(from.x(), from.y(), clearance) ? -clearance : 0f;
    }

    /**
     * The cell nearest cell ({@code cx}, {@code cy}) that a body of {@code clearance} fits at — and, zones given, in
     * zone {@code zone} — ring by ring outward as far as the body is wide and two cells more, the nearest to the
     * cell's middle of a ring and then the lower index winning; -1 for none.
     */
    static int nearestWithRoom(PathGrid grid, int cx, int cy, float clearance, Zones zones, int zone) {
        int rings = (int) Math.ceil(clearance / grid.getCellSize()) + 2;
        for (int ring = 1; ring <= rings; ring++) {
            int best = -1;
            long bestAway = Long.MAX_VALUE;
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    if (!grid.inBounds(x, y) || !fits(grid, x, y, clearance)
                            || zones != null && zone >= 0 && zones.zoneOf(x, y) != zone) {
                        continue;
                    }
                    long away = (long) dx * dx + (long) dy * dy;
                    int index = grid.index(x, y);
                    if (away < bestAway || away == bestAway && index < best) {
                        best = index;
                        bestAway = away;
                    }
                }
            }
            if (best >= 0) {
                return best;
            }
        }
        return -1;
    }

    /**
     * Off the blocked cell it stands on: the only useful first move from there, and a route that ends short
     * of wherever it was going, since it is only the way out.
     */
    static Path escape(PathGrid grid, Coord3D from) {
        var way = nearestOpenCell(grid, from);
        return way == null ? Path.EMPTY : Path.partial(List.of(way));
    }

    /**
     * The centre of the closest cell that can be stood on, or {@code null} if the
     * search gives up.
     *
     * <p>Rings outward from the mover, and within a ring in a fixed order, so two
     * machines pick the same way out. Bounded because a world can be solid stone,
     * and a mover in the middle of one should stop rather than scan it all.
     */
    static Coord3D nearestOpenCell(PathGrid grid, Coord3D from) {
        int cx = grid.toCellX(from);
        int cy = grid.toCellY(from);
        for (int ring = 1; ring <= ESCAPE_RINGS * grid.cellsPerMapCell(); ring++) {
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
}
