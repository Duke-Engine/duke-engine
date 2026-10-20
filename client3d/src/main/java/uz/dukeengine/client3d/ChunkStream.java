package uz.dukeengine.client3d;

import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import uz.dukeengine.core.map.MapScenery;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The ground built a chunk at a time round where the camera looks, and let go of beyond: a world a thousand cells a
 * side drawn as far as it is seen, where built whole it was gigabytes of pieces. A kit's floor is built a step at a
 * time, a painted map's chunk in one go.
 */
final class ChunkStream {

    /**
     * How long a frame may spend building the ground past the chunks round the camera: walking into new ground builds
     * it a step at a time well before it is seen, and never spends a frame on it.
     */
    static final long BUILD_NANOS_A_FRAME = 3_000_000L;
    /** How many pieces a step of a chunk's building lays. */
    private static final int PIECES_A_STEP = 48;

    private final Chunks chunks;
    private final Cells cells;
    private final KitPieces pieces;
    private final Scenery scenery;
    private final BlockGround blocks;
    private final CoverGrowth cover;

    /** How many cells round the camera the ground is built, a chunk at a time — see {@link #within}. */
    private int reachCells;
    /** The map whose chunks are built as the camera comes near them; null where the whole map was built at once. */
    private PathGrid grid;
    /** The paint of a painted map built a chunk at a time; null where none is. */
    private GroundPaint paint;
    private KitPlan plan;
    /** The chunks built, whether anything stands in them or not. */
    private final BitSet built = new BitSet();
    /** The scenery standing in each chunk of a map built a chunk at a time. */
    private Map<Integer, List<MapScenery>> sceneryByChunk = Map.of();
    /** The chunk being built a step at a time; null when none is. */
    private ChunkBuild inProgress;

    ChunkStream(Chunks chunks, Cells cells, KitPieces pieces, Scenery scenery, BlockGround blocks,
            CoverGrowth cover) {
        this.chunks = chunks;
        this.cells = cells;
        this.pieces = pieces;
        this.scenery = scenery;
        this.blocks = blocks;
        this.cover = cover;
    }

    /**
     * Build the ground a chunk at a time within {@code cells} of where the camera looks ({@link #stream}), and let
     * go of chunks well beyond. 0 builds the whole map at once.
     */
    void within(int cells) {
        this.reachCells = Math.max(0, cells);
    }

    boolean enabled() {
        return reachCells > 0;
    }

    /** Forget the map being built. */
    void reset() {
        grid = null;
        paint = null;
        built.clear();
        inProgress = null;
    }

    /**
     * Get ready to build {@code grid} a chunk at a time: its chunks counted, none built, its scenery sorted by the
     * chunk it stands in, and its storeys counted as a whole build counts them.
     */
    void over(PathGrid grid, KitPlan plan) {
        begin(grid);
        this.plan = plan;
        pieces.countStoreys(grid);
    }

    /** The same for a painted map: its chunks counted, none built. */
    void overPainted(PathGrid grid, GroundPaint paint) {
        begin(grid);
        this.paint = paint;
    }

    private void begin(PathGrid grid) {
        this.grid = grid;
        chunks.over(grid);
        built.clear();
        sceneryByChunk = scenery.byChunk(grid, chunks);
    }

    /**
     * A piece of scenery taken away or put back: on a world built round the camera its chunk is let go, to be built
     * again without it or with it; on one built whole, true — the caller builds the ground again.
     */
    boolean redraw(MapScenery piece) {
        if (grid == null) {
            return true;
        }
        int chunk = Scenery.chunkOf(grid, piece, chunks);
        if (inProgress != null && inProgress.chunk == chunk) {
            inProgress = null;
        }
        release(chunk);
        return false;
    }

    private void release(int chunk) {
        chunks.drop(chunk);
        blocks.forget(chunk);
        built.clear(chunk);
    }

    /**
     * Build the chunks within {@link #within} of the point ({@code x}, {@code z}) on the ground
     * — the nearest first — and let go of those more than a chunk beyond: the ground as far as the camera sees,
     * whatever the size of the world. A step of a chunk at a time, for as long as {@link #BUILD_NANOS_A_FRAME} allows,
     * so walking into new ground builds it before it is seen and never spends a frame on it — but the camera's own
     * chunk and the eight round it at once, whatever that takes, so what is under the camera is never missing: a
     * world's first frame, and a camera that leapt. Nothing where the whole map was built at once.
     */
    void stream(float x, float z) {
        if (grid == null) {
            return;
        }
        float cell = grid.getCellSize();
        int centreX = Math.floorDiv((int) Math.floor(x / cell), Chunks.CELLS);
        int centreY = Math.floorDiv((int) Math.floor(z / cell), Chunks.CELLS);
        int reach = Math.ceilDiv(reachCells, Chunks.CELLS);
        for (int chunk = built.nextSetBit(0); chunk >= 0; chunk = built.nextSetBit(chunk + 1)) {
            if (chunks.far(chunk, centreX, centreY, reach + 1)) {
                release(chunk);
            }
        }
        if (inProgress != null && chunks.far(inProgress.chunk, centreX, centreY, reach + 1)) {
            inProgress = null; // left behind before it was finished
        }
        cover.grow(grid, paint, centreX, centreY);
        long until = System.nanoTime() + BUILD_NANOS_A_FRAME;
        while (true) {
            if (System.nanoTime() >= until && builtRound(centreX, centreY)) {
                return;
            }
            if (inProgress == null) {
                int next = nearestUnbuilt(centreX, centreY, reach);
                if (next < 0) {
                    return;
                }
                if (paint != null) {
                    buildPaintedChunk(next);
                    continue;
                }
                inProgress = new ChunkBuild(next);
            }
            if (inProgress.step()) {
                inProgress = null;
            }
        }
    }

    /** Whether the chunk ({@code centreX}, {@code centreY}) and the eight round it that are on the map are built. */
    private boolean builtRound(int centreX, int centreY) {
        for (int cy = Math.max(0, centreY - 1); cy <= Math.min(chunks.deep() - 1, centreY + 1); cy++) {
            for (int cx = Math.max(0, centreX - 1); cx <= Math.min(chunks.wide() - 1, centreX + 1); cx++) {
                if (!built.get(cy * chunks.wide() + cx)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The nearest chunk on the map within {@code reach} rings of ({@code centreX}, {@code centreY}) not built yet; -1 for none. */
    private int nearestUnbuilt(int centreX, int centreY, int reach) {
        for (int ring = 0; ring <= reach; ring++) {
            for (int cy = centreY - ring; cy <= centreY + ring; cy++) {
                for (int cx = centreX - ring; cx <= centreX + ring; cx++) {
                    if (Math.max(Math.abs(cx - centreX), Math.abs(cy - centreY)) == ring && cx >= 0 && cy >= 0
                            && cx < chunks.wide() && cy < chunks.deep() && !built.get(cy * chunks.wide() + cx)) {
                        return cy * chunks.wide() + cx;
                    }
                }
            }
        }
        return -1;
    }

    /** Whether chunk ({@code chunkX}, {@code chunkY}) of a map built a chunk at a time is built. */
    boolean isBuilt(int chunkX, int chunkY) {
        return grid != null && built.get(chunkY * chunks.wide() + chunkX);
    }

    /**
     * One chunk of a painted map, built whole in one go — a chunk is a few hundred cells, a step of the kit's
     * floor's size — as the whole build builds those cells: its surfaces and overlays, its stone, its scenery;
     * kept about its own corner, so ground far from the origin is drawn as finely as ground at it.
     */
    private void buildPaintedChunk(int chunk) {
        float cell = grid.getCellSize();
        int x0 = chunks.originX(chunk);
        int y0 = chunks.originY(chunk);
        float ox = x0 * cell;
        float oz = y0 * cell;
        var node = new Node("chunk");
        blocks.layChunk(chunk, grid, paint, x0, y0, chunks.lastX(chunk, grid), chunks.lastY(chunk, grid), node, ox, oz);
        for (var piece : sceneryByChunk.getOrDefault(chunk, List.of())) {
            var model = scenery.model(grid, piece, false);
            if (model != null) {
                model.move(-ox, 0f, -oz);
                node.attachChild(model);
            }
        }
        node.setLocalTranslation(ox, 0f, oz);
        chunks.put(chunk, node);
        built.set(chunk);
        chunks.ask(chunk);
    }

    /**
     * One chunk of a kit's floor, built as the whole map would build it, a step at a time: every piece of the plan
     * filed under a cell of it — planned with two cells round it, since a rock's body is turned by the first of its
     * faces the map lays and its faces come from the cells round it — then its scenery, gathered into a geometry a
     * material, laid over the relief, and kept about its own corner, so a world far from its origin is drawn as finely as
     * one at it.
     */
    private final class ChunkBuild {

        private final int chunk;
        private final int x0;
        private final int y0;
        private final int x1;
        private final int y1;
        private final List<MapScenery> sceneryHere;
        private final TreeMap<Integer, Node> byCell = new TreeMap<>();
        private final Node cellsRoot = new Node("building");
        /** The pieces filed under its cells, in the order the plan lays them; null until planned. */
        private List<KitPlan.Standing> standing;
        private int piecesLaid;
        private int sceneryLaid;
        /** Its geometries by what they share, in the order the whole build gathers them; null until gathered. */
        private List<List<Geometry>> looks;
        private List<ChunkGeometry.Gather> keys;
        private int merged;
        private final Node node = new Node("chunk");

        ChunkBuild(int chunk) {
            this.chunk = chunk;
            this.x0 = chunks.originX(chunk);
            this.y0 = chunks.originY(chunk);
            this.x1 = chunks.lastX(chunk, grid);
            this.y1 = chunks.lastY(chunk, grid);
            this.sceneryHere = sceneryByChunk.getOrDefault(chunk, List.of());
        }

        /** One step: planned, a batch of pieces laid, gathered, a material merged, or finished; whether it is built. */
        boolean step() {
            if (standing == null) {
                standing = new ArrayList<>();
                for (var one : plan.of(TileLayout.of(grid, x0 - 2, y0 - 2, x1 + 2, y1 + 2), grid)) {
                    if (one.cellX() >= x0 && one.cellX() <= x1 && one.cellY() >= y0 && one.cellY() <= y1) {
                        standing.add(one);
                    }
                }
                return false;
            }
            if (piecesLaid < standing.size() || sceneryLaid < sceneryHere.size()) {
                cells.layingInto(byCell, cellsRoot, this::layABatch);
                return false;
            }
            if (looks == null) {
                cellsRoot.updateGeometricState();
                var byLook = new LinkedHashMap<ChunkGeometry.Gather, List<Geometry>>();
                for (var cellNode : byCell.values()) {
                    ChunkGeometry.file(cellNode, byLook);
                }
                keys = new ArrayList<>(byLook.keySet());
                looks = new ArrayList<>(byLook.values());
                return false;
            }
            if (merged < looks.size()) {
                node.attachChild(ChunkGeometry.merged(keys.get(merged), looks.get(merged)));
                merged++;
                return false;
            }
            finish();
            return true;
        }

        private void layABatch() {
            int until = Math.min(standing.size(), piecesLaid + PIECES_A_STEP);
            while (piecesLaid < until) {
                pieces.lay(grid, standing.get(piecesLaid++));
            }
            if (piecesLaid == standing.size()) {
                int sceneryUntil = Math.min(sceneryHere.size(), sceneryLaid + PIECES_A_STEP);
                while (sceneryLaid < sceneryUntil) {
                    scenery.lay(grid, sceneryHere.get(sceneryLaid++), true);
                }
            }
        }

        private void finish() {
            built.set(chunk);
            chunks.ask(chunk);
            if (node.getQuantity() == 0) {
                return;
            }
            ChunkGeometry.drape(grid, node, true);
            float cell = grid.getCellSize();
            var corner = new Vector3f(x0 * cell, 0f, y0 * cell);
            ChunkGeometry.moveBack(node, corner);
            node.setLocalTranslation(corner);
            chunks.put(chunk, node);
        }
    }
}
