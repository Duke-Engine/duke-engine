package uz.duke.core.pathfind;

import uz.duke.core.math.Coord3D;

/**
 * A uniform navigation grid, ported from SAGE's {@code PathfindCell} map.
 *
 * <p>The world's ground plane (the x/y axes; z is height) is divided into square
 * cells of {@link #getCellSize} units — SAGE uses {@code PATHFIND_CELL_SIZE = 10}.
 * Each cell is either passable or blocked. The {@link Pathfinder} searches over
 * these cells; everything off the grid counts as blocked, so units never path off
 * the map.
 *
 * <p>A cell is blocked for either of two independent reasons, kept in separate
 * layers:
 * <ul>
 *   <li><b>terrain</b> — what the map itself says: cliffs, water, walls. Authored
 *       once by {@link MapLoader} or by hand, and never touched afterwards.</li>
 *   <li><b>obstacles</b> — what is standing there right now: buildings and
 *       anything else immobile enough to count as terrain. The simulation rebuilds
 *       this layer as objects appear and die.</li>
 * </ul>
 * Keeping them apart is what lets a building be demolished without punching a
 * hole in the cliff it was built against.
 */
public final class PathGrid {

    /** SAGE's {@code PATHFIND_CELL_SIZE}. */
    public static final float DEFAULT_CELL_SIZE = 10.0f;

    private final int width;
    private final int height;
    private final float cellSize;
    private final boolean[] blocked;         // terrain: authored by the map, never changes
    private final boolean[] obstacle;        // objects: the committed layer everyone reads
    private final boolean[] obstacleScratch; // objects: the layer being rebuilt
    private int obstacleVersion;

    public PathGrid(int width, int height) {
        this(width, height, DEFAULT_CELL_SIZE);
    }

    public PathGrid(int width, int height, float cellSize) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("grid must be positive: " + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
        this.blocked = new boolean[width * height];
        this.obstacle = new boolean[width * height];
        this.obstacleScratch = new boolean[width * height];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float getCellSize() {
        return cellSize;
    }

    public boolean inBounds(int cx, int cy) {
        return cx >= 0 && cy >= 0 && cx < width && cy < height;
    }

    /**
     * Whether a cell cannot be entered, for any reason — terrain or an object
     * standing on it. Out-of-bounds cells are blocked.
     */
    public boolean isBlocked(int cx, int cy) {
        if (!inBounds(cx, cy)) {
            return true;
        }
        int index = cy * width + cx;
        return blocked[index] || obstacle[index];
    }

    /** Whether the <em>map</em> forbids this cell, ignoring anything standing on it. */
    public boolean isTerrainBlocked(int cx, int cy) {
        if (!inBounds(cx, cy)) {
            return true;
        }
        return blocked[cy * width + cx];
    }

    public void setBlocked(int cx, int cy, boolean value) {
        if (inBounds(cx, cy)) {
            blocked[cy * width + cx] = value;
        }
    }

    /**
     * Start rebuilding the obstacle layer from scratch.
     *
     * <p>The layer is derived state — a snapshot of what is standing on the map —
     * so it is rebuilt whole rather than patched. Writes between here and
     * {@link #commitObstacles()} go to a scratch copy, so readers keep seeing a
     * consistent world while the rebuild runs.
     */
    public void beginObstacles() {
        java.util.Arrays.fill(obstacleScratch, false);
    }

    /** Mark a cell as occupied. Only meaningful between begin and commit. */
    public void setObstacle(int cx, int cy) {
        if (inBounds(cx, cy)) {
            obstacleScratch[cy * width + cx] = true;
        }
    }

    /**
     * Publish the rebuilt layer, bumping {@link #getObstacleVersion()} only if it
     * actually differs from what was there before.
     *
     * <p>That comparison is the point: a rebuild is triggered whenever any object
     * is created or dies, which in an RTS is constantly, but the navigable map
     * changes far more rarely. Versioning the <em>result</em> rather than the
     * rebuild keeps everything that watches for changes quiet.
     */
    public void commitObstacles() {
        if (java.util.Arrays.equals(obstacle, obstacleScratch)) {
            return;
        }
        System.arraycopy(obstacleScratch, 0, obstacle, 0, obstacle.length);
        obstacleVersion++;
    }

    /**
     * Increments whenever the obstacle layer changes shape — a building goes up
     * or comes down. Anything holding a path can compare it to know the route it
     * planned may no longer be valid.
     */
    public int getObstacleVersion() {
        return obstacleVersion;
    }

    /** Block the cell containing the given world position. */
    public void blockWorld(Coord3D worldPos) {
        setBlocked(toCellX(worldPos), toCellY(worldPos), true);
    }

    public int toCellX(Coord3D worldPos) {
        return (int) Math.floor(worldPos.x() / cellSize);
    }

    public int toCellY(Coord3D worldPos) {
        return (int) Math.floor(worldPos.y() / cellSize);
    }

    /** The world position at the centre of a cell. */
    public Coord3D cellCenter(int cx, int cy) {
        return new Coord3D((cx + 0.5f) * cellSize, (cy + 0.5f) * cellSize, 0f);
    }

    /** Linear index of a cell, used as a deterministic tie-breaker in search. */
    int index(int cx, int cy) {
        return cy * width + cx;
    }
}
