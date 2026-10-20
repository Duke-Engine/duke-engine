package uz.dukeengine.client3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.IntFunction;
import uz.dukeengine.core.pathfind.PathGrid;

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
final class KitPlan {

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
    record Standing(TileLayout.Piece piece, int cellX, int cellY, float x, float z,
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

    /** The kit the map is drawn from, where a cell's look names none. */
    private final Tileset tileset;
    /**
     * The kit each cell wears where the map names looks of its own, by its index ({@code cy * width + cx}), null for
     * none — a cell with none, or with a kit that cannot build a floor, wearing the map's — and null for a map that
     * names no looks.
     */
    private final IntFunction<Tileset> cellKits;
    /** How many cells the map laid has, which a cell's index is under. */
    private final int cellCount;

    KitPlan(Tileset tileset, IntFunction<Tileset> cellKits, int cellCount) {
        this.tileset = tileset;
        this.cellKits = cellKits;
        this.cellCount = cellCount;
    }

    /** What to draw for a layout: its pieces, as the kit of each cell asks them drawn. */
    List<Standing> of(List<TileLayout.Placement> layout,
            PathGrid grid) {
        var plan = new ArrayList<Standing>();
        float cell = grid.getCellSize();
        // Per piece of rock: the turn and cell of whichever face was met first — a
        // body has no facing of its own, but the cell it is filed under decides
        // when the fog hides it. Where it stands is the rock's, not the faces'.
        var bodies = new LinkedHashMap<Integer, float[]>();
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
        if (cellKits != null && cell >= 0 && cell < cellCount) {
            var kit = cellKits.apply(cell);
            if (kit != null && kit.isUsable()) {
                return kit;
            }
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

    /** The tallest floor touching a cell, so rock and lids rise with the rooms. */
    static float highestFloorAround(PathGrid grid, int cx, int cy) {
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
}
