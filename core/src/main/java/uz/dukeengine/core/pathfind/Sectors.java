package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A grid's ground in square sectors: each sector's open cells in the pieces a step joins inside it, and which pieces of
 * neighbouring sectors a step joins — so that what reaches what is known without a pass over every cell, and a route
 * across a wide world is looked for first sector by sector and then cell by cell only in the sectors that way goes
 * through ({@link Pathfinder}). Cells are joined by exactly the steps {@link Zones} joins them by, so the cells it puts
 * in one zone are the cells Zones would.
 *
 * <p>Kept as the grid changes: it reads the grid's record of where it changed ({@link PathGrid#changesSince}) and works
 * out again only the sectors there and the joins round them. Deterministic: sectors and cells in order, each sector's
 * pieces flooded in the search's neighbour order, a zone named by the first piece in it.
 */
public final class Sectors {

    private static final int[][] NEIGHBOURS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    /** The piece of a cell in none: stone, a cliff. */
    private static final short NO_PIECE = -1;

    /** The ground as it is seen: the grid, or a passage of it, taken afresh as it is caught up with. */
    private PathGrid grid;
    private final int size;
    private final int across;
    private final int down;
    /** Each cell's piece of its sector, or {@link #NO_PIECE}. */
    private final short[] piece;
    /** How many pieces each sector has. */
    private final int[] pieces;
    /** Each sector's joins to its neighbours' pieces: {its piece, the neighbour, the neighbour's piece}, in order. */
    private final int[][] joins;
    private int serial;
    /** The first piece of each sector among all pieces, and the zone of each piece; null to be worked out again. */
    private int[] firstNode;
    private int[] zoneOfNode;
    private int[] sectorOfNode;
    private int zoneCount;
    /** A way across's arrays, kept from one to the next and made fresh by a stamp. */
    private int[] costSoFar = new int[0];
    private int[] cameFrom = new int[0];
    private int[] written = new int[0];
    private int[] shut = new int[0];
    private int stamp;
    private final LongHeap open = new LongHeap();

    private Sectors(PathGrid grid, int size) {
        this.grid = grid;
        this.size = size;
        this.across = (grid.getWidth() + size - 1) / size;
        this.down = (grid.getHeight() + size - 1) / size;
        this.piece = new short[grid.getWidth() * grid.getHeight()];
        this.pieces = new int[across * down];
        this.joins = new int[across * down][];
    }

    /** {@code grid} in sectors of {@code size} cells a side, as it stands. */
    public static Sectors of(PathGrid grid, int size) {
        if (size < 2 || size > 256) {
            throw new IllegalArgumentException("a sector is 2 to 256 cells a side: " + size);
        }
        var sectors = new Sectors(grid, size);
        sectors.layAll();
        return sectors;
    }

    public PathGrid grid() {
        return grid;
    }

    /** How many cells a side a sector is. */
    public int size() {
        return size;
    }

    /** How many sectors there are across, and down. */
    public int across() {
        return across;
    }

    public int down() {
        return down;
    }

    /** The sector the cell ({@code cx}, {@code cy}) lies in; the cell must be on the grid. */
    public int sectorOf(int cx, int cy) {
        return (cy / size) * across + cx / size;
    }

    /**
     * Catch up with the grid, seen now as {@code seen} — the same grid, or a passage of it for the same classes, taken
     * as it stands: the sectors where it changed since this last looked laid again, and the joins round them. Whether
     * anything changed.
     */
    public boolean catchUp(PathGrid seen) {
        if (seen.root() != grid.root() || seen.surfaces() != grid.surfaces()) {
            throw new IllegalArgumentException("these sectors are another ground's");
        }
        grid = seen;
        int now = grid.changeSerial();
        if (now == serial) {
            return false;
        }
        var changes = grid.changesSince(serial);
        serial = now;
        if (changes == null) {
            layAll();
            return true;
        }
        var dirty = new java.util.BitSet(across * down);
        for (var box : changes) {
            int fromX = Math.max(0, box[0]) / size;
            int fromY = Math.max(0, box[1]) / size;
            int toX = Math.min(grid.getWidth() - 1, box[2]) / size;
            int toY = Math.min(grid.getHeight() - 1, box[3]) / size;
            if (box[2] < 0 || box[3] < 0 || box[0] >= grid.getWidth() || box[1] >= grid.getHeight()) {
                continue;
            }
            for (int sy = fromY; sy <= toY; sy++) {
                for (int sx = fromX; sx <= toX; sx++) {
                    dirty.set(sy * across + sx);
                }
            }
        }
        var rejoin = new java.util.BitSet(across * down);
        for (int sector = dirty.nextSetBit(0); sector >= 0; sector = dirty.nextSetBit(sector + 1)) {
            lay(sector);
            int sx = sector % across;
            int sy = sector / across;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = sx + dx;
                    int ny = sy + dy;
                    if (nx >= 0 && ny >= 0 && nx < across && ny < down) {
                        rejoin.set(ny * across + nx);
                    }
                }
            }
        }
        for (int sector = rejoin.nextSetBit(0); sector >= 0; sector = rejoin.nextSetBit(sector + 1)) {
            join(sector);
        }
        firstNode = null;
        return true;
    }

    private void layAll() {
        serial = grid.changeSerial();
        for (int sector = 0; sector < across * down; sector++) {
            lay(sector);
        }
        for (int sector = 0; sector < across * down; sector++) {
            join(sector);
        }
        firstNode = null;
    }

    /** The sector's open cells in the pieces a step joins inside it: in cell order, each flooded outward. */
    private void lay(int sector) {
        int width = grid.getWidth();
        int x0 = (sector % across) * size;
        int y0 = (sector / across) * size;
        int x1 = Math.min(x0 + size, width) - 1;
        int y1 = Math.min(y0 + size, grid.getHeight()) - 1;
        for (int y = y0; y <= y1; y++) {
            Arrays.fill(piece, y * width + x0, y * width + x1 + 1, NO_PIECE);
        }
        var queue = new int[(x1 - x0 + 1) * (y1 - y0 + 1)];
        int count = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (piece[y * width + x] != NO_PIECE || !enterable(x, y)) {
                    continue;
                }
                short label = (short) count++;
                int head = 0;
                int tail = 0;
                piece[y * width + x] = label;
                queue[tail++] = y * width + x;
                while (head < tail) {
                    int cell = queue[head++];
                    int cx = cell % width;
                    int cy = cell / width;
                    for (var step : NEIGHBOURS) {
                        int nx = cx + step[0];
                        int ny = cy + step[1];
                        if (nx < x0 || ny < y0 || nx > x1 || ny > y1 || piece[ny * width + nx] != NO_PIECE
                                || !steps(cx, cy, step)) {
                            continue;
                        }
                        piece[ny * width + nx] = label;
                        queue[tail++] = ny * width + nx;
                    }
                }
            }
        }
        pieces[sector] = count;
    }

    /** The joins from the sector's edge cells to its neighbours' pieces, each once, in order. */
    private void join(int sector) {
        int width = grid.getWidth();
        int x0 = (sector % across) * size;
        int y0 = (sector / across) * size;
        int x1 = Math.min(x0 + size, width) - 1;
        int y1 = Math.min(y0 + size, grid.getHeight()) - 1;
        var found = new long[16];
        int count = 0;
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (y != y0 && y != y1 && x != x0 && x != x1) {
                    continue; // only the edge's cells step out of it
                }
                short mine = piece[y * width + x];
                if (mine == NO_PIECE) {
                    continue;
                }
                for (var step : NEIGHBOURS) {
                    int nx = x + step[0];
                    int ny = y + step[1];
                    if (nx >= x0 && ny >= y0 && nx <= x1 && ny <= y1 || !grid.inBounds(nx, ny)) {
                        continue;
                    }
                    short theirs = piece[ny * width + nx];
                    if (theirs == NO_PIECE || !steps(x, y, step)) {
                        continue;
                    }
                    if (count == found.length) {
                        found = Arrays.copyOf(found, count * 2);
                    }
                    found[count++] = ((long) mine << 42) | ((long) sectorOf(nx, ny) << 16) | theirs;
                }
            }
        }
        Arrays.sort(found, 0, count);
        var list = new int[count * 3];
        int at = 0;
        for (int i = 0; i < count; i++) {
            if (i > 0 && found[i] == found[i - 1]) {
                continue; // each join once
            }
            long join = found[i];
            list[at++] = (int) (join >>> 42);
            list[at++] = (int) ((join >>> 16) & 0x3FFFFFFL);
            list[at++] = (int) (join & 0xFFFF);
        }
        list = Arrays.copyOf(list, at);
        joins[sector] = list;
    }

    /** Whether a step can end on this cell: open, and not a cliff of the relief — as {@link Zones} has it. */
    private boolean enterable(int cx, int cy) {
        return !grid.isBlocked(cx, cy) && !grid.isCliff(cx, cy);
    }

    /** Whether a step joins a cell to its neighbour, as {@link Zones} joins them: the corner not cut. */
    private boolean steps(int cx, int cy, int[] step) {
        int nx = cx + step[0];
        int ny = cy + step[1];
        if (!grid.inBounds(nx, ny) || !enterable(nx, ny) || !grid.canStep(cx, cy, nx, ny)) {
            return false;
        }
        boolean diagonal = step[0] != 0 && step[1] != 0;
        return !diagonal || !grid.isBlocked(cx + step[0], cy) && !grid.isBlocked(cx, cy + step[1]);
    }

    // ---- zones ----

    /** Every piece numbered, and each piece's zone: pieces a join links, the first of them naming it. */
    private void number() {
        if (firstNode != null) {
            return;
        }
        int sectors = across * down;
        firstNode = new int[sectors + 1];
        for (int sector = 0; sector < sectors; sector++) {
            firstNode[sector + 1] = firstNode[sector] + pieces[sector];
        }
        int nodes = firstNode[sectors];
        var parent = new int[nodes];
        for (int node = 0; node < nodes; node++) {
            parent[node] = node;
        }
        for (int sector = 0; sector < sectors; sector++) {
            var list = joins[sector];
            for (int at = 0; at < list.length; at += 3) {
                int a = root(parent, firstNode[sector] + list[at]);
                int b = root(parent, firstNode[list[at + 1]] + list[at + 2]);
                if (a != b) {
                    parent[Math.max(a, b)] = Math.min(a, b);
                }
            }
        }
        sectorOfNode = new int[nodes];
        for (int sector = 0; sector < sectors; sector++) {
            Arrays.fill(sectorOfNode, firstNode[sector], firstNode[sector + 1], sector);
        }
        if (written.length < nodes) {
            costSoFar = new int[nodes];
            cameFrom = new int[nodes];
            written = new int[nodes];
            shut = new int[nodes];
            stamp = 0;
        }
        zoneOfNode = new int[nodes];
        zoneCount = 0;
        for (int node = 0; node < nodes; node++) {
            zoneOfNode[node] = root(parent, node);
            if (zoneOfNode[node] == node) {
                zoneCount++;
            }
        }
    }

    private static int root(int[] parent, int node) {
        while (parent[node] != node) {
            parent[node] = parent[parent[node]];
            node = parent[node];
        }
        return node;
    }

    /** The zone a cell lies in, or -1 for one in none. */
    public int zoneOf(int cx, int cy) {
        if (!grid.inBounds(cx, cy)) {
            return -1;
        }
        short mine = piece[cy * grid.getWidth() + cx];
        if (mine == NO_PIECE) {
            return -1;
        }
        number();
        return zoneOfNode[firstNode[sectorOf(cx, cy)] + mine];
    }

    /** How many zones there are. */
    public int zoneCount() {
        number();
        return zoneCount;
    }

    /**
     * The cell of zone {@code z} nearest the cell {@code (goalX, goalY)} — as {@link Zones#nearestIn} answers it: by
     * straight-line distance, then the shorter walk from {@code (fromX, fromY)} by the search's measure, then the lower
     * cell index — found sector by sector outward from the goal, a sector looked into only where it holds that zone and
     * could hold a cell as near as the nearest yet; -1 where the zone has no cells.
     */
    public int nearestIn(int z, int goalX, int goalY, int fromX, int fromY) {
        number();
        int width = grid.getWidth();
        int centreX = Math.floorDiv(goalX, size);
        int centreY = Math.floorDiv(goalY, size);
        int rings = Math.max(Math.max(Math.abs(centreX), Math.abs(across - 1 - centreX)),
                Math.max(Math.abs(centreY), Math.abs(down - 1 - centreY)));
        int best = -1;
        long nearest = Long.MAX_VALUE;
        int bestWalk = Integer.MAX_VALUE;
        for (int ring = 0; ring <= rings; ring++) {
            if (ring > 0 && best >= 0) {
                long gap = (long) (ring - 1) * size + 1;
                if (gap * gap > nearest) {
                    break; // no cell of this ring or beyond is as near as the nearest found
                }
            }
            for (int sy = centreY - ring; sy <= centreY + ring; sy++) {
                for (int sx = centreX - ring; sx <= centreX + ring; sx++) {
                    if (Math.max(Math.abs(sx - centreX), Math.abs(sy - centreY)) != ring || sx < 0 || sy < 0
                            || sx >= across || sy >= down) {
                        continue;
                    }
                    int sector = sy * across + sx;
                    if (!holds(sector, z) || boxAway(sx, sy, goalX, goalY) > nearest) {
                        continue;
                    }
                    int x0 = sx * size;
                    int y0 = sy * size;
                    int x1 = Math.min(x0 + size, width) - 1;
                    int y1 = Math.min(y0 + size, grid.getHeight()) - 1;
                    for (int y = y0; y <= y1; y++) {
                        for (int x = x0; x <= x1; x++) {
                            short mine = piece[y * width + x];
                            if (mine == NO_PIECE || zoneOfNode[firstNode[sector] + mine] != z) {
                                continue;
                            }
                            long dx = x - goalX;
                            long dy = y - goalY;
                            long away = dx * dx + dy * dy;
                            if (away > nearest) {
                                continue;
                            }
                            int walk = octile(x - fromX, y - fromY);
                            int cell = y * width + x;
                            if (away < nearest || walk < bestWalk || walk == bestWalk && cell < best) {
                                nearest = away;
                                bestWalk = walk;
                                best = cell;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Whether a piece of the sector lies in zone {@code z}. */
    private boolean holds(int sector, int z) {
        for (int node = firstNode[sector]; node < firstNode[sector + 1]; node++) {
            if (zoneOfNode[node] == z) {
                return true;
            }
        }
        return false;
    }

    /** How far, squared, the nearest cell of sector (sx, sy) is from the cell (x, y). */
    private long boxAway(int sx, int sy, int x, int y) {
        int x0 = sx * size;
        int y0 = sy * size;
        int x1 = Math.min(x0 + size, grid.getWidth()) - 1;
        int y1 = Math.min(y0 + size, grid.getHeight()) - 1;
        long dx = x < x0 ? x0 - x : x > x1 ? x - x1 : 0;
        long dy = y < y0 ? y0 - y : y > y1 ? y - y1 : 0;
        return dx * dx + dy * dy;
    }

    /** The search's own measure of how far apart two cells are: 10 a straight step, 14 a diagonal. */
    private static int octile(int dx, int dy) {
        int a = Math.abs(dx);
        int b = Math.abs(dy);
        return 14 * Math.min(a, b) + 10 * (Math.max(a, b) - Math.min(a, b));
    }

    // ---- the way across ----

    /**
     * The pieces a way from cell {@code from} to cell {@code to} goes through, in order — each a sector's piece, see
     * {@link #sectorOfPiece}: a search over the pieces, a step to a neighbouring sector costing a sector's width and a
     * diagonal one half as much again, the nearer by that measure first and then the lower piece. Null where either
     * cell is in no piece, or no way joins them.
     */
    public int[] way(int from, int to) {
        number();
        int width = grid.getWidth();
        int start = nodeOf(from % width, from / width);
        int goal = nodeOf(to % width, to / width);
        if (start < 0 || goal < 0 || zoneOfNode[start] != zoneOfNode[goal]) {
            return null;
        }
        if (stamp == Integer.MAX_VALUE) {
            Arrays.fill(written, 0);
            Arrays.fill(shut, 0);
            stamp = 0;
        }
        stamp++;
        open.clear();
        int goalX = sectorOfNode[goal] % across;
        int goalY = sectorOfNode[goal] / across;
        reach(start, 0, -1);
        open.push(estimate(sectorOfNode[start], goalX, goalY), start);
        while (!open.isEmpty()) {
            int node = open.popValue();
            if (shut[node] == stamp) {
                continue;
            }
            if (node == goal) {
                var back = new ArrayList<Integer>();
                for (int at = goal; at != -1; at = cameFrom[at]) {
                    back.add(at);
                }
                var way = new int[back.size()];
                for (int i = 0; i < way.length; i++) {
                    way[i] = back.get(back.size() - 1 - i);
                }
                return way;
            }
            shut[node] = stamp;
            int sector = sectorOfNode[node];
            int mine = node - firstNode[sector];
            var list = joins[sector];
            for (int at = 0; at < list.length; at += 3) {
                if (list[at] != mine) {
                    continue;
                }
                int next = firstNode[list[at + 1]] + list[at + 2];
                if (shut[next] == stamp) {
                    continue;
                }
                boolean diagonal = list[at + 1] % across != sector % across && list[at + 1] / across != sector / across;
                int cost = costSoFar[node] + (diagonal ? 14 : 10) * size;
                if (written[next] == stamp && cost >= costSoFar[next]) {
                    continue;
                }
                reach(next, cost, node);
                open.push(cost + estimate(list[at + 1], goalX, goalY), next);
            }
        }
        return null;
    }

    private void reach(int node, int cost, int from) {
        written[node] = stamp;
        costSoFar[node] = cost;
        cameFrom[node] = from;
    }

    /** The piece the cell ({@code cx}, {@code cy}) lies in, as {@link #way} names pieces; -1 for none. */
    public int pieceAt(int cx, int cy) {
        number();
        return nodeOf(cx, cy);
    }

    /** The sector a piece, as {@link #way} names it, lies in. */
    public int sectorOfPiece(int piece) {
        number();
        return sectorOfNode[piece];
    }

    private int nodeOf(int cx, int cy) {
        if (!grid.inBounds(cx, cy)) {
            return -1;
        }
        short mine = piece[cy * grid.getWidth() + cx];
        return mine == NO_PIECE ? -1 : firstNode[sectorOf(cx, cy)] + mine;
    }

    /** From a sector to the goal's, by the search's measure in sectors: never more than the way costs. */
    private int estimate(int sector, int goalX, int goalY) {
        return octile(sector % across - goalX, sector / across - goalY) * size;
    }

    /** A heap of whole numbers each with a key, the least key first and then the least number. */
    static final class LongHeap {
        private long[] keys = new long[64];
        private int count;

        boolean isEmpty() {
            return count == 0;
        }

        void clear() {
            count = 0;
        }

        /** Add {@code value} (0 or more) under {@code key} (0 or more). */
        void push(long key, int value) {
            if (count == keys.length) {
                keys = Arrays.copyOf(keys, count * 2);
            }
            long entry = (key << 31) | value;
            int at = count++;
            while (at > 0) {
                int parent = (at - 1) >>> 1;
                if (keys[parent] <= entry) {
                    break;
                }
                keys[at] = keys[parent];
                at = parent;
            }
            keys[at] = entry;
        }

        /** Take the value of the least key off. */
        int popValue() {
            long top = keys[0];
            long last = keys[--count];
            int at = 0;
            while (true) {
                int child = 2 * at + 1;
                if (child >= count) {
                    break;
                }
                if (child + 1 < count && keys[child + 1] < keys[child]) {
                    child++;
                }
                if (keys[child] >= last) {
                    break;
                }
                keys[at] = keys[child];
                at = child;
            }
            if (count > 0) {
                keys[at] = last;
            }
            return (int) (top & 0x7FFFFFFFL);
        }
    }
}
