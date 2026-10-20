package uz.dukeengine.client3d;

import com.jme3.bounding.BoundingBox;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Box;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import uz.dukeengine.client3d.KitPlan.Standing;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * The pieces of a kit laid where the plan says: its floor, walls, posts, lids, flights of steps and the faces of its
 * rock, each dressed the way the kit asks — scaled to the cell, turned, tinted by its storey — in the cell node it
 * stands in.
 */
final class KitPieces {

    /** How many blocks a built flight of steps is cut into. */
    private static final int BUILT_STEPS = 6;

    private final TileSource tiles;
    private final Surfaces material;
    private final Cells cells;
    /** How many storeys the map stands, which the shade of a flat piece hangs off — see {@link #tintFor}. */
    private int tallest;

    KitPieces(TileSource tiles, Surfaces material, Cells cells) {
        this.tiles = tiles;
        this.material = material;
        this.cells = cells;
    }

    /** Count the storeys of a whole plan, by its highest piece. */
    void countStoreys(List<Standing> plan, float storey) {
        tallest = 0;
        if (storey > 0f) {
            for (var standing : plan) {
                tallest = Math.max(tallest, Math.round(standing.ground() / storey));
            }
        }
    }

    /**
     * Count the storeys of {@code grid} as a whole build counts them — the highest floor's level, every piece's ground
     * being a floor's or no higher than the floors round it.
     */
    void countStoreys(PathGrid grid) {
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
    }

    /** {@code piece} turned {@code radians} about the vertical. */
    static void turn(Spatial piece, float radians) {
        piece.setLocalRotation(new Quaternion().fromAngleAxis(radians, Vector3f.UNIT_Y));
    }

    /** One piece of the plan laid in its cell: a flight of steps, a rock's face, or the kit's pieces for it. */
    void lay(PathGrid grid, Standing standing) {
        float cell = grid.getCellSize();
        float storey = grid.getLevelHeight();
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
        turn(piece, yaw);
        float back = -(box.getCenter().z + box.getZExtent()) * scale;
        piece.setLocalTranslation(
                placement.x() + back * FastMath.sin(yaw),
                placement.ground() + (box.getYExtent() - box.getCenter().y) * scale,
                placement.z() + back * FastMath.cos(yaw));
        cells.at(placement.cellX(), placement.cellY()).attachChild(piece);
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
        turn(piece, facing);
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
        cells.at(placement.cellX(), placement.cellY()).attachChild(piece);
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
        turn(piece, yaw);
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
        cells.at(placement.cellX(), placement.cellY()).attachChild(piece);
    }

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
        var stone = material.of(BlockGround.ROCK, null);
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
        turn(node, FastMath.DEG_TO_RAD * placement.yaw());
        node.setLocalTranslation(placement.x(), placement.ground(), placement.z());
        cells.at(placement.cellX(), placement.cellY()).attachChild(node);
    }

    private final Map<String, BoundingBox> tileBounds =
            new HashMap<>();
    private final Map<String, Vector3f> tileClimb = new HashMap<>();

    /** A piece's box as the model measures; null where its bound is of another sort. */
    private static BoundingBox measured(Spatial piece) {
        piece.updateModelBound();
        piece.updateGeometricState();
        return piece.getWorldBound() instanceof BoundingBox box ? box : null;
    }

    private BoundingBox boundsOf(String asset, Spatial piece) {
        return tileBounds.computeIfAbsent(asset, path -> {
            var box = measured(piece);
            return box != null ? (BoundingBox) box.clone()
                    : new BoundingBox(Vector3f.ZERO, 1f, 1f, 1f);
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

    private static List<Vector3f> verticesOf(Spatial piece) {
        var points = new ArrayList<Vector3f>();
        piece.depthFirstTraversal(spatial -> {
            if (!(spatial instanceof Geometry geometry)) {
                return;
            }
            var buffer = geometry.getMesh().getFloatBuffer(
                    VertexBuffer.Type.Position);
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
    private final Map<String, Float> tileTops = new HashMap<>();

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
            var box = measured(piece);
            return box != null ? box.getCenter().y + box.getYExtent() : 0f;
        });
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
            default -> List.<String>of();
        };
        if (choices.size() < 2) {
            return assetFor(standing.kit(), standing.piece());
        }
        return choices.get((int) (steady(standing.x(), standing.z(), copy, 4) * choices.size()));
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
}
