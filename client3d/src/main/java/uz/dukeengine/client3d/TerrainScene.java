package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.scene.Node;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import uz.dukeengine.core.map.MapScenery;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The ground and the rocks, and the ability to throw them away and lay down a
 * different world.
 *
 * <p>Split out from the client for one reason: it owns a rule that is easy to
 * break and was broken. Terrain used to be attached straight to the scene root
 * when the window opened, which quietly assumed a world is built once and lasts
 * forever. A game that lays out a new world — a roguelike beginning a new run —
 * broke that assumption, and the client went on drawing walls that no longer
 * existed while the units moved around a map the player could not see.
 *
 * <p>The rule is that rebuilding <b>replaces</b>. Everything goes into a node this
 * class owns, and every rebuild empties it first, so the scene holds one world's
 * worth of geometry no matter how many runs the player dies through. Keeping that
 * in a small class with a node and a grid — and no application, window or GPU —
 * is what lets a test hold it still.
 *
 * <p>Materials arrive through a factory rather than an {@code AssetManager} so the
 * shape of the scene can be checked without one.
 *
 * <p>A game may ask for <b>discovery</b>, in which case the scene can be told,
 * each frame, what to leave out of the picture — see {@link #applyDiscovery}.
 * What it does <em>not</em> do is decide how bright anything is drawn: the fog is
 * a picture of the map ({@link FogMap}) that the terrain's own material samples by
 * world position, so a brightness decided here — per cell, per piece, per anything
 * but per fragment — would be a floor of squares whatever it was computed from.
 * Games that do not ask for discovery pay nothing: no handles are kept and the
 * loop never runs, which matters because an RTS map is many times larger than a
 * dungeon floor.
 *
 * <p>This class owns the node and the order of a rebuild; the ground itself is laid by its collaborators —
 * {@link BlockGround} and {@link PaintedGround} for a map with no kit, {@link KitPlan} and {@link KitPieces} for one
 * with, {@link Scenery}, {@link Chunks} and {@link ChunkStream} for gathering the floor into chunks and building them
 * round the camera, and {@link CoverGrowth} for what grows on them.
 */
final class TerrainScene {

    /** The side of a chunk of a kit's floor, in cells: gathered into one geometry a material, and culled as one. */
    static final int CHUNK_CELLS = Chunks.CELLS;

    /** How many surfaces one chunk of blended ground carries: two vectors of four weights a vertex. */
    static final int BLEND_SLOTS = PaintedGround.BLEND_SLOTS;

    private final Node root;
    private final boolean discovery;

    /**
     * The kit the ground is built from, or {@code null} to lay down blocks.
     *
     * <p>Not final, because a game may lay out its next world from a different one
     * — a dungeon whose floors are meant to look like different places. What a
     * rebuild does is replace, and the kit is part of what it replaces.
     */
    private Tileset tileset;
    private final Tileset defaultTileset;
    private final TileSource tiles;

    /**
     * Whether a kit's laid pieces are gathered into chunks, rather than left a node a cell and a spatial a piece for a
     * test to read where each one went.
     */
    private final boolean chunked;

    private final Cells cells;
    private final Chunks chunks;
    private final Scenery scenery;
    private final KitPieces pieces;
    private final PaintedGround painted;
    private final BlockGround blocks;
    private final CoverGrowth cover;
    private final ChunkStream stream;

    TerrainScene(Node root, Surfaces material) {
        this(root, material, false);
    }

    TerrainScene(Node root, Surfaces material, boolean discovery) {
        this(root, material, discovery, null, null);
    }

    TerrainScene(Node root, Surfaces material, boolean discovery,
            Tileset tileset, TileSource tiles) {
        this(root, material, discovery, tileset, tiles, false);
    }

    /**
     * The same, a kit's floor gathered into chunks where {@code chunked} — see {@link Chunks#gather} — which is how
     * the client draws it; laid a node a cell otherwise, which is how a test reads it.
     */
    TerrainScene(Node root, Surfaces material, boolean discovery,
            Tileset tileset, TileSource tiles, boolean chunked) {
        this.chunked = chunked;
        this.root = root;
        this.discovery = discovery;
        this.defaultTileset = tileset != null && tileset.isUsable() && tiles != null
                ? tileset : null;
        this.tileset = this.defaultTileset;
        this.tiles = tiles;
        this.cells = new Cells(root);
        this.chunks = new Chunks(root);
        this.scenery = new Scenery(tiles, cells);
        this.pieces = new KitPieces(tiles, material, cells);
        this.painted = new PaintedGround(material);
        this.blocks = new BlockGround(material, painted, discovery);
        this.cover = new CoverGrowth(tiles, chunks);
        this.stream = new ChunkStream(chunks, cells, pieces, scenery, blocks, cover);
    }

    private boolean tiled() {
        return tileset != null;
    }

    Node node() {
        return root;
    }

    /** The pieces taken away when the ground is next built — a save's felled trees. */
    void hideScenery(Collection<Integer> hidden) {
        scenery.hide(hidden);
    }

    /**
     * A piece taken away or put back while the ground stands: on a world built round the camera its chunk is let go,
     * to be built again without it or with it; on one built whole, true — the caller builds the ground again.
     */
    boolean showScenery(int index, boolean shown) {
        var piece = scenery.show(index, shown);
        return piece != null && stream.redraw(piece);
    }

    /** Told each material cover makes of its own, so whoever keeps the materials that read the fog keeps these too. */
    void onMadeMaterial(Consumer<Material> told) {
        cover.onMadeMaterial(told);
    }

    /** Grow {@code grows} round the camera from now, what grew before let go. */
    void cover(GroundCover grows) {
        cover.set(grows);
    }

    /**
     * Build a kit's floor a chunk at a time within {@code cells} of where the camera looks ({@link #stream}), and let
     * go of chunks well beyond: a world a thousand cells a side drawn as far as it is seen, where built whole it was
     * gigabytes of pieces. 0, as before, builds the whole map at once. From the next map laid.
     */
    void streamWithin(int cells) {
        stream.within(cells);
    }

    /**
     * Blend a painted map's ground from its paint from the next build on, as the game asks ({@link
     * Visuals#paintBlend}); null for one surface a cell. A map that writes blends of its own is drawn from them
     * either way.
     */
    void blendWith(Visuals.PaintBlend blend) {
        painted.blendWith(blend);
    }

    /** Cut each cell of blended ground two by two from the next build on — see {@link Visuals#paintInQuarters}. */
    void quarterWith(boolean quarters) {
        painted.quarterWith(quarters);
    }

    /** Lay out {@code grid} with the kit the game started with. */
    void rebuild(PathGrid grid) {
        rebuild(grid, defaultTileset);
    }

    /**
     * Lay out {@code grid} from {@code kit}, discarding whatever world was there
     * before — and whatever kit it was built from.
     *
     * <p>A kit that is not usable, or none at all, falls back to the one the game
     * started with. A game that never named a kit still gets blocks.
     */
    void rebuild(PathGrid grid, Tileset kit) {
        rebuild(grid, kit, null);
    }

    /**
     * Lay out {@code grid} from {@code kit}, painted with {@code paint} where the map carries any.
     *
     * <p><b>A map's own kit beats paint; the game's default kit does not.</b> A kit's floors are models with a
     * look of their own — a dungeon is built out of them and its rock, its stairs and the lids over them all
     * belong to the same set — and painting over that would leave a floor drawn twice at the same height. So a
     * map that brings a kit of its own (its look's) is drawn from it, and its paint ignored. But a painted map
     * that names no kit has chosen paint, and the kit the game draws its other maps with does not take its
     * ground from it: a game whose descent is tiled may still paint its open world.
     */
    void rebuild(PathGrid grid, Tileset kit, GroundPaint paint) {
        rebuild(grid, kit, paint, null);
    }

    /** The same, the painted ground lit by its own lights, per corner — see {@link Visuals.GroundLight}. */
    void rebuild(PathGrid grid, Tileset kit, GroundPaint paint, Visuals.GroundLight groundLight) {
        rebuild(grid, kit, paint, groundLight, null, List.of());
    }

    /**
     * The same, each cell of a kit's floor drawn from the kit its look names ({@code cellKits}, null for none): its
     * floor, its lid, its standing things and the faces of its rock. A piece standing between two cells is drawn by the
     * one it belongs to — the rock's side and what grows on it by the rock, a floor and its steps by the floor — and by
     * that kit's own numbers, so looks modelled at different sizes lie side by side.
     */
    void rebuild(PathGrid grid, Tileset kit, GroundPaint paint, Visuals.GroundLight groundLight,
            Tileset[] cellKits) {
        rebuild(grid, kit, paint, groundLight, cellKits, List.of());
    }

    /**
     * The same, the map's scenery laid with its ground ({@link MapScenery}): each piece where it
     * stands, turned, sized and tinted as it says, gathered into the chunks with the ground under it and hidden by the
     * fog as that is.
     */
    void rebuild(PathGrid grid, Tileset kit, GroundPaint paint, Visuals.GroundLight groundLight,
            Tileset[] cellKits, List<? extends MapScenery> scenery) {
        rebuildLooked(grid, kit, paint, groundLight, cellKits == null ? null : cell -> cellKits[cell], scenery);
    }

    /**
     * The same, the kit each cell wears asked of {@code kitOfCell} by the cell's index as its ground is laid — on a
     * world built round the camera, only the cells built, never the whole world's at once.
     */
    void rebuildLooked(PathGrid grid, Tileset kit, GroundPaint paint, Visuals.GroundLight groundLight,
            IntFunction<Tileset> kitOfCell, List<? extends MapScenery> scenery) {
        this.scenery.set(scenery);
        painted.light(groundLight);
        boolean ownKit = kit != null && kit != defaultTileset && kit.isUsable() && tiles != null;
        this.tileset = ownKit ? kit : paint != null ? null : defaultTileset;
        root.detachAllChildren();
        cells.clear();
        chunks.clear();
        stream.reset();
        blocks.clear();
        if (tiled()) {
            int cellCount = grid == null ? 0 : grid.getWidth() * grid.getHeight();
            rebuildFromTiles(grid, new KitPlan(tileset, kitOfCell, cellCount));
            return;
        }
        if (grid != null && paint != null && stream.enabled() && chunked) {
            cells.widen(grid.getWidth());
            stream.overPainted(grid, paint);
            return;
        }

        blocks.lay(grid, paint, root);
        cells.widen(grid == null ? 0 : grid.getWidth());
        if (grid == null) {
            return;
        }
        this.scenery.layAll(grid, false);
        if (chunked) {
            chunks.gather(grid, cells);
        }
    }

    /** The diagonal a cell of the painted ground is drawn cut along — see {@link PaintedGround#drawnDiagonal}. */
    static HeightMap.Diagonal drawnDiagonal(PathGrid grid, GroundPaint paint, int cx, int cy) {
        return PaintedGround.drawnDiagonal(grid, paint, cx, cy);
    }

    /**
     * Lay the floor out of a modular kit.
     *
     * <p>No ground plane, unlike the block version: with a kit there is nothing
     * under an unvisited cell to hide, so an undiscovered part of the map is
     * simply not built into the picture and the background shows through — which
     * is the fog's own colour, and so the same dark the fog itself paints.
     */
    private void rebuildFromTiles(PathGrid grid, KitPlan plan) {
        if (grid == null) {
            return;
        }
        cells.widen(grid.getWidth());
        if (stream.enabled() && chunked) {
            stream.over(grid, plan);
            return;
        }
        cells.open(grid);
        var standing = plan.of(TileLayout.of(grid), grid);
        // How many storeys this map actually has, so the shading can hang off the
        // top of it — see KitPieces.tintFor.
        pieces.countStoreys(standing, grid.getLevelHeight());
        for (var one : standing) {
            pieces.lay(grid, one);
        }
        scenery.layAll(grid, true);
        if (chunked) {
            chunks.gather(grid, cells);
        }
        ChunkGeometry.drape(grid, root, chunks.any());
    }

    /**
     * Build the chunks of a kit's floor within {@link #streamWithin} of the point ({@code x}, {@code z}) on the ground
     * — see {@link ChunkStream#stream}. Nothing where the whole map was built at once.
     */
    void stream(float x, float z) {
        stream.stream(x, z);
    }

    /** Whether chunk ({@code chunkX}, {@code chunkY}) of a map built a chunk at a time is built. */
    boolean isBuilt(int chunkX, int chunkY) {
        return stream.isBuilt(chunkX, chunkY);
    }

    /**
     * Draw the world as the player currently knows it.
     *
     * <p>Cheap enough to run every frame: nothing is created or destroyed and
     * nothing is recoloured, only shown and hidden, because the fog moves with the
     * hero and a rebuild per step would rebuild the floor several hundred times a
     * walk.
     *
     * <p>Hiding is only for what the fog has already blacked out. A rock the
     * player has walked past stays in the picture, drawn through the dark like
     * everything else — a wall that switches off at the edge of the light reads as
     * a hole in the world rather than as somewhere he cannot see.
     */
    void applyDiscovery(Discovery seen) {
        if (!discovery || seen == null) {
            return;
        }
        if (chunks.any()) {
            chunks.applyDiscovery(seen, cells.wide());
        } else if (tiled()) {
            cells.applyDiscovery(seen);
        }
        if (tiled()) {
            return;
        }
        blocks.applyDiscovery(seen, cells.wide());
    }
}
