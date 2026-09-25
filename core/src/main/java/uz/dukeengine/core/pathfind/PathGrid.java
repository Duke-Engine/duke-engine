package uz.dukeengine.core.pathfind;

import uz.dukeengine.core.math.Coord3D;

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
 *
 * <p><b>Height.</b> A cell also stands at a whole-numbered {@link #level}, and
 * two cells at different levels are not neighbours: the step between them is
 * refused exactly as a wall is. What joins them is a {@link #isRamp ramp} — a
 * cell that links one level to the next. Whether that is a staircase, a slope, a
 * ladder or a lift is the game's business; the grid knows only that this cell
 * connects.
 *
 * <p>Levels are integers rather than a height field on purpose. A whole number
 * hashes identically on every machine, cannot drift by a rounding, and answers
 * the only questions a simulation asks of height — may I walk there, are we on
 * the same floor.
 *
 * <p><b>Relief.</b> Over the levels a map may lay a {@link HeightMap}: SAGE's height
 * map, whole-numbered steps at every corner, so a floor can rise and fall across a
 * room. It adds to the height under a mover and never to its level, so walls,
 * stairs and who stands on which floor are the levels' business still; and a cell
 * steep enough to be a cliff is one nothing steps onto.
 *
 * <p>A grid nobody tells about height is flat: every cell is level 0, no cell is
 * a ramp, {@link #getLevelHeight()} is zero, and every rule above collapses back
 * into the one that was there before it — which is the promise made to every
 * game that will never have a second floor.
 *
 * <p><b>Decks.</b> Over the ground a game may lay {@link Deck}s ({@link #addDeck}) — the reference's bridges, a floor
 * of cells of its own at its own height, the ground kept under it: a thing is on the ground (floor 0) or on one deck,
 * gets on and off only at the deck's entries ({@link #enters}), and stands at its floor's height ({@link #heightOn}).
 * The ground under a deck standing lower than {@link #setDeckClearance} above it is closed to the ground's walkers;
 * higher, it stays as it was, and things pass under while others drive over.
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
    /** Bumped whenever anything that decides where can be walked changes: see {@link #getShapeVersion}. */
    private int shapeVersion;
    private final int[] level;               // which floor this cell stands on; 0 everywhere
    private final boolean[] ramp;            // cells that link one level to the next
    private float levelHeight;               // world units per level; 0 = the world is flat
    private HeightMap relief;                // smooth ground over the levels; null = none
    // Laid on the simulation thread, read by a client's pointer on its own: a list either can walk without the other.
    private final java.util.List<Deck> decks = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** How high a deck must stand over the ground for the ground under it to stay open: the reference's 10. */
    private float deckClearance = 10f;
    /** For each ground cell, how many open decks stand too low over it for it to be walked; null with no deck. */
    private int[] closedUnder;

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
        this.level = new int[width * height];
        this.ramp = new boolean[width * height];
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
        return blocked[index] || obstacle[index] || closedUnder != null && closedUnder[index] > 0;
    }

    /** Whether the <em>map</em> forbids this cell, ignoring anything standing on it. */
    public boolean isTerrainBlocked(int cx, int cy) {
        if (!inBounds(cx, cy)) {
            return true;
        }
        return blocked[cy * width + cx];
    }

    public void setBlocked(int cx, int cy, boolean value) {
        if (inBounds(cx, cy) && blocked[cy * width + cx] != value) {
            blocked[cy * width + cx] = value;
            shapeVersion++;
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
        shapeVersion++;
    }

    /**
     * Increments whenever anything that decides where a body can walk changes — terrain, what stands on it, a
     * level or a ramp, the relief's cliffs. What {@link Zones} are recomputed by.
     */
    public int getShapeVersion() {
        return shapeVersion;
    }

    // ---- height ----

    /**
     * Which floor a cell stands on. Zero unless a map says otherwise, and zero
     * for anything off the grid.
     */
    public int level(int cx, int cy) {
        return inBounds(cx, cy) ? level[cy * width + cx] : 0;
    }

    public void setLevel(int cx, int cy, int value) {
        if (inBounds(cx, cy) && level[cy * width + cx] != value) {
            level[cy * width + cx] = value;
            shapeVersion++;
        }
    }

    /**
     * Whether this cell links its level to the one above or below it — a
     * staircase, a slope, a ladder. Without one, a change of level is a wall.
     */
    public boolean isRamp(int cx, int cy) {
        return inBounds(cx, cy) && ramp[cy * width + cx];
    }

    public void setRamp(int cx, int cy, boolean value) {
        if (inBounds(cx, cy)) {
            ramp[cy * width + cx] = value;
            shapeVersion++;
        }
    }

    /**
     * How far apart two levels stand, in world units.
     *
     * <p>Zero — the default — means the grid has levels that block but no height
     * to speak of, which is what a flat world has and what a test usually wants.
     */
    public float getLevelHeight() {
        return levelHeight;
    }

    public void setLevelHeight(float levelHeight) {
        this.levelHeight = levelHeight;
    }

    /**
     * The relief the floors lie on, or null: SAGE's height map, a whole number of steps at every corner, over
     * the levels — hills in a room, a slope down a valley.
     */
    public HeightMap getRelief() {
        return relief;
    }

    public void setRelief(HeightMap relief) {
        shapeVersion++;
        if (relief != null && (relief.columns() != width + 1 || relief.rows() != height + 1)) {
            throw new IllegalArgumentException("a relief over " + width + "x" + height + " cells is "
                    + (width + 1) + "x" + (height + 1) + " corners, not " + relief.columns() + "x" + relief.rows());
        }
        this.relief = relief;
    }

    /**
     * How high a cell's storey stands: its level times the level height, before any relief. What a picture of the
     * map is laid out by, storey by storey, and then bent over the relief ({@link #reliefHeight}).
     */
    public float storeyHeight(int cx, int cy) {
        return level(cx, cy) * levelHeight;
    }

    /** How high the floor of a cell stands, at its centre. Zero on a flat grid, always. */
    public float groundHeight(int cx, int cy) {
        if (relief == null) {
            return level(cx, cy) * levelHeight;
        }
        int middle = HeightMap.SUBCELL / 2;
        return level(cx, cy) * levelHeight + HeightMap.lengthOf(relief.fixedAt(cx, cy, middle, middle), cellSize);
    }

    /**
     * How high the floor stands under a world position.
     *
     * <p>Flat within a cell, except on a ramp, which is the one place the floor
     * is a slope: it rises across the cell from the level it stands on to the
     * level it joins. Without that a body crossing a stair keeps the lower height
     * for the whole cell and then jumps a storey at the far edge — which on
     * screen is a hero sinking into the steps and appearing on top of them.
     *
     * <p>The levels either side are still whole numbers and nothing about
     * walking changes; this is the height <em>between</em> them, which is a
     * question only something standing there asks.
     */
    public float groundHeight(Coord3D worldPos) {
        int cx = toCellX(worldPos);
        int cy = toCellY(worldPos);
        // Nothing is added where there is no relief, not even a zero: -0f + 0f is 0f, and a checksum tells them apart.
        float floor = storeyHeightUnder(worldPos, cx, cy);
        return relief == null ? floor : floor + reliefUnder(worldPos, cx, cy);
    }

    /** The height of the storeys under a world position: flat within a cell, rising across a ramp. */
    private float storeyHeightUnder(Coord3D worldPos, int cx, int cy) {
        var rise = rampDirection(cx, cy);
        if (rise == null) {
            return level(cx, cy) * levelHeight;
        }
        // How far across the cell it is, along the way the ramp climbs: nothing at
        // the near edge, all of it at the far one.
        float alongX = worldPos.x() / cellSize - cx;
        float alongY = worldPos.y() / cellSize - cy;
        float across = rise[0] != 0
                ? (rise[0] > 0 ? alongX : 1f - alongX)
                : (rise[1] > 0 ? alongY : 1f - alongY);
        return level(cx, cy) * levelHeight + levelHeight * Math.clamp(across, 0f, 1f);
    }

    /** The relief's height under a world position, in world units; zero where there is none. */
    public float reliefHeight(Coord3D worldPos) {
        return relief == null ? 0f : reliefUnder(worldPos, toCellX(worldPos), toCellY(worldPos));
    }

    /**
     * The relief's height under a world position, in world units.
     *
     * <p>The position is the one thing here that is not a whole number: it becomes 256ths of a cell once, and
     * from there the height is SAGE's triangles in integers.
     */
    private float reliefUnder(Coord3D worldPos, int cx, int cy) {
        int fx = Math.clamp((int) ((worldPos.x() / cellSize - cx) * HeightMap.SUBCELL), 0, HeightMap.SUBCELL - 1);
        int fy = Math.clamp((int) ((worldPos.y() / cellSize - cy) * HeightMap.SUBCELL), 0, HeightMap.SUBCELL - 1);
        return HeightMap.lengthOf(relief.fixedAt(cx, cy, fx, fy), cellSize);
    }

    /**
     * Which way a ramp climbs, as {@code {dx, dy}}, or {@code null} where the cell
     * is not a ramp or has nothing above it to climb to.
     *
     * <p>One place decides it, because two would drift: the height read under a
     * mover and the flight of steps drawn for the player have to agree about
     * which way the stair faces or the two are a staircase and a body walking up
     * it sideways.
     */
    public int[] rampDirection(int cx, int cy) {
        if (!isRamp(cx, cy)) {
            return null;
        }
        int here = level(cx, cy);
        for (var step : ORTHOGONAL) {
            int nx = cx + step[0];
            int ny = cy + step[1];
            if (!isBlocked(nx, ny) && level(nx, ny) == here + 1 && canStep(cx, cy, nx, ny)) {
                return step;
            }
        }
        return null;
    }

    /** East, west, south, north — a fixed order, so a ramp climbs one way only. */
    private static final int[][] ORTHOGONAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Whether something may move from one cell to a neighbouring one.
     *
     * <p>The rule the whole of height rests on. Both cells have to be open, as
     * ever — and then they have to be on the same floor, or joined: one level
     * apart, straight rather than diagonally, with a ramp at one end of the step.
     *
     * <p>Diagonals are not allowed to change level. A body crossing a corner
     * between two floors is halfway up a wall for the length of that step, and
     * the geometry of a staircase drawn there never looks like anything a person
     * could climb.
     *
     * <p>On a flat grid the level test is {@code 0 == 0} for every pair, so this
     * is the passability check that was here before it.
     */
    /** Whether the relief makes this cell a cliff, which nothing can step onto. */
    public boolean isCliff(int cx, int cy) {
        return relief != null && relief.isCliff(cx, cy);
    }

    public boolean canStep(int fromX, int fromY, int toX, int toY) {
        if (isBlocked(fromX, fromY) || isBlocked(toX, toY)) {
            return false;
        }
        if (relief != null && relief.isCliff(toX, toY)) {
            return false;
        }
        int climb = level(toX, toY) - level(fromX, fromY);
        if (climb == 0) {
            return true;
        }
        if (climb > 1 || climb < -1 || (fromX != toX && fromY != toY)) {
            return false;
        }
        return isRamp(fromX, fromY) || isRamp(toX, toY);
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

    /** The world position at the centre of a cell, on its own floor. */
    public Coord3D cellCenter(int cx, int cy) {
        return new Coord3D((cx + 0.5f) * cellSize, (cy + 0.5f) * cellSize, groundHeight(cx, cy));
    }

    /** Linear index of a cell, used as a deterministic tie-breaker in search. */
    int index(int cx, int cy) {
        return cy * width + cx;
    }

    // ---- decks ----

    /**
     * Lay a deck over the ground: four corners with their heights, round it, the first two one end and the last two
     * the other; its floor comes back, 1 for the first. Classified as {@link Deck} says, and open.
     */
    public int addDeck(Coord3D first, Coord3D second, Coord3D third, Coord3D fourth) {
        var deck = new Deck(decks.size() + 1, new Coord3D[] {first, second, third, fourth}, this);
        decks.add(deck);
        decksChanged();
        return deck.floor();
    }

    /** Open or close a deck — a bridge destroyed shuts all its cells and cuts its entries; repaired, it opens again. */
    public void setDeckOpen(int floor, boolean open) {
        var deck = deck(floor);
        if (deck != null && deck.isOpen() != open) {
            deck.setOpen(open);
            decksChanged();
        }
    }

    /** How high a deck must stand over the ground for the ground under it to stay open to the ground's walkers. */
    public void setDeckClearance(float clearance) {
        this.deckClearance = clearance;
        decksChanged();
    }

    /** The deck on {@code floor}, or null. */
    public Deck deck(int floor) {
        return floor >= 1 && floor <= decks.size() ? decks.get(floor - 1) : null;
    }

    /** Every deck laid, in the order laid: the n-th on floor n. */
    public java.util.List<Deck> decks() {
        return java.util.Collections.unmodifiableList(decks);
    }

    public boolean hasDecks() {
        return !decks.isEmpty();
    }

    /** A deck laid, opened or closed: the ground under the open ones worked out again, and every route planned again. */
    private void decksChanged() {
        var closed = new int[width * height];
        for (var deck : decks) {
            if (!deck.isOpen()) {
                continue;
            }
            for (int cy = deck.minY(); cy <= deck.maxY(); cy++) {
                for (int cx = deck.minX(); cx <= deck.maxX(); cx++) {
                    if (!deck.walkable(cx, cy)) {
                        continue;
                    }
                    float over = deck.heightAt((cx + 0.5f) * cellSize, (cy + 0.5f) * cellSize) - groundHeight(cx, cy);
                    if (over < deckClearance) {
                        closed[cy * width + cx]++;
                    }
                }
            }
        }
        closedUnder = closed;
        shapeVersion++;
        obstacleVersion++; // what a route is checked against: every mover plans its way again
    }

    /**
     * Whether a thing on {@code floor} may step from one cell to a neighbour along it: on the ground, {@link #canStep};
     * on a deck, between two of its walkable cells while it is open.
     */
    public boolean walks(int floor, int fromX, int fromY, int toX, int toY) {
        if (floor == 0) {
            return canStep(fromX, fromY, toX, toY);
        }
        var deck = deck(floor);
        return deck != null && deck.isOpen() && deck.walkable(fromX, fromY) && deck.walkable(toX, toY);
    }

    /**
     * Whether a step from one cell to a neighbour takes a thing from {@code floor} onto {@code onto}: from the ground
     * onto an open deck at one of its entries, or off an open deck's end onto open ground — the only ways from one
     * floor to another. A step that is both, a deck high over open ground at its end, is either, as the route says.
     */
    public boolean enters(int floor, int onto, int fromX, int fromY, int toX, int toY) {
        if (floor == onto || floor != 0 && onto != 0 || !inBounds(fromX, fromY) || !inBounds(toX, toY)) {
            return false;
        }
        var deck = deck(floor == 0 ? onto : floor);
        if (deck == null || !deck.isOpen() || isBlocked(floor == 0 ? fromX : toX, floor == 0 ? fromY : toY)) {
            return false;
        }
        int ground = floor == 0 ? index(fromX, fromY) : index(toX, toY);
        int onDeck = floor == 0 ? index(toX, toY) : index(fromX, fromY);
        for (var entry : deck.entries()) {
            if (entry[0] == ground && entry[1] == onDeck) {
                return true;
            }
        }
        return false;
    }

    /** How high the surface a thing on {@code floor} stands on is under a point: the ground's, or its deck's. */
    public float heightOn(int floor, Coord3D worldPos) {
        var deck = deck(floor);
        return deck == null ? groundHeight(worldPos) : deck.heightAt(worldPos.x(), worldPos.y());
    }

    /**
     * The floor a thing at {@code at} is on: the ground, or the open deck over its point whose height is nearest its
     * height — the reference's {@code getLayerForDestination}. Where a thing is placed, where a click lands.
     */
    public int floorAt(Coord3D at) {
        int floor = 0;
        float nearest = Math.abs(at.z() - groundHeight(at));
        for (var deck : decks) {
            if (!deck.isOpen() || !deck.walkable(toCellX(at), toCellY(at))) {
                continue;
            }
            float away = Math.abs(at.z() - deck.heightAt(at.x(), at.y()));
            if (away < nearest) {
                nearest = away;
                floor = deck.floor();
            }
        }
        return floor;
    }
}
