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
    private final boolean[] blocked;   // terrain: authored by the map, never changes
    private final boolean[] obstacle;  // objects: rebuilt by the simulation

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

    /** Mark a cell as occupied by an object. Cleared wholesale by {@link #clearObstacles}. */
    public void setObstacle(int cx, int cy, boolean value) {
        if (inBounds(cx, cy)) {
            obstacle[cy * width + cx] = value;
        }
    }

    /** Forget every object obstacle, leaving the terrain layer untouched. */
    public void clearObstacles() {
        java.util.Arrays.fill(obstacle, false);
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
