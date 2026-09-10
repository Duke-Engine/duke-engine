package uz.duke.client3d;

import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import java.util.function.Function;
import uz.duke.core.pathfind.PathGrid;

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
    private final Function<ColorRGBA, Material> material;
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

    TerrainScene(Node root, Function<ColorRGBA, Material> material) {
        this(root, material, false);
    }

    TerrainScene(Node root, Function<ColorRGBA, Material> material, boolean discovery) {
        this(root, material, discovery, null, null);
    }

    TerrainScene(Node root, Function<ColorRGBA, Material> material, boolean discovery,
            Tileset tileset, TileSource tiles) {
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
        this.tileset = kit != null && kit.isUsable() && tiles != null ? kit : defaultTileset;
        root.detachAllChildren();
        cellNodes = new Node[0];
        if (tiled()) {
            rebuildFromTiles(grid);
            return;
        }

        float worldW = grid == null ? DEFAULT_WIDTH : grid.getWidth() * grid.getCellSize();
        float worldH = grid == null ? DEFAULT_HEIGHT : grid.getHeight() * grid.getCellSize();

        var plane = new Geometry("ground", new Quad(worldW, worldH));
        plane.setMaterial(material.apply(GROUND));
        plane.rotate(-FastMath.HALF_PI, 0, 0);
        plane.setLocalTranslation(0, 0, worldH);
        root.attachChild(plane);

        if (grid == null) {
            rocks = new Geometry[0];
            cellsWide = 0;
            return;
        }
        float cell = grid.getCellSize();
        cellsWide = grid.getWidth();
        rocks = discovery ? new Geometry[grid.getWidth() * grid.getHeight()] : new Geometry[0];
        var stone = material.apply(ROCK);
        var raised = material.apply(GROUND);
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                if (!grid.isBlocked(cx, cy)) {
                    // A room standing above the ground plane needs something under
                    // it, or its floor is a colour on the ground and the units
                    // walking about on it are in mid-air.
                    float ground = grid.groundHeight(cx, cy);
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
    }

    /** The tallest floor touching a cell, so rock and lids rise with the rooms. */
    private static float highestFloorAround(PathGrid grid, int cx, int cy) {
        float highest = 0f;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.inBounds(cx + dx, cy + dy) && !grid.isBlocked(cx + dx, cy + dy)) {
                    highest = Math.max(highest, grid.groundHeight(cx + dx, cy + dy));
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
        cellNodes = new Node[grid.getWidth() * grid.getHeight()];
        float cell = grid.getCellSize();
        float floorScale = cell / tileset.getTileSize();
        // Walls may have been modelled on a different module from the floors, and
        // then they have their own scale — see Tileset.wallTileSize.
        float wallScale = cell / tileset.getWallTileSize();

        for (var placement : TileLayout.of(grid)) {
            if (placement.piece() == TileLayout.Piece.STAIR) {
                addStair(grid, placement, cell);
                continue;
            }
            String asset = assetFor(placement.piece());
            if (asset == null) {
                continue; // a kit without corner posts is a kit with square notches
            }
            var piece = tiles.piece(asset);
            if (piece == null) {
                continue;
            }
            boolean standing = placement.piece() == TileLayout.Piece.WALL
                    || placement.piece() == TileLayout.Piece.CORNER;
            float scale = standing ? wallScale : floorScale;
            // Measured before it is scaled, because the answer is a fact about the
            // model and the same for every copy of it.
            float surface = placement.piece() == TileLayout.Piece.FLOOR
                    ? topOf(asset, piece) * scale : 0f;
            piece.setLocalScale(scale);
            float yaw = FastMath.DEG_TO_RAD * placement.yaw();
            piece.setLocalRotation(new com.jme3.math.Quaternion()
                    .fromAngleAxis(yaw, Vector3f.UNIT_Y));
            // Everything lies on the floor it belongs to except the lid over the
            // stone, which sits level with the tops of the walls it roofs. A floor
            // tile's top surface is what has to land on the floor's own height, so
            // it is sunk by however thick the tile is.
            float y = placement.ground() + (placement.piece() == TileLayout.Piece.CAP
                    ? tileset.getWallHeight() * wallScale
                    : standing ? tileset.getWallLift() * wallScale : -surface);
            // A wall's face belongs on the boundary, and where its own kit put its
            // origin decides how far back that is. Along the wall's own facing,
            // which the yaw has just turned.
            float back = standing ? tileset.getWallShift() * wallScale : 0f;
            piece.setLocalTranslation(
                    placement.x() + back * FastMath.sin(yaw),
                    y,
                    placement.z() + back * FastMath.cos(yaw));
            cellNode(placement.cellY() * cellsWide + placement.cellX()).attachChild(piece);
        }
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
     */
    private void addStair(PathGrid grid, TileLayout.Placement placement, float cell) {
        var asset = tileset == null ? null : tileset.getStairs();
        var piece = asset == null ? null : tiles.piece(asset);
        if (piece == null) {
            addBuiltSteps(placement, cell, grid.getLevelHeight());
            return;
        }
        var box = boundsOf(asset, piece);
        var climb = climbOf(asset, piece);
        float run = Math.max(0.001f, Math.abs(climb.x) > Math.abs(climb.z)
                ? box.getXExtent() * 2f : box.getZExtent() * 2f);
        float rise = Math.max(0.001f, box.getYExtent() * 2f);
        piece.setLocalScale(cell / run, grid.getLevelHeight() / rise, cell / run);

        float own = FastMath.atan2(climb.x, climb.z);
        float yaw = FastMath.DEG_TO_RAD * placement.yaw() - own;
        piece.setLocalRotation(new com.jme3.math.Quaternion()
                .fromAngleAxis(yaw, Vector3f.UNIT_Y));
        // Its foot on the lower floor: the model's own bottom is wherever its kit
        // put the origin, so it is lifted by however far it hangs below.
        piece.setLocalTranslation(placement.x(),
                placement.ground() + (box.getYExtent() - box.getCenter().y)
                        * (grid.getLevelHeight() / rise),
                placement.z());
        cellNode(placement.cellY() * cellsWide + placement.cellX()).attachChild(piece);
    }

    /**
     * Steps built out of blocks, for a kit that ships no stair of its own.
     *
     * <p>Plain, but never wrong: four boxes, each a quarter of the climb, laid up
     * the cell in the direction the player has to walk. A kit that names a stair
     * gets its own.
     */
    private void addBuiltSteps(TileLayout.Placement placement, float cell, float storey) {
        if (storey <= 0f) {
            return;
        }
        int steps = 4;
        var stone = material.apply(ROCK);
        float yaw = FastMath.DEG_TO_RAD * placement.yaw();
        var node = new Node("steps");
        for (int i = 0; i < steps; i++) {
            float depth = cell / steps;
            float height = storey * (i + 1) / steps;
            var step = new Geometry("step", new Box(cell / 2f, height / 2f, depth / 2f));
            step.setMaterial(stone);
            // Along the climb: the deepest step is the highest, at the far end.
            float along = (i + 0.5f) * depth - cell / 2f;
            step.setLocalTranslation(along * FastMath.sin(yaw), height / 2f,
                    along * FastMath.cos(yaw));
            node.attachChild(step);
        }
        node.setLocalRotation(new com.jme3.math.Quaternion()
                .fromAngleAxis(yaw, Vector3f.UNIT_Y));
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

    private String assetFor(TileLayout.Piece piece) {
        return switch (piece) {
            case FLOOR -> tileset.getFloor();
            case WALL -> tileset.getWall();
            case CORNER -> tileset.getCorner();
            // A floor tile, laid on top of the rock rather than under the room --
            // and only where there are walls to roof. A kit with no walls has
            // nothing to see over, and its lids would float above bare ground.
            case CAP -> tileset.getWall() == null ? null : tileset.getFloor();
            case STAIR -> tileset.getStairs();
        };
    }

    private Node cellNode(int index) {
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
        if (tiled()) {
            applyDiscoveryToTiles(seen);
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
