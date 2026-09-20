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
import java.util.function.Function;
import uz.dukeengine.core.math.Coord3D;
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
    }

    /** The tallest floor touching a cell, so rock and lids rise with the rooms. */
    private static float highestFloorAround(PathGrid grid, int cx, int cy) {
        float highest = 0f;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.inBounds(cx + dx, cy + dy) && !grid.isBlocked(cx + dx, cy + dy)) {
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
        cellNodes = new Node[grid.getWidth() * grid.getHeight()];
        float cell = grid.getCellSize();
        float floorScale = cell / tileset.getTileSize();
        // Walls may have been modelled on a different module from the floors, and
        // then they have their own scale — see Tileset.wallTileSize.
        float wallScale = cell / tileset.getWallTileSize();

        float storey = grid.getLevelHeight();
        var plan = plan(TileLayout.of(grid), grid);
        // How many storeys this map actually has, so the shading can hang off the
        // top of it — see storeyShade.
        int tallest = 0;
        if (storey > 0f) {
            for (var standing : plan) {
                tallest = Math.max(tallest, Math.round(standing.ground() / storey));
            }
        }
        for (var standing : plan) {
            if (standing.piece() == TileLayout.Piece.STAIR) {
                addStair(grid, standing, cell);
                continue;
            }
            String asset = assetFor(standing.piece());
            if (asset == null) {
                continue; // a kit without corner posts is a kit with square notches
            }
            if (standing.piece() == TileLayout.Piece.ROCK_FACE) {
                addRockFace(asset, standing, storey);
                continue;
            }
            int clump = standing.upright() ? tileset.getWallClump() : 1;
            for (int copy = 0; copy < clump; copy++) {
                addKitPiece(asset, standing, copy, clump,
                        standing.upright() ? wallScale : floorScale, wallScale, cell,
                        tintFor(standing.piece(), standing.ground(), storey, tallest));
            }
        }
        drape(grid);
    }

    /**
     * Every piece bent over the map's relief: each corner of its mesh raised by the relief under it, so a floor
     * rises and falls with the ground its walkers stand on and a wall follows the ground along its foot. The layout
     * is the storeys', and this the one place the relief enters the picture.
     *
     * <p>Only where a map has relief. A mesh is shared by every piece cut from one model, so a bent piece bends a
     * copy of its own.
     */
    private void drape(PathGrid grid) {
        if (grid.getRelief() == null) {
            return;
        }
        root.updateGeometricState();
        var local = new Vector3f();
        var world = new Vector3f();
        root.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry)) {
                return;
            }
            var mesh = geometry.getMesh().deepClone();
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
    private int tintFor(TileLayout.Piece piece, float ground, float storey, int tallest) {
        float shade = storey <= 0f ? 1f
                : (float) Math.pow(tileset.getStoreyShade(),
                        Math.round(ground / storey) - tallest);
        return switch (piece) {
            case CAP -> shaded(tileset.getCapTint(), shade);
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
        var piece = tiles.piece(asset);
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
     */
    private record Standing(TileLayout.Piece piece, int cellX, int cellY, float x, float z,
            float yaw, float ground, int storeys, boolean inRock) {

        static Standing facing(TileLayout.Placement of, int storeys) {
            return new Standing(of.piece(), of.cellX(), of.cellY(), of.x(), of.z(), of.yaw(),
                    of.ground(), storeys, false);
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
        if (!tileset.wallFillsRock()) {
            for (var placement : layout) {
                plan.add(Standing.facing(placement, 1));
            }
            return plan;
        }
        float cell = grid.getCellSize();
        float proud = tileset.getWallHeight() * (cell / tileset.getWallTileSize());
        // Per piece of rock: the turn and cell of whichever face was met first — a
        // body has no facing of its own, but the cell it is filed under decides
        // when the fog hides it. Where it stands is the rock's, not the faces'.
        var bodies = new java.util.LinkedHashMap<Integer, float[]>();
        for (var placement : layout) {
            var standing = Standing.facing(placement, 1);
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
            if (topOfTheRockAt(placement, grid, cell) + proud > placement.ground() + 0.001f) {
                plan.add(new Standing(TileLayout.Piece.ROCK_FACE, placement.cellX(),
                        placement.cellY(), placement.x(), placement.z(), placement.yaw(),
                        placement.ground(), 1, false));
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
                    found[0], highestFloorAround(grid, rockX, rockY), 1, true));
        }
        return plan;
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
        return grid.inBounds(nx, ny) && grid.isBlocked(nx, ny) ? ny * grid.getWidth() + nx : -1;
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
        var piece = tiles.piece(asset, tint);
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
            float radius = tileset.getWallSpread() * cell
                    * (0.55f + 0.45f * steady(placement.x(), placement.z(), copy, 1));
            side = radius * FastMath.sin(turn);
            // A body scatters about its own middle and stays in its cell. A face
            // scatters behind its line by the ring's own radius, so what leans out
            // over open ground is canopy and the trunk keeps to the solid side.
            ring = radius * (placement.inRock() ? FastMath.cos(turn)
                    : -(1f + FastMath.cos(turn)));
            facing = FastMath.TWO_PI * steady(placement.x(), placement.z(), copy, 2);
        }
        if (tileset.getWallVariety() > 0f && placement.upright()) {
            scale *= 1f + tileset.getWallVariety()
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
            case CAP -> tileset.getWallHeight() * wallScale;
            case LEDGE -> (tileset.getWallHeight() + tileset.getWallLift()) * wallScale;
            case WALL, CORNER -> tileset.getWallLift() * wallScale;
            default -> -surface;
        };
        // A wall's face belongs on the boundary, and where its own kit put its
        // origin decides how far back that is. Along the wall's own facing,
        // which the yaw has just turned.
        //
        // A body standing in the rock is not on a boundary and takes none of it:
        // the shift exists to move a face off a line, and there is no line.
        float back = (standing && !placement.inRock()
                ? tileset.getWallShift() * wallScale : 0f) + ring;
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
        var stone = material.apply(ROCK);
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

    private String assetFor(TileLayout.Piece piece) {
        return switch (piece) {
            case FLOOR -> tileset.getFloor();
            case WALL -> tileset.getWall();
            case CORNER -> tileset.getCorner();
            // A floor tile, laid on top of the rock rather than under the room --
            // and only where there are walls to roof. A kit with no walls has
            // nothing to see over, and its lids would float above bare ground.
            case CAP -> tileset.getWall() == null ? null : tileset.getFloor();
            case LEDGE -> tileset.getWall();
            case STAIR -> tileset.getStairs();
            // Only a kit whose wall is a thing rather than a surface names one.
            // Masonry fills a raised block of rock with its own courses.
            case ROCK_FACE -> tileset.getRockFace();
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
