package uz.dukeengine.client3d;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import uz.dukeengine.core.math.Coord3D;
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
 */
final class TerrainScene {

    private static final ColorRGBA GROUND = new ColorRGBA(0.16f, 0.22f, 0.13f, 1f);
    private static final ColorRGBA ROCK = new ColorRGBA(0.25f, 0.23f, 0.20f, 1f);

    /** Fallback size for a game that never set a map. */
    private static final float DEFAULT_WIDTH = 700f;
    private static final float DEFAULT_HEIGHT = 450f;

    /** Half the height of a stone block, which is drawn about a body tall. */
    private static final float BLOCK_HALF_HEIGHT = 3f;

    private final Node root;
    private final Surfaces material;
    private final boolean discovery;

    /**
     * The kit the ground is built from, or {@code null} to lay down blocks.
     *
     * <p>Not final, because a game may lay out its next world from a different one
     * — a dungeon whose floors are meant to look like different places. What a
     * rebuild does is replace, and the kit is part of what it replaces.
     */
    private Tileset tileset;
    /**
     * The kit each cell wears where the map names looks of its own ({@code cy * width + cx}), null for none — a cell
     * with none, or with a kit that cannot build a floor, wearing the map's.
     */
    private Tileset[] cellKits;
    /** The map's scenery, laid with its ground — see {@link #layScenery}. */
    private java.util.List<? extends uz.dukeengine.core.map.MapScenery> scenery = java.util.List.of();
    /** How scenery is dressed: in the materials its models came with, each kept and made to read the fog. */
    private static final Tileset SCENERY = Tileset.create().ownMaterials(true);
    private final Tileset defaultTileset;
    private final TileSource tiles;

    /**
     * One node per open cell, holding its floor and whatever walls and posts stand
     * on its edges. A node rather than a list of pieces because fog is decided per
     * cell: hiding a cell is one call, not one per piece it happens to own.
     */
    private Node[] cellNodes = new Node[0];

    /**
     * Per-cell handles, kept only when there is discovery to apply. Indexed
     * {@code cy * width + cx}; a cell with no stone in it has no rock.
     */
    private Geometry[] rocks = new Geometry[0];
    private int cellsWide;

    /** The side of a chunk of a kit's floor, in cells: gathered into one geometry a material, and culled as one. */
    static final int CHUNK_CELLS = 16;

    /**
     * Whether a kit's laid pieces are gathered into chunks, rather than left a node a cell and a spatial a piece for a
     * test to read where each one went.
     */
    private final boolean chunked;
    /** The gathered floor, a node a chunk (null where nothing stands), indexed {@code chunkY * chunksWide + chunkX}. */
    private Node[] chunks = new Node[0];
    private int chunksWide;
    /** Every chunk to be asked whether the fog hides it at the next discovery, as for a new map. */
    private boolean everyChunk;
    private final java.util.BitSet dirtyChunks = new java.util.BitSet();
    /** The ground's own lights, worked into the painted ground's corners; null where the things' light it. */
    private Visuals.GroundLight light;
    /** How many cells round the camera a kit's floor is built, a chunk at a time — see {@link #streamWithin}. */
    private int streamCells;
    /** The map whose chunks are built as the camera comes near them; null where the whole map was built at once. */
    private PathGrid streamed;
    /** The chunks built, whether anything stands in them or not. */
    private final java.util.BitSet built = new java.util.BitSet();
    /** The scenery standing in each chunk of a map built a chunk at a time. */
    private java.util.Map<Integer, java.util.List<uz.dukeengine.core.map.MapScenery>> sceneryByChunk =
            java.util.Map.of();
    /** How many storeys the map stands, which the shade of a flat piece hangs off — see {@link #tintFor}. */
    private int tallest;
    /** A chunk's cells while the chunk's pieces are laid, each a node, in order; null when none is. */
    private java.util.TreeMap<Integer, Node> building;
    private Node buildingRoot;
    /**
     * How long a frame may spend building the ground past the chunks round the camera: walking into new ground builds
     * it a step at a time well before it is seen, and never spends a frame on it.
     */
    static final long BUILD_NANOS_A_FRAME = 3_000_000L;
    /** How many pieces a step of a chunk's building lays. */
    private static final int PIECES_A_STEP = 48;
    /** The chunk being built a step at a time; null when none is. */
    private ChunkBuild inProgress;

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
     * The same, a kit's floor gathered into chunks where {@code chunked} — see {@link #gatherIntoChunks} — which is
     * how the client draws it; laid a node a cell otherwise, which is how a test reads it.
     */
    TerrainScene(Node root, Surfaces material, boolean discovery,
            Tileset tileset, TileSource tiles, boolean chunked) {
        this.chunked = chunked;
        this.root = root;
        this.material = material;
        this.discovery = discovery;
        this.defaultTileset = tileset != null && tileset.isUsable() && tiles != null
                ? tileset : null;
        this.tileset = this.defaultTileset;
        this.tiles = tiles;
    }

    private boolean tiled() {
        return tileset != null;
    }

    Node node() {
        return root;
    }

    /**
     * Build a kit's floor a chunk at a time within {@code cells} of where the camera looks ({@link #stream}), and let
     * go of chunks well beyond: a world a thousand cells a side drawn as far as it is seen, where built whole it was
     * gigabytes of pieces. 0, as before, builds the whole map at once. From the next map laid.
     */
    void streamWithin(int cells) {
        this.streamCells = Math.max(0, cells);
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
     * <p><b>A kit beats paint.</b> A kit's floors are models with a look of their own — a dungeon is built
     * out of them and its rock, its stairs and the lids over them all belong to the same set — and painting
     * over that would leave a floor drawn twice at the same height. Paint is for the other kind of map: an
     * outdoor field with no floor models at all, where the ground was a single coloured quad and now is not.
     * A map with both is drawn from the kit, and the paint is ignored rather than argued about.
     */
    void rebuild(PathGrid grid, Tileset kit, GroundPaint paint) {
        rebuild(grid, kit, paint, null);
    }

    /** The same, the painted ground lit by its own lights, per corner — see {@link Visuals.GroundLight}. */
    void rebuild(PathGrid grid, Tileset kit, GroundPaint paint, Visuals.GroundLight groundLight) {
        rebuild(grid, kit, paint, groundLight, null, java.util.List.of());
    }

    /**
     * The same, each cell of a kit's floor drawn from the kit its look names ({@code cellKits}, null for none): its
     * floor, its lid, its standing things and the faces of its rock. A piece standing between two cells is drawn by the
     * one it belongs to — the rock's side and what grows on it by the rock, a floor and its steps by the floor — and by
     * that kit's own numbers, so looks modelled at different sizes lie side by side.
     */
    void rebuild(PathGrid grid, Tileset kit, GroundPaint paint, Visuals.GroundLight groundLight,
            Tileset[] cellKits) {
        rebuild(grid, kit, paint, groundLight, cellKits, java.util.List.of());
    }

    /**
     * The same, the map's scenery laid with its ground ({@link uz.dukeengine.core.map.MapScenery}): each piece where it
     * stands, turned, sized and tinted as it says, gathered into the chunks with the ground under it and hidden by the
     * fog as that is.
     */
    void rebuild(PathGrid grid, Tileset kit, GroundPaint paint, Visuals.GroundLight groundLight,
            Tileset[] cellKits, java.util.List<? extends uz.dukeengine.core.map.MapScenery> scenery) {
        this.scenery = scenery == null ? java.util.List.of() : scenery;
        this.cellKits = cellKits;
        this.light = groundLight;
        this.tileset = kit != null && kit.isUsable() && tiles != null ? kit : defaultTileset;
        root.detachAllChildren();
        cellNodes = new Node[0];
        chunks = new Node[0];
        streamed = null;
        built.clear();
        inProgress = null;
        if (tiled()) {
            rebuildFromTiles(grid);
            return;
        }

        float worldW = grid == null ? DEFAULT_WIDTH : grid.getWidth() * grid.getCellSize();
        float worldH = grid == null ? DEFAULT_HEIGHT : grid.getHeight() * grid.getCellSize();

        if (grid != null && paint != null) {
            paintGround(grid, paint);
        } else {
            var plane = new Geometry("ground", new Quad(worldW, worldH));
            plane.setMaterial(material.of(GROUND, null));
            plane.rotate(-FastMath.HALF_PI, 0, 0);
            plane.setLocalTranslation(0, 0, worldH);
            root.attachChild(plane);
        }

        if (grid == null) {
            rocks = new Geometry[0];
            cellsWide = 0;
            return;
        }
        float cell = grid.getCellSize();
        cellsWide = grid.getWidth();
        rocks = discovery ? new Geometry[grid.getWidth() * grid.getHeight()] : new Geometry[0];
        var stone = material.of(ROCK, null);
        var raised = material.of(GROUND, null);
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                if (!grid.isTerrainBlocked(cx, cy)) {
                    // A room standing above the ground plane needs something under
                    // it, or its floor is a colour on the ground and the units
                    // walking about on it are in mid-air.
                    float ground = grid.storeyHeight(cx, cy);
                    if (ground > 0f) {
                        var plinth = new Geometry("plinth",
                                new Box(cell / 2f, ground / 2f, cell / 2f));
                        plinth.setMaterial(raised);
                        plinth.setLocalTranslation((cx + 0.5f) * cell, ground / 2f,
                                (cy + 0.5f) * cell);
                        root.attachChild(plinth);
                    }
                    continue;
                }
                // Rock stands as tall as the tallest floor beside it, so a raised
                // room is walled in rather than looked over.
                float top = highestFloorAround(grid, cx, cy) + BLOCK_HALF_HEIGHT * 2f;
                var rock = new Geometry("rock", new Box(cell / 2f, top / 2f, cell / 2f));
                rock.setMaterial(stone);
                rock.setLocalTranslation((cx + 0.5f) * cell, top / 2f, (cy + 0.5f) * cell);
                root.attachChild(rock);
                if (discovery) {
                    rocks[cy * cellsWide + cx] = rock;
                }
            }
        }
        layScenery(grid);
        if (chunked) {
            gatherIntoChunks(grid);
        }
    }

    /**
     * A cell's two triangles, by its corners — 0 at (x0, z0), then round by (x1, z0), (x1, z1) and (x0, z1) — cut
     * along {@code diagonal} and each wound so its face looks up.
     */
    static int[] cellTriangles(uz.dukeengine.core.pathfind.HeightMap.Diagonal diagonal) {
        return switch (diagonal) {
            case MAIN -> new int[] {0, 3, 2, 0, 2, 1};
            case ANTI -> new int[] {0, 3, 1, 1, 3, 2};
        };
    }

    /**
     * The ground as the map painted it: one mesh a surface, every cell of it lifted onto the relief.
     *
     * <p><b>One geometry a palette entry, not a cell.</b> A converted Command &amp; Conquer map is sixty
     * thousand cells and a dozen pictures. Gathered, that is a dozen meshes and a dozen materials; a
     * geometry a cell would be sixty thousand of each, and a scene graph that size is not slow to draw so
     * much as slow to walk, which the fog does every frame.
     *
     * <p><b>The texture coordinates run across the world, not across a cell.</b> This is the whole
     * difference between ground and a chequerboard: a picture laid 0..1 inside every cell shows the whole
     * of itself sixty thousand times and its own edges draw the grid. So {@code u} is the world position
     * divided by how much ground one copy covers — which the map says, per entry, because one game's road
     * repeats every two cells and its grass every ten, and a number in the engine could only ever be wrong
     * for one of them.
     *
     * <p>Corners are not shared between cells. Two cells of the same surface meet at the same position with
     * the same texture coordinate, so nothing shows; and not sharing is what lets each cell carry the flat
     * normal of its own slope, which is the look this client's art is drawn for.
     */
    private void paintGround(PathGrid grid, GroundPaint paint) {
        float cell = grid.getCellSize();
        int width = grid.getWidth();
        for (var patch : paint.patches(width, grid.getHeight())) {
            float span = patch.coverage() * cell;
            var positions = com.jme3.util.BufferUtils.createFloatBuffer(patch.cells().length * 12);
            var normals = com.jme3.util.BufferUtils.createFloatBuffer(patch.cells().length * 12);
            var uvs = com.jme3.util.BufferUtils.createFloatBuffer(patch.cells().length * 8);
            var indices = com.jme3.util.BufferUtils.createIntBuffer(patch.cells().length * 6);
            var lights = light == null ? null : com.jme3.util.BufferUtils.createFloatBuffer(patch.cells().length * 16);
            int vertex = 0;
            for (var at : patch.cells()) {
                int cx = at % width;
                int cy = at / width;
                var split = cellTriangles(drawnDiagonal(grid, paint, cx, cy));
                float x0 = cx * cell;
                float x1 = x0 + cell;
                float z0 = cy * cell;
                float z1 = z0 + cell;
                float h00 = groundAt(grid, x0, z0);
                float h10 = groundAt(grid, x1, z0);
                float h11 = groundAt(grid, x1, z1);
                float h01 = groundAt(grid, x0, z1);
                // The slope of this cell, from the two diagonals of its own corners: flat where the ground
                // is flat, and tilted into the sun where it is not.
                var normal = new Vector3f(x1 - x0, h10 - h00, 0f)
                        .cross(new Vector3f(0f, h01 - h00, z1 - z0)).negateLocal().normalizeLocal();
                positions.put(x0).put(h00).put(z0).put(x1).put(h10).put(z0)
                        .put(x1).put(h11).put(z1).put(x0).put(h01).put(z1);
                for (int corner = 0; corner < 4; corner++) {
                    normals.put(normal.x).put(normal.y).put(normal.z);
                }
                if (lights != null) {
                    for (var place : new float[][] {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}}) {
                        var lit = light.at(smoothNormal(grid, place[0], place[1]));
                        lights.put(lit.r).put(lit.g).put(lit.b).put(1f);
                    }
                }
                var laid = paint.cornersOf(cx, cy);
                if (laid != null) {
                    uvs.put(laid); // up a cliff as the map lays it, not stretched straight down
                } else {
                    uvs.put(x0 / span).put(z0 / span).put(x1 / span).put(z0 / span)
                            .put(x1 / span).put(z1 / span).put(x0 / span).put(z1 / span);
                }
                // Cut along the diagonal HeightMap.fixedAt cuts along. It was once cut along the other: on a
                // slope the two triangles then describe a different surface from the one the pathfinder walks,
                // and a unit stood a little in the air or a little in the ground on every tilted cell. A cell the
                // map turns is cut along the other, and a unit on it stands so, as in the reference.
                for (int corner : split) {
                    indices.put(vertex + corner);
                }
                vertex += 4;
            }
            var mesh = new com.jme3.scene.Mesh();
            mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, positions);
            mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Normal, 3, normals);
            mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.TexCoord, 2, uvs);
            mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Index, 3, indices);
            if (lights != null) {
                mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Color, 4, lights);
            }
            mesh.updateBound();
            var ground = new Geometry("painted", mesh);
            ground.setMaterial(material.of(patch.surface().colour(), patch.surface().texture()));
            litByCorners(ground);
            root.attachChild(ground);
        }
        for (var overlay : paint.overlays(width, grid.getHeight())) {
            layOver(grid, paint, overlay);
        }
    }

    /** A painted mesh carrying the ground's own light at its corners told to be drawn by it, where its material can. */
    private void litByCorners(Geometry geometry) {
        var look = geometry.getMaterial();
        if (light != null && look != null && look.getMaterialDef().getMaterialParam("VertexLight") != null) {
            look.setBoolean("VertexLight", true);
        }
    }

    /**
     * How the ground leans at a place, smoothed from the heights a cell either side — the reference's normal for its
     * terrain lights, where a cell's own flat slope draws the facets its lit corners would blur.
     */
    static Vector3f smoothNormal(PathGrid grid, float x, float z) {
        float cell = grid.getCellSize();
        float across = groundAt(grid, x + cell, z) - groundAt(grid, x - cell, z);
        float down = groundAt(grid, x, z + cell) - groundAt(grid, x, z - cell);
        return new Vector3f(-across / (2f * cell), 1f, -down / (2f * cell)).normalizeLocal();
    }

    /**
     * The diagonal a cell of the painted ground is drawn cut along: its relief's, or the other where the map turns it
     * ({@link uz.dukeengine.core.data.Flipped}). Drawing only: heights, slopes and clicks keep the relief's.
     */
    static HeightMap.Diagonal drawnDiagonal(PathGrid grid, GroundPaint paint, int cx, int cy) {
        var relief = grid.getRelief() == null ? HeightMap.Diagonal.MAIN : grid.getRelief().diagonal();
        if (paint == null || !paint.flipped(cx, cy)) {
            return relief;
        }
        return relief == HeightMap.Diagonal.MAIN ? HeightMap.Diagonal.ANTI : HeightMap.Diagonal.MAIN;
    }

    /**
     * A picture laid over the ground where kinds of it meet, faded in by the shape each cell names.
     *
     * <p>Four triangles a cell, meeting at its middle, because that is what makes every one of the sixteen
     * fades exact — see {@link FadeShape}. It costs nothing in the fit: the middle lies on the diagonal the
     * ground under it is cut along, at the height the ground has there, so all four triangles lie in the two
     * the ground is made of and the overlay cannot sink into it or float above it.
     *
     * <p>Drawn after the ground and blended over it. Within a layer the order never matters — a cell has one
     * overlay a layer, so no two of a layer's meshes cover the same place. Between layers it is everything:
     * where three kinds of ground meet, a cell carries one picture a layer on exactly the same triangles, and
     * the later layer has to land on top. So each mesh is marked with its layer ({@link OverlayOrder#LAYER})
     * and the transparent bucket is drawn in layer order by {@link OverlayOrder} — a fixed order, not the
     * camera's distance to a mesh that spans the map, and not a depth test two identical surfaces would
     * fight over.
     *
     * <p>Laid across the world at its own coverage, like the ground's pictures, so an overlay lines up with
     * the same picture drawn as ground next door rather than starting again in every cell.
     */
    private void layOver(PathGrid grid, GroundPaint paint, GroundPaint.OverlayPatch overlay) {
        float cell = grid.getCellSize();
        int width = grid.getWidth();
        float span = overlay.coverage() * cell;
        int count = overlay.cells().length;
        var positions = com.jme3.util.BufferUtils.createFloatBuffer(count * 5 * 3);
        var normals = com.jme3.util.BufferUtils.createFloatBuffer(count * 5 * 3);
        var colours = com.jme3.util.BufferUtils.createFloatBuffer(count * 5 * 4);
        var uvs = com.jme3.util.BufferUtils.createFloatBuffer(count * 5 * 2);
        var indices = com.jme3.util.BufferUtils.createIntBuffer(count * 12);
        int vertex = 0;
        for (int i = 0; i < count; i++) {
            int at = overlay.cells()[i];
            float x0 = at % width * cell;
            float z0 = at / width * cell;
            float x1 = x0 + cell;
            float z1 = z0 + cell;
            float xm = x0 + cell / 2f;
            float zm = z0 + cell / 2f;
            float h00 = groundAt(grid, x0, z0);
            float h10 = groundAt(grid, x1, z0);
            float h11 = groundAt(grid, x1, z1);
            float h01 = groundAt(grid, x0, z1);
            var normal = new Vector3f(x1 - x0, h10 - h00, 0f)
                    .cross(new Vector3f(0f, h01 - h00, z1 - z0)).negateLocal().normalizeLocal();
            // The middle on the diagonal the ground under it is drawn cut along, so it lies on that ground.
            float hm = drawnDiagonal(grid, paint, at % width, at / width) == HeightMap.Diagonal.MAIN
                    ? (h00 + h11) / 2f : (h10 + h01) / 2f;
            positions.put(x0).put(h00).put(z0).put(x1).put(h10).put(z0).put(x1).put(h11).put(z1)
                    .put(x0).put(h01).put(z1).put(xm).put(hm).put(zm);
            uvs.put(x0 / span).put(z0 / span).put(x1 / span).put(z0 / span).put(x1 / span).put(z1 / span)
                    .put(x0 / span).put(z1 / span).put(xm / span).put(zm / span);
            // White, so the picture is what is seen; the fade is the alpha alone — and the ground's own light, where
            // it has one, at each corner and the middle.
            float[][] places = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}, {xm, zm}};
            var strengths = FadeShape.strengths(overlay.shapes()[i]);
            for (int corner = 0; corner < strengths.length; corner++) {
                var lit = light == null ? com.jme3.math.ColorRGBA.White
                        : light.at(smoothNormal(grid, places[corner][0], places[corner][1]));
                colours.put(lit.r).put(lit.g).put(lit.b).put(strengths[corner]);
                normals.put(normal.x).put(normal.y).put(normal.z);
            }
            // Corner, middle, next corner, round the cell: each wound so its face looks up.
            for (int corner = 0; corner < 4; corner++) {
                indices.put(vertex + corner).put(vertex + 4).put(vertex + (corner + 1) % 4);
            }
            vertex += 5;
        }
        var mesh = new com.jme3.scene.Mesh();
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Position, 3, positions);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Normal, 3, normals);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Color, 4, colours);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.TexCoord, 2, uvs);
        mesh.setBuffer(com.jme3.scene.VertexBuffer.Type.Index, 3, indices);
        mesh.updateBound();
        var laid = new Geometry("overlay", mesh);
        laid.setMaterial(material.overlay(overlay.surface().colour(), overlay.surface().texture()));
        litByCorners(laid);
        laid.setQueueBucket(com.jme3.renderer.queue.RenderQueue.Bucket.Transparent);
        laid.setUserData(OverlayOrder.LAYER, overlay.layer());
        root.attachChild(laid);
    }

    /**
     * How high the painted ground stands at a place: the relief, and zero where the map has none.
     *
     * <p>The same number the pathfinder reads, asked the same way, so the ground a unit walks on and the
     * ground drawn under it cannot drift apart. Two cells sharing a corner ask about the same world
     * position and get the same answer, so the meshes meet without a crack even though they share no vertex.
     *
     * <p>Storeys are not in it. A map built in storeys is a map built from a kit — see {@link #rebuild} —
     * and a painted map is the flat outdoor sort, where a storey is a thing it does not have.
     */
    private static float groundAt(PathGrid grid, float x, float z) {
        return grid.reliefHeight(new Coord3D(x, z, 0f));
    }

    /** The tallest floor touching a cell, so rock and lids rise with the rooms. */
    private static float highestFloorAround(PathGrid grid, int cx, int cy) {
        float highest = 0f;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.inBounds(cx + dx, cy + dy) && !grid.isTerrainBlocked(cx + dx, cy + dy)) {
                    highest = Math.max(highest, grid.storeyHeight(cx + dx, cy + dy));
                }
            }
        }
        return highest;
    }

    /**
     * Lay the floor out of a modular kit.
     *
     * <p>No ground plane, unlike the block version: with a kit there is nothing
     * under an unvisited cell to hide, so an undiscovered part of the map is
     * simply not built into the picture and the background shows through — which
     * is the fog's own colour, and so the same dark the fog itself paints.
     */
    private void rebuildFromTiles(PathGrid grid) {
        if (grid == null) {
            return;
        }
        cellsWide = grid.getWidth();
        float cell = grid.getCellSize();
        float storey = grid.getLevelHeight();
        if (streamCells > 0 && chunked) {
            streamOver(grid);
            return;
        }
        cellNodes = new Node[grid.getWidth() * grid.getHeight()];
        var plan = plan(TileLayout.of(grid), grid);
        // How many storeys this map actually has, so the shading can hang off the
        // top of it — see storeyShade.
        tallest = 0;
        if (storey > 0f) {
            for (var standing : plan) {
                tallest = Math.max(tallest, Math.round(standing.ground() / storey));
            }
        }
        for (var standing : plan) {
            layPiece(grid, standing, cell, storey);
        }
        layScenery(grid);
        if (chunked) {
            gatherIntoChunks(grid);
        }
        drape(grid);
    }

    /** One piece of the plan laid in its cell: a flight of steps, a rock's face, or the kit's pieces for it. */
    private void layPiece(PathGrid grid, Standing standing, float cell, float storey) {
        var kit = standing.kit();
        if (standing.piece() == TileLayout.Piece.STAIR) {
            addStair(grid, standing, cell);
            return;
        }
        String asset = assetFor(kit, standing.piece());
        if (asset == null) {
            return; // a kit without corner posts is a kit with square notches
        }
        if (standing.piece() == TileLayout.Piece.ROCK_FACE) {
            addRockFace(asset, standing, storey);
            return;
        }
        float floorScale = cell / kit.getTileSize();
        // Walls may have been modelled on a different module from the floors, and
        // then they have their own scale — see Tileset.wallTileSize.
        float wallScale = cell / kit.getWallTileSize();
        int clump = standing.upright() ? kit.getWallClump() : 1;
        for (int copy = 0; copy < clump; copy++) {
            addKitPiece(assetFor(standing, copy), standing, copy, clump,
                    standing.upright() ? wallScale : floorScale, wallScale, cell,
                    tintFor(kit, standing.piece(), standing.ground(), storey, tallest));
        }
    }

    /**
     * Get ready to build {@code grid} a chunk at a time: its chunks counted, none built, its scenery sorted by the
     * chunk it stands in, and its storeys counted as a whole build counts them — the highest floor's level, every
     * piece's ground being a floor's or no higher than the floors round it.
     */
    private void streamOver(PathGrid grid) {
        streamed = grid;
        chunksWide = Math.ceilDiv(grid.getWidth(), CHUNK_CELLS);
        chunks = new Node[chunksWide * Math.ceilDiv(grid.getHeight(), CHUNK_CELLS)];
        built.clear();
        everyChunk = true;
        tallest = 0;
        if (grid.getLevelHeight() > 0f) {
            for (int cy = 0; cy < grid.getHeight(); cy++) {
                for (int cx = 0; cx < grid.getWidth(); cx++) {
                    if (!grid.isTerrainBlocked(cx, cy)) {
                        tallest = Math.max(tallest, grid.level(cx, cy));
                    }
                }
            }
        }
        var byChunk = new java.util.HashMap<Integer, java.util.List<uz.dukeengine.core.map.MapScenery>>();
        for (var piece : scenery) {
            int cx = Math.clamp((int) Math.floor(piece.x()), 0, grid.getWidth() - 1);
            int cy = Math.clamp((int) Math.floor(piece.y()), 0, grid.getHeight() - 1);
            byChunk.computeIfAbsent(cy / CHUNK_CELLS * chunksWide + cx / CHUNK_CELLS,
                    chunk -> new java.util.ArrayList<>()).add(piece);
        }
        sceneryByChunk = byChunk;
    }

    /**
     * Build the chunks of a kit's floor within {@link #streamWithin} of the point ({@code x}, {@code z}) on the ground
     * — the nearest first — and let go of those more than a chunk beyond: the ground as far as the camera sees,
     * whatever the size of the world. A step of a chunk at a time, for as long as {@link #BUILD_NANOS_A_FRAME} allows,
     * so walking into new ground builds it before it is seen and never spends a frame on it — but the camera's own
     * chunk and the eight round it at once, whatever that takes, so what is under the camera is never missing: a
     * world's first frame, and a camera that leapt. Nothing where the whole map was built at once.
     */
    void stream(float x, float z) {
        if (streamed == null) {
            return;
        }
        float cell = streamed.getCellSize();
        int centreX = Math.floorDiv((int) Math.floor(x / cell), CHUNK_CELLS);
        int centreY = Math.floorDiv((int) Math.floor(z / cell), CHUNK_CELLS);
        int reach = Math.ceilDiv(streamCells, CHUNK_CELLS);
        for (int chunk = built.nextSetBit(0); chunk >= 0; chunk = built.nextSetBit(chunk + 1)) {
            if (far(chunk, centreX, centreY, reach + 1)) {
                if (chunks[chunk] != null) {
                    chunks[chunk].removeFromParent();
                    chunks[chunk] = null;
                }
                built.clear(chunk);
            }
        }
        if (inProgress != null && far(inProgress.chunk, centreX, centreY, reach + 1)) {
            inProgress = null; // left behind before it was finished
        }
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
                inProgress = new ChunkBuild(next);
            }
            if (inProgress.step()) {
                inProgress = null;
            }
        }
    }

    /** Whether chunk {@code chunk} is more than {@code rings} chunks from chunk ({@code centreX}, {@code centreY}). */
    private boolean far(int chunk, int centreX, int centreY, int rings) {
        return Math.max(Math.abs(chunk % chunksWide - centreX), Math.abs(chunk / chunksWide - centreY)) > rings;
    }

    /** Whether the chunk ({@code centreX}, {@code centreY}) and the eight round it that are on the map are built. */
    private boolean builtRound(int centreX, int centreY) {
        int chunksDeep = chunks.length / chunksWide;
        for (int cy = Math.max(0, centreY - 1); cy <= Math.min(chunksDeep - 1, centreY + 1); cy++) {
            for (int cx = Math.max(0, centreX - 1); cx <= Math.min(chunksWide - 1, centreX + 1); cx++) {
                if (!built.get(cy * chunksWide + cx)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The nearest chunk on the map within {@code reach} rings of ({@code centreX}, {@code centreY}) not built yet; -1 for none. */
    private int nearestUnbuilt(int centreX, int centreY, int reach) {
        int chunksDeep = chunks.length / chunksWide;
        for (int ring = 0; ring <= reach; ring++) {
            for (int cy = centreY - ring; cy <= centreY + ring; cy++) {
                for (int cx = centreX - ring; cx <= centreX + ring; cx++) {
                    if (Math.max(Math.abs(cx - centreX), Math.abs(cy - centreY)) == ring && cx >= 0 && cy >= 0
                            && cx < chunksWide && cy < chunksDeep && !built.get(cy * chunksWide + cx)) {
                        return cy * chunksWide + cx;
                    }
                }
            }
        }
        return -1;
    }

    /** Whether chunk ({@code chunkX}, {@code chunkY}) of a map built a chunk at a time is built. */
    boolean isBuilt(int chunkX, int chunkY) {
        return streamed != null && built.get(chunkY * chunksWide + chunkX);
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
        private final java.util.List<uz.dukeengine.core.map.MapScenery> scenery;
        private final java.util.TreeMap<Integer, Node> cells = new java.util.TreeMap<>();
        private final Node cellsRoot = new Node("building");
        /** The pieces filed under its cells, in the order the plan lays them; null until planned. */
        private java.util.List<Standing> pieces;
        private int piecesLaid;
        private int sceneryLaid;
        /** Its geometries by what they share, in the order the whole build gathers them; null until gathered. */
        private java.util.List<java.util.List<Geometry>> looks;
        private java.util.List<Gather> keys;
        private int merged;
        private final Node node = new Node("chunk");

        ChunkBuild(int chunk) {
            this.chunk = chunk;
            this.x0 = chunk % chunksWide * CHUNK_CELLS;
            this.y0 = chunk / chunksWide * CHUNK_CELLS;
            this.x1 = Math.min(x0 + CHUNK_CELLS, streamed.getWidth()) - 1;
            this.y1 = Math.min(y0 + CHUNK_CELLS, streamed.getHeight()) - 1;
            this.scenery = sceneryByChunk.getOrDefault(chunk, java.util.List.of());
        }

        /** One step: planned, a batch of pieces laid, gathered, a material merged, or finished; whether it is built. */
        boolean step() {
            var grid = streamed;
            if (pieces == null) {
                pieces = new java.util.ArrayList<>();
                for (var standing : plan(TileLayout.of(grid, x0 - 2, y0 - 2, x1 + 2, y1 + 2), grid)) {
                    if (standing.cellX() >= x0 && standing.cellX() <= x1 && standing.cellY() >= y0
                            && standing.cellY() <= y1) {
                        pieces.add(standing);
                    }
                }
                return false;
            }
            if (piecesLaid < pieces.size() || sceneryLaid < scenery.size()) {
                layABatch(grid);
                return false;
            }
            if (looks == null) {
                cellsRoot.updateGeometricState();
                var byLook = gatheredByLook(cells.values());
                keys = new java.util.ArrayList<>(byLook.keySet());
                looks = new java.util.ArrayList<>(byLook.values());
                return false;
            }
            if (merged < looks.size()) {
                node.attachChild(merged(keys.get(merged), looks.get(merged)));
                merged++;
                return false;
            }
            finish(grid);
            return true;
        }

        private void layABatch(PathGrid grid) {
            building = cells;
            buildingRoot = cellsRoot;
            try {
                float cell = grid.getCellSize();
                float storey = grid.getLevelHeight();
                int until = Math.min(pieces.size(), piecesLaid + PIECES_A_STEP);
                while (piecesLaid < until) {
                    layPiece(grid, pieces.get(piecesLaid++), cell, storey);
                }
                if (piecesLaid == pieces.size()) {
                    int sceneryUntil = Math.min(scenery.size(), sceneryLaid + PIECES_A_STEP);
                    while (sceneryLaid < sceneryUntil) {
                        layScenery(grid, scenery.get(sceneryLaid++));
                    }
                }
            } finally {
                building = null;
                buildingRoot = null;
            }
        }

        private void finish(PathGrid grid) {
            built.set(chunk);
            dirtyChunks.set(chunk);
            if (node.getQuantity() == 0) {
                return;
            }
            if (grid.getRelief() != null) {
                drape(grid, node, true);
            }
            float cell = grid.getCellSize();
            var corner = new Vector3f(x0 * cell, 0f, y0 * cell);
            for (var geometry : node.descendantMatches(Geometry.class)) {
                var positions = geometry.getMesh().getFloatBuffer(com.jme3.scene.VertexBuffer.Type.Position);
                for (int i = 0; i + 2 < positions.limit(); i += 3) {
                    positions.put(i, positions.get(i) - corner.x).put(i + 2, positions.get(i + 2) - corner.z);
                }
                geometry.getMesh().getBuffer(com.jme3.scene.VertexBuffer.Type.Position).setUpdateNeeded();
                geometry.getMesh().updateBound();
            }
            node.setLocalTranslation(corner);
            root.attachChild(node);
            chunks[chunk] = node;
        }
    }

    /**
     * The map's scenery, each piece in the cell it stands in: at the cell's floor on a kit's map, where the relief is
     * then laid over it as over the floor; on the relief itself on a painted one, upright.
     */
    private void layScenery(PathGrid grid) {
        if (scenery.isEmpty() || tiles == null) {
            return;
        }
        if (cellNodes.length == 0) {
            cellNodes = new Node[grid.getWidth() * grid.getHeight()];
            cellsWide = grid.getWidth();
        }
        for (var piece : scenery) {
            layScenery(grid, piece);
        }
    }

    /** One piece of scenery in the cell it stands in. */
    private void layScenery(PathGrid grid, uz.dukeengine.core.map.MapScenery piece) {
        float cell = grid.getCellSize();
        var model = tiles.piece(SCENERY, piece.model(), piece.tint());
        if (model == null) {
            return;
        }
        int cx = Math.clamp((int) Math.floor(piece.x()), 0, grid.getWidth() - 1);
        int cy = Math.clamp((int) Math.floor(piece.y()), 0, grid.getHeight() - 1);
        float x = piece.x() * cell;
        float z = piece.y() * cell;
        model.setLocalScale(piece.scale());
        model.setLocalRotation(new com.jme3.math.Quaternion()
                .fromAngleAxis(FastMath.DEG_TO_RAD * piece.facing(), Vector3f.UNIT_Y));
        model.setLocalTranslation(x, tiled() ? grid.storeyHeight(cx, cy) : groundAt(grid, x, z), z);
        cellNode(cy * cellsWide + cx).attachChild(model);
    }

    /**
     * Every laid piece gathered into the chunk its cell is in: one geometry for each material in each chunk of {@link
     * #CHUNK_CELLS} cells a side, its vertices where the pieces stood — the reference's static terrain drawn as a few
     * hundred things rather than a geometry a piece, tens of thousands on a large floor, each walked by the scene every
     * frame. What is gathered keeps its material, its bucket and its shadows, and pieces of different vertex layouts
     * are gathered apart so none borrows another's.
     */
    private void gatherIntoChunks(PathGrid grid) {
        chunksWide = Math.ceilDiv(grid.getWidth(), CHUNK_CELLS);
        int chunksDeep = Math.ceilDiv(grid.getHeight(), CHUNK_CELLS);
        root.updateGeometricState();
        var gathered = new java.util.ArrayList<java.util.Map<Gather, java.util.List<Geometry>>>();
        for (int chunk = 0; chunk < chunksWide * chunksDeep; chunk++) {
            gathered.add(new java.util.LinkedHashMap<>());
        }
        for (int index = 0; index < cellNodes.length; index++) {
            if (cellNodes[index] == null) {
                continue;
            }
            int chunk = index / cellsWide / CHUNK_CELLS * chunksWide + index % cellsWide / CHUNK_CELLS;
            for (var geometry : cellNodes[index].descendantMatches(Geometry.class)) {
                gathered.get(chunk).computeIfAbsent(Gather.of(geometry), key -> new java.util.ArrayList<>())
                        .add(geometry);
            }
        }
        for (var node : cellNodes) {
            if (node != null) {
                node.removeFromParent(); // a painted map's ground stays where it is
            }
        }
        cellNodes = new Node[0];
        chunks = new Node[gathered.size()];
        for (int chunk = 0; chunk < chunks.length; chunk++) {
            if (gathered.get(chunk).isEmpty()) {
                continue;
            }
            var node = merged(gathered.get(chunk));
            root.attachChild(node);
            chunks[chunk] = node;
        }
        everyChunk = true;
    }

    /** The pieces under {@code cells} gathered into a chunk: a geometry for each material, in the order met. */
    private static Node gathered(java.util.Collection<Node> cells) {
        return merged(gatheredByLook(cells));
    }

    /** The geometries under {@code cells} by what they must share to be gathered, in the order each is first met. */
    private static java.util.Map<Gather, java.util.List<Geometry>> gatheredByLook(java.util.Collection<Node> cells) {
        var byLook = new java.util.LinkedHashMap<Gather, java.util.List<Geometry>>();
        for (var cell : cells) {
            for (var geometry : cell.descendantMatches(Geometry.class)) {
                byLook.computeIfAbsent(Gather.of(geometry), key -> new java.util.ArrayList<>()).add(geometry);
            }
        }
        return byLook;
    }

    private static Node merged(java.util.Map<Gather, java.util.List<Geometry>> byLook) {
        var node = new Node("chunk");
        byLook.forEach((key, geometries) -> node.attachChild(merged(key, geometries)));
        return node;
    }

    /** The geometries sharing {@code key}, one geometry. */
    private static Geometry merged(Gather key, java.util.List<Geometry> geometries) {
        var mesh = new com.jme3.scene.Mesh();
        jme3tools.optimize.GeometryBatchFactory.mergeGeometries(geometries, mesh);
        mesh.updateCounts();
        mesh.updateBound();
        var one = new Geometry("chunk", mesh);
        one.setMaterial(key.material());
        one.setQueueBucket(key.bucket());
        one.setShadowMode(key.shadows());
        return one;
    }

    /** What pieces must share to be gathered into one geometry: a material, a bucket, shadows, a vertex layout. */
    private record Gather(Material material, com.jme3.renderer.queue.RenderQueue.Bucket bucket,
            com.jme3.renderer.queue.RenderQueue.ShadowMode shadows, String layout) {

        static Gather of(Geometry geometry) {
            var layout = new StringBuilder();
            for (var buffer : geometry.getMesh().getBufferList()) {
                if (buffer.getBufferType() != com.jme3.scene.VertexBuffer.Type.Index) {
                    layout.append(buffer.getBufferType()).append(buffer.getNumComponents()).append(' ');
                }
            }
            return new Gather(geometry.getMaterial(), geometry.getQueueBucket(), geometry.getShadowMode(),
                    layout.toString());
        }
    }

    /**
     * Every piece bent over the map's relief: each corner of its mesh raised by the relief under it, so a floor
     * rises and falls with the ground its walkers stand on and a wall follows the ground along its foot. The layout
     * is the storeys', and this the one place the relief enters the picture.
     *
     * <p>Only where a map has relief. A mesh is shared by every piece cut from one model, so a bent piece bends a
     * copy of its own; a chunk's gathered mesh is its own already, and is bent where it is.
     */
    private void drape(PathGrid grid) {
        if (grid.getRelief() == null) {
            return;
        }
        drape(grid, root, chunks.length > 0);
    }

    /** Every geometry under {@code under} bent over the relief; its own mesh where {@code ownMeshes}, else a copy. */
    private static void drape(PathGrid grid, Spatial under, boolean ownMeshes) {
        under.updateGeometricState();
        var local = new Vector3f();
        var world = new Vector3f();
        under.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry)) {
                return;
            }
            var mesh = ownMeshes ? geometry.getMesh() : geometry.getMesh().deepClone();
            var positions = mesh.getFloatBuffer(com.jme3.scene.VertexBuffer.Type.Position);
            var transform = geometry.getWorldTransform();
            for (int i = 0; i + 2 < positions.limit(); i += 3) {
                local.set(positions.get(i), positions.get(i + 1), positions.get(i + 2));
                transform.transformVector(local, world);
                world.y += grid.reliefHeight(new Coord3D(world.x, world.z, 0f));
                transform.transformInverseVector(world, local);
                positions.put(i, local.x).put(i + 1, local.y).put(i + 2, local.z);
            }
            mesh.getBuffer(com.jme3.scene.VertexBuffer.Type.Position).setUpdateNeeded();
            mesh.updateBound();
            geometry.setMesh(mesh);
        });
    }

    /**
     * What colour a piece is given beyond the kit's own, so that two surfaces the
     * player has to tell apart are not the same picture at two heights.
     *
     * <p>Only the ones that lie flat. A wall, a ledge and a tree are already told
     * apart by standing up — they meet the sun at a different angle and the shader
     * shades them for it. A floor and the lid over the rock are the same model, the
     * same texture and the same normal, so nothing whatever separates them, and the
     * one thing the player most needs off the picture is which of the two he can
     * walk on. Storeys are the same question one level out: a raised room is the
     * same floor higher up, and from a camera looking down a slope that is very
     * little to go on.
     *
     * <p><b>Counted down from the tallest storey on the map, not up from the
     * ground.</b> A tint multiplies, so it can only ever darken — asking for a
     * floor half again as bright hands back the white it started from, and the
     * setting silently does nothing at all. Hanging it off the top says the same
     * thing in the one direction the arithmetic allows: the highest floor is left
     * alone and everything under it is stepped down, which is the picture "each
     * storey up is lighter" was asking for.
     */
    private static int tintFor(Tileset kit, TileLayout.Piece piece, float ground, float storey, int tallest) {
        float shade = storey <= 0f ? 1f
                : (float) Math.pow(kit.getStoreyShade(),
                        Math.round(ground / storey) - tallest);
        return switch (piece) {
            case CAP -> shaded(kit.getCapTint(), shade);
            case FLOOR -> shaded(0xFFFFFF, shade);
            default -> shaded(0xFFFFFF, 1f);
        };
    }

    /** A packed colour with every channel multiplied, clamped where it would overflow. */
    private static int shaded(int packedRgb, float by) {
        if (by == 1f) {
            return packedRgb;
        }
        int red = Math.clamp(Math.round((packedRgb >> 16 & 0xFF) * by), 0, 255);
        int green = Math.clamp(Math.round((packedRgb >> 8 & 0xFF) * by), 0, 255);
        int blue = Math.clamp(Math.round((packedRgb & 0xFF) * by), 0, 255);
        return red << 16 | green << 8 | blue;
    }

    /**
     * One storey of the side of a block of rock — a retaining wall.
     *
     * <p>Scaled by what it measures rather than by a number in the file, like the
     * stair and for the same reason: how tall a slab is against how wide it is
     * belongs to the model, and a kit is then right by being shipped. Its height is
     * made to fill exactly one storey, and the scale is <b>uniform</b>, so a piece
     * modelled square comes out a cell wide as well — which is what a modular wall
     * is, and what stretching it to fit would have thrown away.
     *
     * <p>The face on the boundary and the body behind it, like any wall. How far
     * back that is is measured too, rather than read from {@code wallShift}: this
     * is not the kit's own wall and there is no reason its origin should sit where
     * that one's does.
     */
    private void addRockFace(String asset, Standing placement, float storey) {
        var piece = tiles.piece(placement.kit(), asset, 0xFFFFFF);
        if (piece == null || storey <= 0f) {
            return;
        }
        var box = boundsOf(asset, piece);
        float tall = Math.max(0.001f, box.getYExtent() * 2f);
        float scale = storey / tall;
        float yaw = FastMath.DEG_TO_RAD * placement.yaw();
        piece.setLocalScale(scale);
        piece.setLocalRotation(new com.jme3.math.Quaternion()
                .fromAngleAxis(yaw, Vector3f.UNIT_Y));
        float back = -(box.getCenter().z + box.getZExtent()) * scale;
        piece.setLocalTranslation(
                placement.x() + back * FastMath.sin(yaw),
                placement.ground() + (box.getYExtent() - box.getCenter().y) * scale,
                placement.z() + back * FastMath.cos(yaw));
        cellNode(placement.cellY() * cellsWide + placement.cellX()).attachChild(piece);
    }

    /**
     * One piece to draw: where it stands, what it stands on, and how many storeys
     * of the map it stands for.
     *
     * <p>A layout placement and this are not the same thing, and the difference is
     * the whole of what a kit of trees needs. The layout speaks in <em>faces</em>:
     * a boundary the player may not cross, one piece of it per storey of drop. A
     * tree is not a face. It has one body, it stands somewhere rather than facing
     * somewhere, and it is as big as the thing it stands for.
     *
     * @param inRock whether it stands <em>inside</em> a piece of rock rather than
     *     against it — then it is already where it belongs, needs no nudging back
     *     off a boundary, and scatters evenly about its own point instead of
     *     behind a line
     * @param kit the kit it is drawn from: the map's, or the one its cell's look names
     */
    private record Standing(TileLayout.Piece piece, int cellX, int cellY, float x, float z,
            float yaw, float ground, int storeys, boolean inRock, Tileset kit) {

        static Standing facing(TileLayout.Placement of, int storeys, Tileset kit) {
            return new Standing(of.piece(), of.cellX(), of.cellY(), of.x(), of.z(), of.yaw(),
                    of.ground(), storeys, false, kit);
        }

        /** Whether this is a piece that stands up rather than one that lies flat. */
        boolean upright() {
            return piece == TileLayout.Piece.WALL || piece == TileLayout.Piece.CORNER
                    || piece == TileLayout.Piece.LEDGE;
        }
    }

    /**
     * What to draw, which is the layout for a kit of masonry and a rearrangement
     * of it for a kit of things.
     *
     * <p>The <b>faces of one piece of rock become one body</b>: a wall a single
     * cell thick is walled from both sides, which for stone is right, because
     * stone has two faces and you can stand on either side of it. A tree seen from
     * both sides is one tree, and drawing it twice put a tree at the foot of the
     * rock and a second one on the roof with the lid between them — which is
     * exactly the picture this is here to stop.
     *
     * <p>So a thing is drawn once per piece of rock, in the middle of it, and
     * <b>on top of it</b> — standing on the rock's own lid, one storey tall,
     * whatever the rock is. It used to be grown from the lowest floor beside the
     * rock to the highest, which was wrong twice over. A tree standing for two
     * storeys of rock came out twice the size all round, because a thing cannot be
     * made taller without being made wider, and a tree twice as wide as the room
     * beside it is not what a tall wood looks like. And where <em>no</em> floor
     * beside the rock was on the ground — the rock ringing a room two storeys up,
     * which is most of the rock on an upper floor — the lowest face was up there
     * too, so the tree was drawn at the room's own height with nothing whatever
     * underneath it. From a chair: trees hanging in the air.
     *
     * <p><b>And the faces are kept, not thrown away.</b> They were, and that is
     * what left the wood standing on nothing: the sides of a block of rock are
     * exactly the faces the layout had already worked out, and discarding them
     * discarded the only thing that ever drew the rock. They are drawn instead
     * with whatever the kit says its rock is faced with — a retaining wall; see
     * {@link Tileset#rockFace}. The tree is what grows on top of it.
     *
     * <p>Only where the rock actually stands <em>above</em> the face's foot. A kit
     * of this sort lays its lids on the ground — what you cannot walk into is a
     * tree line, not a wall — so most of its rock is at the floor's own height and
     * has no side to show. Facing those too would put a stone kerb round every
     * tree in the forest.
     */
    private java.util.List<Standing> plan(java.util.List<TileLayout.Placement> layout,
            PathGrid grid) {
        var plan = new java.util.ArrayList<Standing>();
        float cell = grid.getCellSize();
        // Per piece of rock: the turn and cell of whichever face was met first — a
        // body has no facing of its own, but the cell it is filed under decides
        // when the fog hides it. Where it stands is the rock's, not the faces'.
        var bodies = new java.util.LinkedHashMap<Integer, float[]>();
        for (var placement : layout) {
            var kit = kitOf(drawnBy(placement, grid, cell));
            var standing = Standing.facing(placement, 1, kit);
            if (!kit.wallFillsRock()) {
                plan.add(standing); // masonry: the layout's faces are what it draws
                continue;
            }
            if (!standing.upright() || placement.piece() == TileLayout.Piece.CORNER) {
                // A corner post plugs the notch between two walls. There is no
                // notch in a wood, and a wall slab dropped into one would lie
                // across both of the faces that meet there.
                plan.add(standing);
                continue;
            }
            if (placement.piece() == TileLayout.Piece.LEDGE
                    && rockFacedBy(placement, grid, cell) < 0) {
                // A ledge facing off the edge of the map is the outside of the
                // world. Nobody stands there to see it, and a wood's whole border
                // would be faced, storey by storey, for nothing.
                continue;
            }
            float proud = kit.getWallHeight() * (cell / kit.getWallTileSize());
            if (topOfTheRockAt(placement, grid, cell) + proud > placement.ground() + 0.001f) {
                plan.add(new Standing(TileLayout.Piece.ROCK_FACE, placement.cellX(),
                        placement.cellY(), placement.x(), placement.z(), placement.yaw(),
                        placement.ground(), 1, false, kit));
            }
            int rock = rockFacedBy(placement, grid, cell);
            if (rock >= 0) {
                // First face wins: the rest say nothing a body needs. putIfAbsent
                // rather than merge, and the order the layout comes in is settled,
                // so the same map grows the same wood.
                bodies.putIfAbsent(rock,
                        new float[] {placement.yaw(), placement.cellX(), placement.cellY()});
            }
        }
        for (var body : bodies.entrySet()) {
            int rock = body.getKey();
            var found = body.getValue();
            int rockX = rock % grid.getWidth();
            int rockY = rock / grid.getWidth();
            plan.add(new Standing(TileLayout.Piece.WALL, (int) found[1], (int) found[2],
                    (rockX + 0.5f) * cell, (rockY + 0.5f) * cell,
                    found[0], highestFloorAround(grid, rockX, rockY), 1, true, kitOf(rock)));
        }
        return plan;
    }

    /**
     * The cell whose look draws a piece, {@code cy * width + cx}: a wall against rock by the rock — the rock decides
     * whether it is a cliff or a wood — a corner post by the rock in its notch, and everything else by the cell it
     * was laid for: a floor and its steps by the floor, a lid and a ledge by their own rock.
     */
    private static int drawnBy(TileLayout.Placement placement, PathGrid grid, float cell) {
        int filed = placement.cellY() * grid.getWidth() + placement.cellX();
        return switch (placement.piece()) {
            case WALL -> {
                int rock = rockFacedBy(placement, grid, cell);
                yield rock >= 0 ? rock : filed;
            }
            case CORNER -> notchOf(placement, grid, cell, filed);
            default -> filed;
        };
    }

    /** The rock in a corner post's notch: the cell across the corner, or one of the two beside it, where stone. */
    private static int notchOf(TileLayout.Placement placement, PathGrid grid, float cell, int filed) {
        int dx = placement.x() / cell > placement.cellX() + 0.5f ? 1 : -1;
        int dy = placement.z() / cell > placement.cellY() + 0.5f ? 1 : -1;
        for (var step : new int[][] {{dx, dy}, {dx, 0}, {0, dy}}) {
            int nx = placement.cellX() + step[0];
            int ny = placement.cellY() + step[1];
            if (grid.inBounds(nx, ny) && grid.isTerrainBlocked(nx, ny)) {
                return ny * grid.getWidth() + nx;
            }
        }
        return filed;
    }

    /** The kit a cell wears: its look's, where the map names one that can build a floor, or the map's own. */
    private Tileset kitOf(int cell) {
        if (cellKits != null && cell >= 0 && cell < cellKits.length && cellKits[cell] != null
                && cellKits[cell].isUsable()) {
            return cellKits[cell];
        }
        return tileset;
    }

    /**
     * How high the thing this face is the side of reaches.
     *
     * <p>The rock it faces, where there is rock; the floor it holds up, where
     * there is not. Both are steps the face has to cover, and the second is the
     * edge of a raised terrace with no stone in it at all.
     *
     * <p>And a ledge is the side of its <em>own</em> rock. It stands where one lid of
     * rock is roofed higher than the rock beside it, and faces the lower one -- so
     * read against the rock it faces, its top was that lower lid, which is its own
     * foot, and it was never above anything. Every step between two lids went
     * undrawn: a row of trees at the foot of a raised block, and above them the
     * block's side open onto the dark.
     */
    private static float topOfTheRockAt(TileLayout.Placement placement, PathGrid grid,
            float cell) {
        if (placement.piece() == TileLayout.Piece.LEDGE) {
            return highestFloorAround(grid, placement.cellX(), placement.cellY());
        }
        int rock = rockFacedBy(placement, grid, cell);
        if (rock >= 0) {
            return highestFloorAround(grid, rock % grid.getWidth(), rock / grid.getWidth());
        }
        return grid.storeyHeight(placement.cellX(), placement.cellY());
    }

    /**
     * The piece of rock a wall faces, as {@code cy * width + cx}, or {@code -1}
     * where there is none — a retaining wall, or the edge of the map.
     *
     * <p>Read back off the placement rather than passed down with it: a wall is put
     * halfway to its neighbour, so which neighbour that is is written in where it
     * ended up.
     */
    private static int rockFacedBy(TileLayout.Placement placement, PathGrid grid, float cell) {
        int nx = placement.cellX()
                + Math.round((placement.x() / cell - (placement.cellX() + 0.5f)) * 2f);
        int ny = placement.cellY()
                + Math.round((placement.z() / cell - (placement.cellY() + 0.5f)) * 2f);
        return grid.inBounds(nx, ny) && grid.isTerrainBlocked(nx, ny) ? ny * grid.getWidth() + nx : -1;
    }

    /**
     * One piece of a kit, laid where the plan says and dressed the way the kit
     * asks.
     *
     * <p>{@code copy} of {@code clump} is which of the several things standing
     * where the layout asked for one wall this is — one, for masonry. They are
     * arranged in a ring, each a different size and facing, and which size and
     * which facing is settled by where the ring stands rather than by chance, so
     * the same wood grows the same way every time it is built.
     */
    private void addKitPiece(String asset, Standing placement, int copy, int clump,
            float pieceScale, float wallScale, float cell, int tint) {
        var kit = placement.kit();
        var piece = tiles.piece(kit, asset, tint);
        if (piece == null) {
            return;
        }
        float yaw = FastMath.DEG_TO_RAD * placement.yaw();
        float scale = pieceScale * placement.storeys();
        float facing = yaw;
        float side = 0f;
        float ring = 0f;
        if (clump > 1) {
            float turn = FastMath.TWO_PI
                    * (copy + steady(placement.x(), placement.z(), copy, 0)) / clump;
            float radius = kit.getWallSpread() * cell
                    * (0.55f + 0.45f * steady(placement.x(), placement.z(), copy, 1));
            side = radius * FastMath.sin(turn);
            // A body scatters about its own middle and stays in its cell. A face
            // scatters behind its line by the ring's own radius, so what leans out
            // over open ground is canopy and the trunk keeps to the solid side.
            ring = radius * (placement.inRock() ? FastMath.cos(turn)
                    : -(1f + FastMath.cos(turn)));
            facing = FastMath.TWO_PI * steady(placement.x(), placement.z(), copy, 2);
        }
        if (kit.getWallVariety() > 0f && placement.upright()) {
            scale *= 1f + kit.getWallVariety()
                    * (steady(placement.x(), placement.z(), copy, 3) - 0.5f);
        }
        boolean standing = placement.upright();
        // Measured before it is scaled, because the answer is a fact about the
        // model and the same for every copy of it.
        float surface = placement.piece() == TileLayout.Piece.FLOOR
                ? topOf(asset, piece) * scale : 0f;
        piece.setLocalScale(scale);
        piece.setLocalRotation(new com.jme3.math.Quaternion()
                .fromAngleAxis(facing, Vector3f.UNIT_Y));
        // Everything lies on the floor it belongs to except the lid over the
        // stone, which sits level with the tops of the walls it roofs. A floor
        // tile's top surface is what has to land on the floor's own height, so
        // it is sunk by however thick the tile is.
        // A ledge stands on the roof rather than on the floor: it is the face
        // of the step between two lids, and a lid sits a wall's height up.
        float y = placement.ground() + switch (placement.piece()) {
            case CAP -> kit.getWallHeight() * wallScale;
            case LEDGE -> (kit.getWallHeight() + kit.getWallLift()) * wallScale;
            case WALL, CORNER -> kit.getWallLift() * wallScale;
            default -> -surface;
        };
        // A wall's face belongs on the boundary, and where its own kit put its
        // origin decides how far back that is. Along the wall's own facing,
        // which the yaw has just turned.
        //
        // A body standing in the rock is not on a boundary and takes none of it:
        // the shift exists to move a face off a line, and there is no line.
        float back = (standing && !placement.inRock()
                ? kit.getWallShift() * wallScale : 0f) + ring;
        piece.setLocalTranslation(
                placement.x() + back * FastMath.sin(yaw) + side * FastMath.cos(yaw),
                y,
                placement.z() + back * FastMath.cos(yaw) - side * FastMath.sin(yaw));
        cellNode(placement.cellY() * cellsWide + placement.cellX()).attachChild(piece);
    }

    /**
     * A settled number in {@code [0, 1)} for a place, a copy and a purpose.
     *
     * <p>Not chance: a wood that rearranged itself every time the map was redrawn
     * would be a wood you could not learn, and the same seed has to grow the same
     * map. So it is a hash of where the thing stands, and nothing else.
     */
    private static float steady(float x, float z, int copy, int purpose) {
        int hash = Float.floatToIntBits(x) * 0x27d4eb2d;
        hash = (hash ^ Float.floatToIntBits(z)) * 0x165667b1;
        hash = (hash ^ (copy * 0x9e3779b9 + purpose)) * 0x85ebca6b;
        hash ^= hash >>> 15;
        return (hash >>> 8) / (float) (1 << 24);
    }

    /**
     * A flight of steps, made to fit the cell it stands on and the storey it
     * climbs.
     *
     * <p>Two things about a stair model are measured rather than written down,
     * because kits disagree about both and neither is guessable from a file name.
     * How big it is: scaled so its run is a cell and its rise is a storey, so the
     * top step lands exactly on the floor above rather than a hand's breadth over
     * or under it. And which way it climbs: one kit's steps rise toward -z and the
     * next kit's toward +x, so the model is turned by the difference between the
     * way it happens to face and the way this one has to.
     *
     * <p>Where its origin sits is a third, and is measured the same way. A kit may
     * put it in the middle of the flight or at the foot of the bottom step, and a
     * model whose origin is at one end, laid by its origin on the middle of a
     * cell, ends up half a cell out — hanging over the drop it was meant to join,
     * with its foot in the room behind.
     */
    private void addStair(PathGrid grid, Standing placement, float cell) {
        var asset = placement.kit().getStairs();
        var piece = asset == null ? null : tiles.piece(placement.kit(), asset, 0xFFFFFF);
        if (piece == null) {
            addBuiltSteps(placement, cell, grid.getLevelHeight());
            return;
        }
        var box = boundsOf(asset, piece);
        var climb = climbOf(asset, piece);
        float run = Math.max(0.001f, Math.abs(climb.x) > Math.abs(climb.z)
                ? box.getXExtent() * 2f : box.getZExtent() * 2f);
        // Its rise is its run. A modular stair fills one cell and joins the floor
        // above it — that is what makes it modular — so the two are the same
        // number, and taking it from the model's own footprint costs nothing.
        //
        // The height of the box is *not* that number, and the difference shows.
        // A flight has rails, and rails stand a hand above the landing they guard:
        // KayKit's climbs exactly 4 and boxes 5.1, so measuring the box squashed
        // the stair to four fifths and left it ending in mid-air a fifth of a
        // storey below the floor it was supposed to reach.
        float rise = run;
        piece.setLocalScale(cell / run, grid.getLevelHeight() / rise, cell / run);

        float own = FastMath.atan2(climb.x, climb.z);
        float yaw = FastMath.DEG_TO_RAD * placement.yaw() - own;
        piece.setLocalRotation(new com.jme3.math.Quaternion()
                .fromAngleAxis(yaw, Vector3f.UNIT_Y));
        // Its foot on the lower floor: the model's own bottom is wherever its kit
        // put the origin, so it is lifted by however far it hangs below.
        //
        // And the middle of the flight on the middle of the cell. Where a kit put
        // the origin across the floor is its own business too, and KayKit's is at
        // the foot of the bottom step rather than in the middle — so the flight
        // laid by its origin covered half this cell and half the next, hanging
        // over the drop with a whole cell of nothing under one end of it. Turned
        // first, because the offset is in the model's axes and the model has just
        // been turned out of them.
        float across = box.getCenter().x * (cell / run);
        float along = box.getCenter().z * (cell / run);
        piece.setLocalTranslation(
                placement.x() - (across * FastMath.cos(yaw) + along * FastMath.sin(yaw)),
                placement.ground() + (box.getYExtent() - box.getCenter().y)
                        * (grid.getLevelHeight() / rise),
                placement.z() - (along * FastMath.cos(yaw) - across * FastMath.sin(yaw)));
        cellNode(placement.cellY() * cellsWide + placement.cellX()).attachChild(piece);
    }

    /** How many blocks a built flight of steps is cut into. */
    private static final int BUILT_STEPS = 6;

    /**
     * Steps built out of blocks, for a kit that ships no stair of its own.
     *
     * <p>Plain, but a flight of steps rather than a lump: six treads, each one
     * tread deeper into the cell and one step higher, so the top of the last one
     * lands exactly on the floor above.
     *
     * <p>Every block is laid out along the node's own {@code +z} and the node is
     * turned once at the end. Turning both — the blocks by the yaw and then the
     * node by it again — was what made this a grey box: the treads were rotated
     * twice and piled up on one another in the middle of the cell, which is
     * exactly what a hero walks into and then pops out of the top of.
     */
    private void addBuiltSteps(Standing placement, float cell, float storey) {
        if (storey <= 0f) {
            return;
        }
        var stone = material.of(ROCK, null);
        var node = new Node("steps");
        float depth = cell / BUILT_STEPS;
        for (int i = 0; i < BUILT_STEPS; i++) {
            // Each tread is a slab standing on the floor, as tall as the climb has
            // got by the time you are on it.
            float height = storey * (i + 1) / BUILT_STEPS;
            var step = new Geometry("step", new Box(cell / 2f, height / 2f, depth / 2f));
            step.setMaterial(stone);
            step.setLocalTranslation(0f, height / 2f, (i + 0.5f) * depth - cell / 2f);
            node.attachChild(step);
        }
        node.setLocalRotation(new com.jme3.math.Quaternion()
                .fromAngleAxis(FastMath.DEG_TO_RAD * placement.yaw(), Vector3f.UNIT_Y));
        node.setLocalTranslation(placement.x(), placement.ground(), placement.z());
        cellNode(placement.cellY() * cellsWide + placement.cellX()).attachChild(node);
    }

    private final java.util.Map<String, com.jme3.bounding.BoundingBox> tileBounds =
            new java.util.HashMap<>();
    private final java.util.Map<String, Vector3f> tileClimb = new java.util.HashMap<>();

    private com.jme3.bounding.BoundingBox boundsOf(String asset, Spatial piece) {
        return tileBounds.computeIfAbsent(asset, path -> {
            piece.updateModelBound();
            piece.updateGeometricState();
            return piece.getWorldBound() instanceof com.jme3.bounding.BoundingBox box
                    ? (com.jme3.bounding.BoundingBox) box.clone()
                    : new com.jme3.bounding.BoundingBox(Vector3f.ZERO, 1f, 1f, 1f);
        });
    }

    /**
     * Which way a model climbs, in its own axes.
     *
     * <p>Read off the mesh: the middle of its highest vertices, less the middle of
     * its lowest. A staircase is the one piece whose meaning is a direction, and
     * no kit says which way it faces.
     */
    private Vector3f climbOf(String asset, Spatial piece) {
        return tileClimb.computeIfAbsent(asset, path -> {
            var high = new Vector3f();
            var low = new Vector3f();
            var counts = new int[2];
            var box = boundsOf(asset, piece);
            float top = box.getCenter().y + box.getYExtent();
            float bottom = box.getCenter().y - box.getYExtent();
            float cut = bottom + (top - bottom) * 0.8f;
            float floor = bottom + (top - bottom) * 0.2f;
            for (var vertex : verticesOf(piece)) {
                if (vertex.y >= cut) {
                    high.addLocal(vertex);
                    counts[0]++;
                } else if (vertex.y <= floor) {
                    low.addLocal(vertex);
                    counts[1]++;
                }
            }
            if (counts[0] == 0 || counts[1] == 0) {
                return Vector3f.UNIT_Z.clone();
            }
            var direction = high.divide(counts[0]).subtract(low.divide(counts[1]));
            direction.y = 0f;
            return direction.lengthSquared() < 0.0001f ? Vector3f.UNIT_Z.clone()
                    : direction.normalizeLocal();
        });
    }

    private static java.util.List<Vector3f> verticesOf(Spatial piece) {
        var points = new java.util.ArrayList<Vector3f>();
        piece.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry)) {
                return;
            }
            var buffer = geometry.getMesh().getFloatBuffer(
                    com.jme3.scene.VertexBuffer.Type.Position);
            if (buffer == null) {
                return;
            }
            for (int i = 0; i + 2 < buffer.limit(); i += 3) {
                points.add(new Vector3f(buffer.get(i), buffer.get(i + 1), buffer.get(i + 2)));
            }
        });
        return points;
    }

    /** How high a floor tile's surface sits above its own origin, per asset. */
    private final java.util.Map<String, Float> tileTops = new java.util.HashMap<>();

    /**
     * The top of a floor tile in its own model units.
     *
     * <p>Kits disagree about this and it is not a detail: a unit stands at y = 0
     * and so does everything drawn on the ground — the ring under a selected
     * creature, the mark where an order landed. A kit whose tile is a flat plane
     * at its origin puts its surface at zero and those show; a kit whose tile is a
     * slab a fifth of a unit thick buries them, and the player is left clicking
     * with nothing to show for it.
     *
     * <p>Measured rather than written down, so a kit is right by being shipped.
     */
    private float topOf(String asset, Spatial piece) {
        return tileTops.computeIfAbsent(asset, path -> {
            piece.updateModelBound();
            piece.updateGeometricState();
            return piece.getWorldBound() instanceof com.jme3.bounding.BoundingBox box
                    ? box.getCenter().y + box.getYExtent() : 0f;
        });
    }

    /**
     * Anything nobody could see even if it were drawn is left out of the picture.
     *
     * <p>All this does is cull. How bright a piece is drawn belongs to its own
     * material, which samples the fog at the place the fragment stands — see
     * {@link FogMap}.
     *
     * <p>Per <b>piece</b>, and asked about where the piece stands rather than
     * which cell it was filed under. Those are not the same thing: a wall sits on
     * the line between two cells and a roof lies over rock that can be diagonal to
     * the room it was built with. Culling by the cell a piece belonged to dropped
     * walls and roofs that stood beside a fully lit room — the light ran out, and
     * instead of the wall going dim it went out, which reads as a bug in the
     * scene rather than as fog.
     */
    private void applyDiscoveryToTiles(Discovery seen) {
        for (var node : cellNodes) {
            if (node == null) {
                continue; // stone; nothing was built here
            }
            var pieces = node.getChildren();
            for (int i = 0; i < pieces.size(); i++) {
                var piece = pieces.get(i);
                var at = piece.getLocalTranslation();
                piece.setCullHint(seen.hiddenAt(at.x, at.z)
                        ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
            }
        }
    }

    /**
     * The model one piece is drawn with: the kit's one, or, where it names several of a kind, the one this piece takes
     * by where it stands — the settled number that sizes and turns a clump, for a purpose of its own, so a wall of
     * rocks is the same rocks in the same places on every build and each member of a clump takes its own.
     */
    private static String assetFor(Standing standing, int copy) {
        var choices = switch (standing.piece()) {
            case WALL, LEDGE -> standing.kit().getWalls();
            case CORNER -> standing.kit().getCorners();
            default -> java.util.List.<String>of();
        };
        if (choices.size() < 2) {
            return assetFor(standing.kit(), standing.piece());
        }
        return choices.get((int) (steady(standing.x(), standing.z(), copy, 4) * choices.size()));
    }

    /**
     * A chunk is left out of the picture only when all of it, and a cell round it, is behind the dark: a wall on the
     * chunk's edge faces the cell beside it, as a piece was asked about where it stood with a cell round it. Asked of
     * the chunks near a cell whose light moved, and of every chunk the first time after a new map.
     */
    private void applyDiscoveryToChunks(Discovery seen) {
        if (everyChunk) {
            dirtyChunks.set(0, chunks.length);
            everyChunk = false;
        } else {
            var moved = seen.moved();
            int chunksDeep = chunks.length / chunksWide;
            for (int i = 0; i < moved.size(); i++) {
                int at = moved.get(i);
                int cx = at % cellsWide;
                int cy = at / cellsWide;
                int fromX = Math.max(0, Math.floorDiv(cx - 1, CHUNK_CELLS));
                int toX = Math.min(chunksWide - 1, (cx + 1) / CHUNK_CELLS);
                for (int y = Math.max(0, Math.floorDiv(cy - 1, CHUNK_CELLS));
                        y <= Math.min(chunksDeep - 1, (cy + 1) / CHUNK_CELLS); y++) {
                    dirtyChunks.set(y * chunksWide + fromX, y * chunksWide + toX + 1);
                }
            }
        }
        for (int chunk = dirtyChunks.nextSetBit(0); chunk >= 0; chunk = dirtyChunks.nextSetBit(chunk + 1)) {
            if (chunks[chunk] != null) {
                chunks[chunk].setCullHint(chunkHidden(seen, chunk)
                        ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
            }
        }
        dirtyChunks.clear();
    }

    private boolean chunkHidden(Discovery seen, int chunk) {
        int x0 = chunk % chunksWide * CHUNK_CELLS;
        int y0 = chunk / chunksWide * CHUNK_CELLS;
        for (int cy = y0 - 1; cy <= y0 + CHUNK_CELLS; cy++) {
            for (int cx = x0 - 1; cx <= x0 + CHUNK_CELLS; cx++) {
                if (seen.lightAt(cx, cy) > Discovery.DARK) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String assetFor(Tileset kit, TileLayout.Piece piece) {
        return switch (piece) {
            case FLOOR -> kit.getFloor();
            case WALL -> kit.getWall();
            case CORNER -> kit.getCorner();
            // A floor tile, laid on top of the rock rather than under the room --
            // and only where there are walls to roof. A kit with no walls has
            // nothing to see over, and its lids would float above bare ground.
            case CAP -> kit.getWall() == null ? null : kit.getFloor();
            case LEDGE -> kit.getWall();
            case STAIR -> kit.getStairs();
            // Only a kit whose wall is a thing rather than a surface names one.
            // Masonry fills a raised block of rock with its own courses.
            case ROCK_FACE -> kit.getRockFace();
        };
    }

    private Node cellNode(int index) {
        if (building != null) {
            return building.computeIfAbsent(index, cell -> {
                var node = new Node("cell");
                buildingRoot.attachChild(node);
                return node;
            });
        }
        if (cellNodes[index] == null) {
            cellNodes[index] = new Node("cell");
            root.attachChild(cellNodes[index]);
        }
        return cellNodes[index];
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
        if (chunks.length > 0) {
            applyDiscoveryToChunks(seen);
        } else if (tiled()) {
            applyDiscoveryToTiles(seen);
        }
        if (tiled()) {
            return;
        }
        for (int index = 0; index < rocks.length; index++) {
            var rock = rocks[index];
            if (rock != null) {
                rock.setCullHint(seen.hidden(index % cellsWide, index / cellsWide)
                        ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
            }
        }
    }
}
