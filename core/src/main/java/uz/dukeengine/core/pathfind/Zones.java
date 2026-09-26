package uz.dukeengine.core.pathfind;

/**
 * Which cells of a {@link PathGrid} can reach which: every open cell labelled with the connected region it lies in,
 * by exactly the steps the search takes — so "can this cell reach that one" is answered at once, rather than by a
 * search that walks every cell it can reach before it knows it cannot. The reference keeps the same map of zones
 * ({@code PathfindZoneManager}), recomputed when a structure goes up or comes down.
 *
 * <p>For a body with no width: a narrow gap joins two regions here that a wide body squeezes through, as the
 * search itself lets it squeeze when there is no way with room. A cell nothing can step into — stone, something
 * standing on it, a cliff of the relief — is in no zone.
 *
 * <p>Computed whole, in cell order, from the grid as it stands; {@link #isCurrent} says whether the grid has
 * changed shape since. Deterministic: a flood in the search's own neighbour order.
 */
public final class Zones {

    private static final int[][] NEIGHBOURS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private final PathGrid grid;
    private final int version;
    private final int[] zone;
    private final int count;

    private Zones(PathGrid grid, int version, int[] zone, int count) {
        this.grid = grid;
        this.version = version;
        this.zone = zone;
        this.count = count;
    }

    /** The zones of {@code grid} as it stands now. */
    public static Zones of(PathGrid grid) {
        int width = grid.getWidth();
        int cells = width * grid.getHeight();
        var zone = new int[cells];
        java.util.Arrays.fill(zone, -1);
        var queue = new int[cells];
        int next = 0;
        for (int start = 0; start < cells; start++) {
            if (zone[start] >= 0 || !enterable(grid, start % width, start / width)) {
                continue;
            }
            int head = 0;
            int tail = 0;
            zone[start] = next;
            queue[tail++] = start;
            while (head < tail) {
                int cell = queue[head++];
                int cx = cell % width;
                int cy = cell / width;
                for (var step : NEIGHBOURS) {
                    int nx = cx + step[0];
                    int ny = cy + step[1];
                    if (!grid.inBounds(nx, ny) || zone[ny * width + nx] >= 0 || !enterable(grid, nx, ny)
                            || !grid.canStep(cx, cy, nx, ny)) {
                        continue;
                    }
                    boolean diagonal = step[0] != 0 && step[1] != 0;
                    if (diagonal && (grid.isBlocked(cx + step[0], cy) || grid.isBlocked(cx, cy + step[1]))) {
                        continue; // the search does not cut a corner, so neither may a zone
                    }
                    zone[ny * width + nx] = next;
                    queue[tail++] = ny * width + nx;
                }
            }
            next++;
        }
        joinAcrossDecks(grid, zone, next);
        return new Zones(grid, grid.getShapeVersion(), zone, next);
    }

    /**
     * The zones an open deck joins made one: whatever its entries stand in can reach whatever its other entries do,
     * across it. The lowest zone joined names the whole, so the labels are the same on every machine.
     */
    private static void joinAcrossDecks(PathGrid grid, int[] zone, int count) {
        if (!grid.hasDecks()) {
            return;
        }
        var parent = new int[count];
        for (int z = 0; z < count; z++) {
            parent[z] = z;
        }
        for (var deck : grid.decks()) {
            if (!deck.isOpen()) {
                continue;
            }
            int joined = -1;
            for (var entry : deck.entries()) {
                int z = zone[entry[0]];
                if (z < 0) {
                    continue;
                }
                if (joined < 0) {
                    joined = root(parent, z);
                    continue;
                }
                int a = root(parent, joined);
                int b = root(parent, z);
                parent[Math.max(a, b)] = Math.min(a, b);
                joined = Math.min(a, b);
            }
        }
        for (int cell = 0; cell < zone.length; cell++) {
            if (zone[cell] >= 0) {
                zone[cell] = root(parent, zone[cell]);
            }
        }
    }

    private static int root(int[] parent, int z) {
        while (parent[z] != z) {
            z = parent[z];
        }
        return z;
    }

    /** Whether a step can end on this cell: open, and not a cliff of the relief. */
    private static boolean enterable(PathGrid grid, int cx, int cy) {
        return !grid.isBlocked(cx, cy) && !grid.isCliff(cx, cy);
    }

    /** Whether these zones are {@code grid}'s as it stands now. */
    public boolean isCurrent(PathGrid grid) {
        return this.grid.root() == grid.root() && this.grid.surfaces() == grid.surfaces()
                && version == grid.getShapeVersion();
    }

    /** The zone a cell lies in, or -1 for one in none. */
    public int zoneOf(int cx, int cy) {
        return grid.inBounds(cx, cy) ? zone[cy * grid.getWidth() + cx] : -1;
    }

    /** Whether a body with no width can walk from one cell to the other. */
    public boolean connected(int fromX, int fromY, int toX, int toY) {
        int from = zoneOf(fromX, fromY);
        return from >= 0 && from == zoneOf(toX, toY);
    }

    /** How many zones there are. */
    public int count() {
        return count;
    }

    /**
     * The cell of zone {@code z} nearest the cell {@code (goalX, goalY)} — by straight-line distance; of cells as near,
     * the one nearest {@code (fromX, fromY)} by the search's own measure, which is the shorter walk wherever the
     * ground between is open; then the lower cell index — or -1 where the zone has no cells. A scan, nothing walked.
     */
    public int nearestIn(int z, int goalX, int goalY, int fromX, int fromY) {
        int width = grid.getWidth();
        int best = -1;
        long nearest = Long.MAX_VALUE;
        int bestWalk = Integer.MAX_VALUE;
        for (int cell = 0; cell < zone.length; cell++) {
            if (zone[cell] != z) {
                continue;
            }
            int cx = cell % width;
            int cy = cell / width;
            long dx = cx - goalX;
            long dy = cy - goalY;
            long away = dx * dx + dy * dy;
            if (away > nearest) {
                continue;
            }
            int walk = octile(cx - fromX, cy - fromY);
            if (away < nearest || walk < bestWalk) {
                nearest = away;
                bestWalk = walk;
                best = cell;
            }
        }
        return best;
    }

    /** The search's own measure of how far apart two cells are: 10 a straight step, 14 a diagonal. */
    private static int octile(int dx, int dy) {
        int across = Math.abs(dx);
        int along = Math.abs(dy);
        return 14 * Math.min(across, along) + 10 * (Math.max(across, along) - Math.min(across, along));
    }
}
