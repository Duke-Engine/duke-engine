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
 * <p>A game may ask for <b>discovery</b>, in which case the scene also carries a
 * lid over every cell and can be told, each frame, which cells the player has seen
 * — see {@link #applyDiscovery}. Games that do not ask pay nothing: no lids are
 * built and the loop never runs, which matters because an RTS map is many times
 * larger than a dungeon floor.
 */
final class TerrainScene {

    private static final ColorRGBA GROUND = new ColorRGBA(0.16f, 0.22f, 0.13f, 1f);
    private static final ColorRGBA ROCK = new ColorRGBA(0.25f, 0.23f, 0.20f, 1f);

    /**
     * Remembered ground and stone: the same colours turned well down.
     *
     * <p>Drawn as flat colours rather than a translucent veil over the lit scene.
     * A veil would need a blended material, and the whole scene is built from
     * opaque primitives handed a colour — so the memory is a darker world, not a
     * dimmer light on this one.
     */
    private static final ColorRGBA GROUND_REMEMBERED = new ColorRGBA(0.06f, 0.08f, 0.05f, 1f);
    private static final ColorRGBA ROCK_REMEMBERED = new ColorRGBA(0.10f, 0.09f, 0.08f, 1f);
    private static final ColorRGBA UNSEEN = new ColorRGBA(0f, 0f, 0f, 1f);

    /** Just clear of the ground, so the lid wins the depth test against it. */
    private static final float LID_HEIGHT = 0.08f;

    /** Fallback size for a game that never set a map. */
    private static final float DEFAULT_WIDTH = 700f;
    private static final float DEFAULT_HEIGHT = 450f;

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
    private Geometry[] lids = new Geometry[0];
    private int cellsWide;
    private Material unseenMaterial;
    private Material rememberedGround;
    private Material litRock;
    private Material rememberedRock;

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
            lids = new Geometry[0];
            cellsWide = 0;
            return;
        }
        float cell = grid.getCellSize();
        cellsWide = grid.getWidth();
        int cells = grid.getWidth() * grid.getHeight();
        rocks = discovery ? new Geometry[cells] : new Geometry[0];
        lids = discovery ? new Geometry[cells] : new Geometry[0];
        litRock = material.apply(ROCK);
        if (discovery) {
            unseenMaterial = material.apply(UNSEEN);
            rememberedGround = material.apply(GROUND_REMEMBERED);
            rememberedRock = material.apply(ROCK_REMEMBERED);
        }
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                if (grid.isBlocked(cx, cy)) {
                    var rock = new Geometry("rock", new Box(cell / 2f, 3f, cell / 2f));
                    rock.setMaterial(litRock);
                    rock.setLocalTranslation((cx + 0.5f) * cell, 3f, (cy + 0.5f) * cell);
                    root.attachChild(rock);
                    if (discovery) {
                        rocks[cy * cellsWide + cx] = rock;
                    }
                }
                if (discovery) {
                    lids[cy * cellsWide + cx] = lid(cx, cy, cell);
                }
            }
        }
    }

    /**
     * Lay the floor out of a modular kit.
     *
     * <p>No ground plane and no black covers, unlike the block version: with a kit
     * there is nothing under an unvisited cell to hide, so an undiscovered part of
     * the map is simply not built into the picture and the background shows
     * through. Black, which is what it should be.
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
     * A cell the player has never reached is not drawn at all; one he has walked
     * and left is drawn dimmer.
     *
     * <p>Per cell rather than per piece, which is what the node-per-cell is for: a
     * floor a hundred cells wide is a hundred calls a frame, not a thousand.
     */
    /**
     * Drawn at the brightness the fog has settled on rather than at one of three
     * shades, which is what turns a staircase of hard cells into an edge.
     *
     * <p>The cull is still all or nothing — a cell is built into the picture or it
     * is not — but it now happens at the point the light has actually run out
     * rather than at the first cell the hero has not stood in. What that buys is
     * a rim of nearly-black ground around the edge of the known world, which is
     * the difference between fog and a hole cut in the floor.
     */
    private void applyDiscoveryToTiles(Discovery seen) {
        for (int index = 0; index < cellNodes.length; index++) {
            var node = cellNodes[index];
            if (node == null) {
                continue; // stone; nothing was built here
            }
            float light = seen.lightAt(index % cellsWide, index / cellsWide);
            node.setCullHint(light <= Discovery.DARK
                    ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
            if (light > Discovery.DARK) {
                for (var piece : node.getChildren()) {
                    tiles.shade(piece, light);
                }
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
        };
    }

    private Node cellNode(int index) {
        if (cellNodes[index] == null) {
            cellNodes[index] = new Node("cell");
            root.attachChild(cellNodes[index]);
        }
        return cellNodes[index];
    }

    /** The cover over one cell: black while unseen, dim once remembered. */
    private Geometry lid(int cellX, int cellY, float cell) {
        var quad = new Geometry("fog", new Quad(cell, cell));
        quad.setMaterial(unseenMaterial);
        quad.rotate(-FastMath.HALF_PI, 0, 0);
        // Laid out like the ground quad, which spans upward from the z it sits at.
        quad.setLocalTranslation(cellX * cell, LID_HEIGHT, (cellY + 1) * cell);
        root.attachChild(quad);
        return quad;
    }

    /**
     * Draw the world as the player currently knows it.
     *
     * <p>Cheap enough to run every frame: nothing is created or destroyed, only
     * shown, hidden and recoloured, because the fog moves with the hero and a
     * rebuild per step would rebuild the floor several hundred times a walk.
     *
     * <p>Unseen stone is <em>hidden</em> rather than blacked out. A lid lies flat
     * on the ground and a rock stands six units above it, so painting the lid
     * black would leave the wall itself sticking up out of the dark — which would
     * hand the player the shape of a room they have not entered.
     */
    void applyDiscovery(Discovery seen) {
        if (!discovery || seen == null) {
            return;
        }
        if (tiled()) {
            applyDiscoveryToTiles(seen);
            return;
        }
        for (int index = 0; index < lids.length; index++) {
            var state = seen.stateAt(index % cellsWide, index / cellsWide);
            var lid = lids[index];
            if (lid != null) {
                lid.setCullHint(state == Discovery.State.VISIBLE
                        ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
                if (state == Discovery.State.REMEMBERED) {
                    lid.setMaterial(rememberedGround);
                } else if (state == Discovery.State.UNSEEN) {
                    lid.setMaterial(unseenMaterial);
                }
            }
            var rock = rocks[index];
            if (rock != null) {
                rock.setCullHint(state == Discovery.State.UNSEEN
                        ? Spatial.CullHint.Always : Spatial.CullHint.Inherit);
                rock.setMaterial(state == Discovery.State.VISIBLE ? litRock : rememberedRock);
            }
        }
    }
}
