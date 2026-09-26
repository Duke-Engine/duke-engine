package uz.dukeengine.core;

import uz.dukeengine.core.math.Coord3D;

/**
 * What each player has seen of the map, kept by cells — the reference's partition cells' shroud
 * ({@code PartitionCell::getShroudStatusForPlayer}): never seen until a looker first covers a cell; in sight while one
 * covers it and for a while after — {@code UnlookPersistDuration}, a scout's trail staying open behind him — and seen,
 * not in sight, once that while is out.
 *
 * <p>A looker covers the disc of cells round its own cell as far as its reach in cells, rounded up ({@code
 * doShroudReveal}, {@code DiscreteCircle}): cells whose middles lie within that many cells of its own cell's middle.
 * Who looks for whom is the world's ({@code GameLogic.canSee}'s lookers). Looked at once a frame, as the frame ends, in
 * the order the world keeps its things: the same on every machine.
 */
public final class SightCells {

    /** What a player has of a cell. */
    public enum Sight {
        /** No looker of his has ever covered it. */
        NEVER_SEEN,
        /** Covered once, and by nothing now nor for the while after. */
        SEEN,
        /** Covered now, or within the while after. */
        IN_SIGHT
    }

    private static final int NEVER = Integer.MIN_VALUE;

    private final float cellSize;
    private final int linger;
    private final int width;
    private final int height;
    /** Per player, the last frame each cell was covered, or {@link #NEVER}. */
    private final java.util.Map<Integer, int[]> covered = new java.util.TreeMap<>();
    /** The last frame looked at. */
    private int lookedAt = NEVER;

    /**
     * @param cellSize  how wide a cell is, world units — the reference's {@code PartitionCellSize}, 40
     * @param linger    how many frames a cell stays in sight once nothing covers it — its 150
     * @param mapWidth  how wide the map is, world units
     * @param mapHeight how deep
     */
    public SightCells(float cellSize, int linger, float mapWidth, float mapHeight) {
        this.cellSize = Math.max(1f, cellSize);
        this.linger = Math.max(1, linger);
        this.width = Math.max(1, (int) Math.ceil(mapWidth / this.cellSize));
        this.height = Math.max(1, (int) Math.ceil(mapHeight / this.cellSize));
    }

    public float cellSize() {
        return cellSize;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** A new frame's look begins: {@code frame} is the frame just run. */
    void begin(int frame) {
        lookedAt = frame;
    }

    /** {@code player}'s looker at {@code at} covers the disc of its reach, this frame. */
    void look(int player, Coord3D at, float reach) {
        var cells = covered.computeIfAbsent(player, p -> fresh());
        int cx = (int) Math.floor(at.x() / cellSize);
        int cy = (int) Math.floor(at.y() / cellSize);
        int round = (int) Math.ceil(reach / cellSize);
        long most = (long) round * round;
        for (int dy = -round; dy <= round; dy++) {
            int y = cy + dy;
            if (y < 0 || y >= height) {
                continue;
            }
            for (int dx = -round; dx <= round; dx++) {
                int x = cx + dx;
                if (x >= 0 && x < width && (long) dx * dx + (long) dy * dy <= most) {
                    cells[y * width + x] = lookedAt;
                }
            }
        }
    }

    /** What {@code player} has of the cell under {@code at}; past the map's edges, never seen. */
    public Sight sight(int player, Coord3D at) {
        return sight(player, (int) Math.floor(at.x() / cellSize), (int) Math.floor(at.y() / cellSize));
    }

    /** What {@code player} has of cell ({@code cx}, {@code cy}); past the map's edges, never seen. */
    public Sight sight(int player, int cx, int cy) {
        var cells = covered.get(player);
        if (cells == null || cx < 0 || cy < 0 || cx >= width || cy >= height) {
            return Sight.NEVER_SEEN;
        }
        int last = cells[cy * width + cx];
        if (last == NEVER) {
            return Sight.NEVER_SEEN;
        }
        return lookedAt - last < linger ? Sight.IN_SIGHT : Sight.SEEN;
    }

    /** Whether {@code player} has the cell under {@code at} in sight. */
    public boolean inSight(int player, Coord3D at) {
        return sight(player, at) == Sight.IN_SIGHT;
    }

    /** Whether {@code player} has ever seen the cell under {@code at}. */
    public boolean everSeen(int player, Coord3D at) {
        return sight(player, at) != Sight.NEVER_SEEN;
    }

    /** {@code player}'s cells as they stand, row by row, for a client to draw: a copy it may keep. */
    public View view(int player) {
        var states = new byte[width * height];
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                states[cy * width + cx] = (byte) sight(player, cx, cy).ordinal();
            }
        }
        return new View(cellSize, width, height, states);
    }

    private int[] fresh() {
        var cells = new int[width * height];
        java.util.Arrays.fill(cells, NEVER);
        return cells;
    }

    /**
     * One player's cells at one frame, to keep and read from any thread: each cell's {@link Sight} by its ordinal.
     */
    public record View(float cellSize, int width, int height, byte[] states) {

        /** What the view has of the cell under world point ({@code x}, {@code y}); past the edges, never seen. */
        public Sight at(float x, float y) {
            int cx = (int) Math.floor(x / cellSize);
            int cy = (int) Math.floor(y / cellSize);
            if (cx < 0 || cy < 0 || cx >= width || cy >= height) {
                return Sight.NEVER_SEEN;
            }
            return Sight.values()[states[cy * width + cx]];
        }
    }
}
