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
 * <p>A game may ask for <b>discovery</b>, in which case the scene keeps a handle
 * per cell and can be told, each frame, which of them to leave out of the picture
 * — see {@link #applyDiscovery}. What it does <em>not</em> do is decide how bright
 * anything is drawn: that is one sheet over the whole map, in {@link FogOverlay},
 * because a brightness per cell is a floor of squares whatever it is computed
 * from. Games that do not ask for discovery pay nothing — no handles are kept and
 * the loop never runs, which matters because an RTS map is many times larger than
 * a dungeon floor.
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

    /** The kit the ground is built from, or {@code null} to lay down blocks. */
    private final Tileset tileset;
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
        this.tileset = tileset != null && tileset.isUsable() && tiles != null ? tileset : null;
        this.tiles = tiles;
    }

    private boolean tiled() {
        return tileset != null;
    }

    Node node() {
        return root;
    }

    /**
     * How high the world stands — the top of a wall, or of a block of stone.
     *
     * <p>Asked by the fog, which hangs its sheet at exactly this height. A sheet
     * is flat and the world is not, so the two only line up at one height, and the
     * one to choose is the height of the surfaces with <em>edges</em>: a roof over
     * the rock is a tile with a wall along the side of it, and a sheet hung
     * anywhere else covers half of it and leaves the other half lit. The floor,
     * which is where the sheet then does not line up, has no edges of its own —
     * only a gradient, which slides a fraction of a cell and looks the same.
     */
    float standingHeight(PathGrid grid) {
        if (!tiled()) {
            return BLOCK_HALF_HEIGHT * 2f;
        }
        float cell = grid == null ? PathGrid.DEFAULT_CELL_SIZE : grid.getCellSize();
        return tileset.getWallHeight() * (cell / tileset.getTileSize());
    }

    /** Lay out {@code grid}, discarding whatever world was there before. */
    void rebuild(PathGrid grid) {
        root.detachAllChildren();
        cellNodes = new Node[0];
        if (tiled()) {
            rebuildFromTiles(grid);
            return;
        }

        float worldW = grid == null ? DEFAULT_WIDTH : grid.getWidth() * grid.getCellSize();
        float worldH = grid == null ? DEFAULT_HEIGHT : grid.getHeight() * grid.getCellSize();

        var ground = new Geometry("ground", new Quad(worldW, worldH));
        ground.setMaterial(material.apply(GROUND));
        ground.rotate(-FastMath.HALF_PI, 0, 0);
        ground.setLocalTranslation(0, 0, worldH);
        root.attachChild(ground);

        if (grid == null) {
            rocks = new Geometry[0];
            cellsWide = 0;
            return;
        }
        float cell = grid.getCellSize();
        cellsWide = grid.getWidth();
        rocks = discovery ? new Geometry[grid.getWidth() * grid.getHeight()] : new Geometry[0];
        var stone = material.apply(ROCK);
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                if (!grid.isBlocked(cx, cy)) {
                    continue;
                }
                var rock = new Geometry("rock",
                        new Box(cell / 2f, BLOCK_HALF_HEIGHT, cell / 2f));
                rock.setMaterial(stone);
                rock.setLocalTranslation((cx + 0.5f) * cell, BLOCK_HALF_HEIGHT,
                        (cy + 0.5f) * cell);
                root.attachChild(rock);
                if (discovery) {
                    rocks[cy * cellsWide + cx] = rock;
                }
            }
        }
    }

    /**
     * Lay the floor out of a modular kit.
     *
     * <p>No ground plane, unlike the block version: with a kit there is nothing
     * under an unvisited cell to hide, so an undiscovered part of the map is
     * simply not built into the picture and the background shows through — which
     * is the fog's own colour, and so the same dark the sheet paints.
     */
    private void rebuildFromTiles(PathGrid grid) {
        if (grid == null) {
            return;
        }
        cellsWide = grid.getWidth();
        cellNodes = new Node[grid.getWidth() * grid.getHeight()];
        float scale = grid.getCellSize() / tileset.getTileSize();

        for (var placement : TileLayout.of(grid)) {
            String asset = assetFor(placement.piece());
            if (asset == null) {
                continue; // a kit without corner posts is a kit with square notches
            }
            var piece = tiles.piece(asset);
            if (piece == null) {
                continue;
            }
            piece.setLocalScale(scale);
            piece.setLocalRotation(new com.jme3.math.Quaternion()
                    .fromAngleAxis(FastMath.DEG_TO_RAD * placement.yaw(), Vector3f.UNIT_Y));
            // Everything lies on the floor except the lid over the stone, which
            // sits level with the tops of the walls it roofs.
            float y = placement.piece() == TileLayout.Piece.CAP
                    ? tileset.getWallHeight() * scale : 0f;
            piece.setLocalTranslation(placement.x(), y, placement.z());
            cellNode(placement.cellY() * cellsWide + placement.cellX()).attachChild(piece);
        }
    }

    /**
     * A cell nobody could see even if it were drawn is left out of the picture.
     *
     * <p>All this does now is cull. How bright a cell is drawn belongs to the fog
     * sheet laid over the whole map — see {@link FogOverlay} — and doing it here
     * as well is what made the floor a field of squares: a brightness per cell,
     * painted flat over every tile in it, however smooth the number itself was.
     *
     * <p>Per cell rather than per piece, which is what the node-per-cell is for: a
     * floor a hundred cells wide is a hundred calls a frame, not a thousand.
     */
    private void applyDiscoveryToTiles(Discovery seen) {
        for (int index = 0; index < cellNodes.length; index++) {
            var node = cellNodes[index];
            if (node == null) {
                continue; // stone; nothing was built here
            }
            node.setCullHint(seen.hidden(index % cellsWide, index / cellsWide)
                    ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
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
     * <p>Unseen stone is <em>hidden</em> rather than left to the sheet. A rock
     * stands six units above the ground, so the fog over it is the fog of the
     * ground a little way behind — and a wall sticking up out of the dark hands
     * the player the shape of a room they have not entered.
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
