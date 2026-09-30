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
 *
 * <p><b>Classes of ground.</b> A cell may hold a class the game names ({@link #setGroundClass}) — the reference's
 * cell types, cliff, water, rubble ({@code PathfindCell::setType}) — and a still thing may lay one over its footprint
 * instead of blocking it. A cell of a class is blocked as stone is to whatever may not enter that class, and open to
 * what may: {@link #passage} is the grid as a mover that may enter some classes sees it, sharing every layer with it,
 * for its routes, zones, steps and width to read; the grid itself is what one that may enter none sees. A relief's
 * cliffs hold the class the game names for them ({@link #setCliffClass}), or none: stone to every mover, as they always
 * were.
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
    private final byte[] groundClass;        // the class of ground the map laid on each cell; 0 for plain
    private final byte[] laid;               // the class the still things laid over each cell; 0 for none
    private final byte[] laidScratch;        // the same, being rebuilt with the obstacles
    /** The classes' names, class c at c - 1. */
    private final java.util.List<String> classNames;
    /** The grid a passage looks at, or null for the grid itself. */
    private final PathGrid root;
    /** The classes a passage's mover may enter, class c as bit c; none for the grid itself. */
    private final int surfaces;
    /** The class a relief's cliffs hold; 0 for none, stone to every mover. */
    private int cliffClass;
    private int obstacleVersion;
    /** Bumped whenever anything that decides where can be walked changes: see {@link #getShapeVersion}. */
    private int shapeVersion;
    private final short[] level;             // which floor this cell stands on; 0 everywhere
    private final boolean[] ramp;            // cells that link one level to the next
    private float levelHeight;               // world units per level; 0 = the world is flat
    private HeightMap relief;                // smooth ground over the levels; null = none
    // Laid on the simulation thread, read by a client's pointer on its own: a list either can walk without the other.
    private final java.util.List<Deck> decks;
    /** How high a deck must stand over the ground for the ground under it to stay open: the reference's 10. */
    private float deckClearance = 10f;
    /** For each ground cell, how many open decks stand too low over it for it to be walked; null with no deck. */
    private int[] closedUnder;
    /** The ground movers on its cells — see {@link MoverCells}; made the first time it is asked for. */
    private MoverCells movers;
    /**
     * The grid of the map's own cells this one walks finer — which answers how high the ground stands, the relief, the
     * climb of a ramp and the cliffs, at its own cells — or null for a grid of the map's own cells. See {@link
     * #subdivided}.
     */
    private PathGrid coarse;
    /** How many of its cells a side stand in one of the map's: 1 for a grid of the map's own cells. */
    private int perMapCell = 1;
    /** The scenery standing in the way — see {@link #setSceneryFootprints}. */
    private Circles scenery = Circles.NONE;
    /** The round still things standing in the way, where they close no cell — see {@link #setObstacleCircle}. */
    private Circles stillCircles = Circles.NONE;
    private final java.util.List<SceneryFootprint> circlesScratch = new java.util.ArrayList<>();
    /** How many changes to where can be walked the grid has seen — see {@link #changesSince}. */
    private int changeSerial;
    /** The latest of them, each {serial, minX, minY, maxX, maxY} in cells, oldest first. */
    private final java.util.ArrayDeque<int[]> changes = new java.util.ArrayDeque<>();
    /** The last serial no longer kept: a reader behind it works out everything again. */
    private int forgottenUpTo;

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
        this.groundClass = new byte[width * height];
        this.laid = new byte[width * height];
        this.laidScratch = new byte[width * height];
        this.classNames = new java.util.concurrent.CopyOnWriteArrayList<>();
        this.root = null;
        this.surfaces = 0;
        this.level = new short[width * height];
        this.ramp = new boolean[width * height];
        this.decks = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    /** {@code grid} as a mover that may enter the classes {@code surfaces} sees it: every layer its. */
    private PathGrid(PathGrid grid, int surfaces) {
        this.width = grid.width;
        this.height = grid.height;
        this.cellSize = grid.cellSize;
        this.blocked = grid.blocked;
        this.obstacle = grid.obstacle;
        this.obstacleScratch = grid.obstacleScratch;
        this.groundClass = grid.groundClass;
        this.laid = grid.laid;
        this.laidScratch = grid.laidScratch;
        this.classNames = grid.classNames;
        this.root = grid;
        this.surfaces = surfaces;
        this.cliffClass = grid.cliffClass;
        this.level = grid.level;
        this.ramp = grid.ramp;
        this.levelHeight = grid.levelHeight;
        this.relief = grid.relief;
        this.decks = grid.decks;
        this.deckClearance = grid.deckClearance;
        this.closedUnder = grid.closedUnder;
        this.coarse = grid.coarse;
        this.perMapCell = grid.perMapCell;
    }

    // ---- walked finer than drawn ----

    /**
     * This grid walked at {@code k} cells a side for each of its own: a map drawn a tile to a cell, and walked on a
     * grid fine enough that a body goes between two trees where their trunks leave it room. Each of its cells is
     * blocked, on its level, a ramp and of its class of ground as the cell of this grid it stands in; how high the
     * ground stands — the storeys, the relief, the climb of a ramp — and where the cliffs are is still answered by this
     * grid at its own cells, so a slope and a stair stand exactly where they stood. Decks already laid are laid again.
     * {@code k} of 1 or less is this grid.
     */
    public PathGrid subdivided(int k) {
        if (k <= 1) {
            return this;
        }
        var fine = new PathGrid(width * k, height * k, cellSize / k);
        fine.coarse = this;
        fine.perMapCell = k;
        fine.levelHeight = levelHeight;
        fine.deckClearance = deckClearance;
        fine.classNames.addAll(classNames);
        fine.cliffClass = cliffClass;
        for (int cy = 0; cy < height; cy++) {
            for (int cx = 0; cx < width; cx++) {
                int from = cy * width + cx;
                for (int sy = 0; sy < k; sy++) {
                    for (int sx = 0; sx < k; sx++) {
                        int to = (cy * k + sy) * fine.width + cx * k + sx;
                        fine.blocked[to] = blocked[from];
                        fine.level[to] = level[from];
                        fine.ramp[to] = ramp[from];
                        fine.groundClass[to] = groundClass[from];
                    }
                }
            }
        }
        for (var deck : decks) {
            int floor = fine.addDeck(deck.corner(0), deck.corner(1), deck.corner(2), deck.corner(3));
            fine.setDeckOpen(floor, deck.isOpen());
        }
        return fine;
    }

    /** How many of its cells a side stand in one of the map's: 1 for a grid of the map's own cells. */
    public int cellsPerMapCell() {
        return perMapCell;
    }

    /**
     * How wide a cell of the map this grid walks is, in world units: its own cells', or the map's where it walks the
     * map finer — the size every rule counted in cells is counted in, as the reference counts in its {@code
     * PATHFIND_CELL_SIZE}.
     */
    public float mapCellSize() {
        return cellSize * perMapCell;
    }

    // ---- scenery ----

    /**
     * The scenery standing in the way: circles on the ground, in world units, that are no things of anyone's — see
     * {@code GameLogic.setSceneryFootprints}. They close no cell: a body is kept clear of each by its true distance, in
     * a route's cells and in the line it is pulled straight along ({@link #clearOfCircles}), so a body goes between two
     * trunks the moment it fits between them.
     */
    public void setSceneryFootprints(java.util.List<SceneryFootprint> footprints) {
        root().scenery = Circles.of(footprints == null ? java.util.List.of() : footprints, width, height, cellSize);
        changedEverywhere();
    }

    /**
     * Whether a body of {@code clearance} radius standing at ({@code x}, {@code y}) is clear of every circle standing
     * in the way: the scenery, and the round still things that close no cell ({@link #setObstacleCircle}).
     */
    public boolean clearOfCircles(float x, float y, float clearance) {
        var grid = root();
        return grid.scenery.clear(x, y, clearance) && grid.stillCircles.clear(x, y, clearance);
    }

    /**
     * Whether any piece of scenery whose middle stands within {@code reach} plus the widest footprint of ({@code x},
     * {@code y}) is one {@code test} takes.
     */
    public boolean anySceneryNear(float x, float y, float reach, java.util.function.Predicate<SceneryFootprint> test) {
        return root().scenery.any(x, y, reach, test);
    }

    /** Circles on the ground in world units, by the cell of a grid each one's middle stands in. */
    private static final class Circles {

        static final Circles NONE = new Circles(java.util.List.of(), new SceneryFootprint[0], null, 0f, 1, 1, 1f);

        final java.util.List<SceneryFootprint> list;
        /** The circles cell by cell, and where each cell's start, one past the last cell's end; null for none. */
        private final SceneryFootprint[] byCell;
        private final int[] start;
        private final float widest;
        private final int width;
        private final int height;
        private final float cellSize;

        private Circles(java.util.List<SceneryFootprint> list, SceneryFootprint[] byCell, int[] start, float widest,
                        int width, int height, float cellSize) {
            this.list = list;
            this.byCell = byCell;
            this.start = start;
            this.widest = widest;
            this.width = width;
            this.height = height;
            this.cellSize = cellSize;
        }

        static Circles of(java.util.List<SceneryFootprint> circles, int width, int height, float cellSize) {
            if (circles.isEmpty()) {
                return NONE;
            }
            int cells = width * height;
            int[] start = new int[cells + 1];
            int[] cellOf = new int[circles.size()];
            float widest = 0f;
            for (int i = 0; i < circles.size(); i++) {
                var circle = circles.get(i);
                // One just off the map is kept at its edge, where what it reaches of the map is looked for.
                int cx = Math.clamp((long) Math.floor(circle.x() / cellSize), 0, width - 1);
                int cy = Math.clamp((long) Math.floor(circle.y() / cellSize), 0, height - 1);
                cellOf[i] = cy * width + cx;
                start[cellOf[i] + 1]++;
                widest = Math.max(widest, circle.radius());
            }
            for (int c = 0; c < cells; c++) {
                start[c + 1] += start[c];
            }
            var byCell = new SceneryFootprint[circles.size()];
            int[] next = java.util.Arrays.copyOf(start, cells);
            for (int i = 0; i < circles.size(); i++) {
                byCell[next[cellOf[i]]++] = circles.get(i);
            }
            return new Circles(java.util.List.copyOf(circles), byCell, start, widest, width, height, cellSize);
        }

        /** Whether a body of {@code clearance} radius standing at ({@code x}, {@code y}) is clear of all of them. */
        boolean clear(float x, float y, float clearance) {
            if (start == null) {
                return true;
            }
            float far = clearance + widest;
            int minX = Math.max(0, (int) Math.floor((x - far) / cellSize));
            int maxX = Math.min(width - 1, (int) Math.floor((x + far) / cellSize));
            int minY = Math.max(0, (int) Math.floor((y - far) / cellSize));
            int maxY = Math.min(height - 1, (int) Math.floor((y + far) / cellSize));
            for (int cy = minY; cy <= maxY; cy++) {
                for (int cell = cy * width + minX, last = cy * width + maxX; cell <= last; cell++) {
                    for (int i = start[cell]; i < start[cell + 1]; i++) {
                        var circle = byCell[i];
                        float dx = x - circle.x();
                        float dy = y - circle.y();
                        float room = circle.radius() + clearance;
                        if (dx * dx + dy * dy < room * room) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        /** Whether any whose middle stands within {@code reach} plus the widest of them is one {@code test} takes. */
        boolean any(float x, float y, float reach, java.util.function.Predicate<SceneryFootprint> test) {
            if (start == null) {
                return false;
            }
            float far = reach + widest;
            int minX = Math.max(0, (int) Math.floor((x - far) / cellSize));
            int maxX = Math.min(width - 1, (int) Math.floor((x + far) / cellSize));
            int minY = Math.max(0, (int) Math.floor((y - far) / cellSize));
            int maxY = Math.min(height - 1, (int) Math.floor((y + far) / cellSize));
            for (int cy = minY; cy <= maxY; cy++) {
                for (int cell = cy * width + minX, last = cy * width + maxX; cell <= last; cell++) {
                    for (int i = start[cell]; i < start[cell + 1]; i++) {
                        if (test.test(byCell[i])) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    // ---- classes of ground ----

    /**
     * The grid as a mover that may enter the classes {@code surfaces} sees it ({@link #surfacesOf}): a cell of one of
     * them open to it, one of any other blocked. The grid itself for none. A passage is read, never written: laid as
     * the grid stands when it is asked for, for one search or one step.
     */
    public PathGrid passage(int surfaces) {
        var grid = root == null ? this : root;
        return surfaces == 0 ? grid : new PathGrid(grid, surfaces);
    }

    /** The grid a passage looks at, or this grid. */
    public PathGrid root() {
        return root == null ? this : root;
    }

    /** The classes this grid is seen as entering: none for the grid itself. */
    public int surfaces() {
        return surfaces;
    }

    /** The bits of the classes named — {@code [CLIFF, RUBBLE]} — each given a class of its own the first time. */
    public int surfacesOf(java.util.Collection<String> names) {
        int bits = 0;
        for (var name : names) {
            int c = classOf(name);
            if (c > 0) {
                bits |= 1 << c;
            }
        }
        return bits;
    }

    /** The class of that name, 1 for the first named; 0 for none or for more than thirty. */
    public int classOf(String name) {
        if (name == null || name.isEmpty()) {
            return 0;
        }
        var names = root().classNames;
        synchronized (names) {
            int at = names.indexOf(name);
            if (at < 0) {
                if (names.size() >= 30) {
                    return 0;
                }
                names.add(name);
                at = names.size() - 1;
            }
            return at + 1;
        }
    }

    /** Lay the class of that name on a cell, as the map says; null for plain ground. */
    public void setGroundClass(int cx, int cy, String name) {
        if (!inBounds(cx, cy)) {
            return;
        }
        byte c = (byte) classOf(name);
        if (groundClass[cy * width + cx] != c) {
            groundClass[cy * width + cx] = c;
            root().shapeVersion++;
            changedAround(cx, cy);
        }
    }

    /** The class a relief's cliffs hold, by name; null for none — stone to every mover. */
    public void setCliffClass(String name) {
        root().cliffClass = classOf(name);
        root().shapeVersion++;
        changedEverywhere();
    }

    /**
     * The name of the class of ground on a cell: what a still thing laid over it, else what the map laid, else a
     * cliff's where the relief makes one and the game names theirs; null for plain ground.
     */
    public String groundClassAt(int cx, int cy) {
        if (!inBounds(cx, cy)) {
            return null;
        }
        int index = cy * width + cx;
        int c = laid[index] != 0 ? laid[index] : groundClass[index];
        if (c == 0 && cliffClass != 0 && reliefCliff(cx, cy)) {
            c = cliffClass;
        }
        return c == 0 ? null : root().classNames.get(c - 1);
    }

    /** Lay a class over a cell for the still thing standing on it, instead of blocking it. Between begin and commit. */
    public void setLaid(int cx, int cy, String name) {
        if (inBounds(cx, cy)) {
            laidScratch[cy * width + cx] = (byte) classOf(name);
        }
    }

    /** Whether this grid's mover may enter class {@code c}. */
    private boolean mayEnter(int c) {
        return c != 0 && (surfaces & (1 << c)) != 0;
    }

    public int getWidth() {
        return width;
    }

    /** The ground movers standing on its cells and going to them — see {@link MoverCells}. */
    public MoverCells movers() {
        if (root != null) {
            return root.movers();
        }
        if (movers == null) {
            movers = new MoverCells(width, height);
        }
        return movers;
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
        if (blocked[index] || obstacle[index] || closedUnder != null && closedUnder[index] > 0) {
            return true;
        }
        int c = laid[index] != 0 ? laid[index] : groundClass[index];
        return c != 0 && !mayEnter(c); // ground of a class it may not enter
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
            changedAround(cx, cy);
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
        java.util.Arrays.fill(laidScratch, (byte) 0);
        circlesScratch.clear();
    }

    /** Mark a cell as occupied. Only meaningful between begin and commit. */
    public void setObstacle(int cx, int cy) {
        if (inBounds(cx, cy)) {
            obstacleScratch[cy * width + cx] = true;
        }
    }

    /**
     * A round still thing in the way that closes no cell, kept off by its true distance as scenery is ({@link
     * #clearOfCircles}). Only meaningful between begin and commit.
     */
    public void setObstacleCircle(float x, float y, float radius) {
        circlesScratch.add(new SceneryFootprint(x, y, radius));
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
        boolean sameCells = java.util.Arrays.equals(obstacle, obstacleScratch)
                && java.util.Arrays.equals(laid, laidScratch);
        boolean sameCircles = circlesScratch.equals(stillCircles.list);
        if (sameCells && sameCircles) {
            return;
        }
        if (!sameCells) {
            noteChangedCells();
            System.arraycopy(obstacleScratch, 0, obstacle, 0, obstacle.length);
            System.arraycopy(laidScratch, 0, laid, 0, laid.length);
            shapeVersion++; // what the zones are made of; a circle is none of it
        }
        if (!sameCircles) {
            noteChangedCircles(stillCircles.list, circlesScratch);
            stillCircles = Circles.of(circlesScratch, width, height, cellSize);
        }
        obstacleVersion++;
    }

    /**
     * Increments whenever anything that decides where a body can walk changes — terrain, what stands on it, a
     * level or a ramp, the relief's cliffs. What {@link Zones} are recomputed by.
     */
    public int getShapeVersion() {
        return root == null ? shapeVersion : root.shapeVersion;
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
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IllegalArgumentException("a cell stands on a storey of " + Short.MIN_VALUE + " to "
                    + Short.MAX_VALUE + ", not " + value);
        }
        if (inBounds(cx, cy) && level[cy * width + cx] != value) {
            level[cy * width + cx] = (short) value;
            shapeVersion++;
            changedAround(cx, cy);
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
            changedAround(cx, cy);
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
        if (coarse != null) {
            coarse.setLevelHeight(levelHeight); // which answers how high the ground stands
        }
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
        changedEverywhere();
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
        if (coarse != null) {
            return coarse.groundHeight(new Coord3D((cx + 0.5f) * cellSize, (cy + 0.5f) * cellSize, 0f));
        }
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
        if (coarse != null) {
            return coarse.groundHeight(worldPos);
        }
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
        if (coarse != null) {
            return coarse.reliefHeight(worldPos);
        }
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
    /**
     * Whether the relief makes this cell a cliff, which nothing can step onto — nothing but a mover that may enter the
     * class the game names for cliffs.
     */
    public boolean isCliff(int cx, int cy) {
        return reliefCliff(cx, cy) && !mayEnter(cliffClass);
    }

    /** Whether the relief makes the map's cell under this cell a cliff. */
    private boolean reliefCliff(int cx, int cy) {
        if (coarse != null) {
            return coarse.relief != null
                    && coarse.relief.isCliff(Math.floorDiv(cx, perMapCell), Math.floorDiv(cy, perMapCell));
        }
        return relief != null && relief.isCliff(cx, cy);
    }

    public boolean canStep(int fromX, int fromY, int toX, int toY) {
        if (isBlocked(fromX, fromY) || isBlocked(toX, toY)) {
            return false;
        }
        if (isCliff(toX, toY)) {
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
        return root == null ? obstacleVersion : root.obstacleVersion;
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

    // ---- where it changed ----

    /** How many changes are kept for a reader to catch up with; one further behind works out everything again. */
    private static final int MOST_CHANGES_KEPT = 4096;
    /** Cells changed in one laying past this many squares of the ground are told as one change everywhere. */
    private static final int MOST_SQUARES_TOLD = 256;
    /** The side of a square of the ground the cells a laying changed are told by. */
    private static final int SQUARE = 16;

    /**
     * The serial of the last change to where can be walked — a cell blocked or opened, laid, raised, made a ramp or a
     * class, a circle standing in the way or gone — for {@link #changesSince}.
     */
    public int changeSerial() {
        return root().changeSerial;
    }

    /**
     * Where can be walked changed since change {@code since}: each change's cells, {minX, minY, maxX, maxY}, oldest
     * first — a step between two cells is decided within that box — or null where some of them are no longer kept,
     * and whatever was worked out from the grid is to be worked out again.
     */
    public java.util.List<int[]> changesSince(int since) {
        var grid = root();
        if (since < grid.forgottenUpTo) {
            return null;
        }
        var after = new java.util.ArrayList<int[]>();
        for (var change : grid.changes) {
            if (change[0] > since) {
                after.add(new int[] {change[1], change[2], change[3], change[4]});
            }
        }
        return after;
    }

    private void changed(int minX, int minY, int maxX, int maxY) {
        var grid = root();
        grid.changeSerial++;
        grid.changes.addLast(new int[] {grid.changeSerial, minX, minY, maxX, maxY});
        if (grid.changes.size() > MOST_CHANGES_KEPT) {
            while (grid.changes.size() > MOST_CHANGES_KEPT / 2) {
                grid.forgottenUpTo = grid.changes.removeFirst()[0];
            }
        }
    }

    /** A cell changed: the steps into it, out of it and past its corner along with it. */
    private void changedAround(int cx, int cy) {
        changed(cx - 1, cy - 1, cx + 1, cy + 1);
    }

    private void changedEverywhere() {
        changed(-1, -1, width, height);
    }

    /** The cells the obstacles being committed change, told by the squares of the ground they fall in. */
    private void noteChangedCells() {
        int squaresAcross = (width + SQUARE - 1) / SQUARE;
        var squares = new java.util.BitSet();
        for (int i = 0; i < obstacle.length; i++) {
            if (obstacle[i] != obstacleScratch[i] || laid[i] != laidScratch[i]) {
                squares.set(((i / width) / SQUARE) * squaresAcross + (i % width) / SQUARE);
            }
        }
        if (squares.cardinality() > MOST_SQUARES_TOLD) {
            changedEverywhere();
            return;
        }
        for (int square = squares.nextSetBit(0); square >= 0; square = squares.nextSetBit(square + 1)) {
            int x = (square % squaresAcross) * SQUARE;
            int y = (square / squaresAcross) * SQUARE;
            changed(x - 1, y - 1, x + SQUARE, y + SQUARE);
        }
    }

    /** The circles that came or went, each told by the cells its middle's reach covers. */
    private void noteChangedCircles(java.util.List<SceneryFootprint> was, java.util.List<SceneryFootprint> now) {
        var before = new java.util.HashSet<>(was);
        var after = new java.util.HashSet<>(now);
        var moved = new java.util.ArrayList<SceneryFootprint>();
        was.stream().filter(circle -> !after.contains(circle)).forEach(moved::add);
        now.stream().filter(circle -> !before.contains(circle)).forEach(moved::add);
        if (moved.size() > MOST_SQUARES_TOLD) {
            changedEverywhere();
            return;
        }
        for (var circle : moved) {
            changed((int) Math.floor((circle.x() - circle.radius()) / cellSize) - 1,
                    (int) Math.floor((circle.y() - circle.radius()) / cellSize) - 1,
                    (int) Math.floor((circle.x() + circle.radius()) / cellSize) + 1,
                    (int) Math.floor((circle.y() + circle.radius()) / cellSize) + 1);
        }
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
        changedEverywhere();
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
