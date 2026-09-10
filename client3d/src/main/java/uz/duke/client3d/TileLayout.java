package uz.duke.client3d;

import java.util.ArrayList;
import java.util.List;
import uz.duke.core.pathfind.PathGrid;

/**
 * Which tile goes where, worked out from the map alone.
 *
 * <p>A modular kit does not think in the same shapes a pathfinding grid does. The
 * grid says a <em>cell</em> is stone; the kit has floor tiles and wall pieces that
 * stand on the <em>edge</em> of a tile. So a wall is not a solid cell — it is the
 * boundary between an open cell and a solid one, and that boundary is exactly
 * where the pathfinder stops the player. Drawing walls there rather than filling
 * solid cells with blocks is what makes the picture agree with the collision.
 *
 * <p>Pure on purpose: a grid in, a list of placements out, no jME and no
 * application. Where a tile ends up is the part that is easy to get wrong by half
 * a cell or a right angle, and this is where that can be held still.
 */
final class TileLayout {

    /** The kinds of piece a floor is built from. */
    enum Piece {
        FLOOR,
        WALL,
        CORNER,
        /**
         * A lid over the stone, level with the tops of the walls.
         *
         * <p>Stone is not drawn — it is only what the walls face — so the rock
         * behind a wall is an empty hole, and a camera looking across the floor
         * sees over the wall into it. The room reads as a house with the roof off.
         * A lid closes the hole, and then a wall is the near face of something
         * solid rather than a standing screen.
         *
         * <p><b>Every</b> piece of rock is roofed, not only the ring of it that
         * touches open ground. A one-cell ring is a lid on a wall rather than a
         * roof over a mass: the rock beyond it is a straight-edged hole in the
         * picture, and from a camera that looks across the map it reads as the
         * roof having been cut off in a line. What made the ring look like enough
         * was that a lid used to belong to the room beside it, and rock further in
         * had no room to belong to.
         */
        CAP
    }

    /**
     * One piece, placed. {@code yaw} turns it about the vertical axis, in degrees.
     *
     * <p>{@code cellX}/{@code cellY} is the cell this piece was built for, which is
     * not always the cell it stands in — a wall stands on the boundary and a corner
     * post in the stone. It is a grouping and nothing more: the renderer decides
     * what to draw from where a piece actually <em>stands</em>, not from what it
     * was filed under.
     */
    record Placement(Piece piece, int cellX, int cellY, float x, float z, float yaw) {
    }

    private TileLayout() {
    }

    /**
     * The four sides of a cell: the neighbour it leads to, and how far the wall on
     * that side is turned.
     *
     * <p>A wall piece faces along its own +Z and its body lies behind it, so the
     * turn is whatever puts the face on the boundary and the body in the stone.
     */
    private static final int[][] SIDES = {
        // dx, dy, yaw
        {0, -1, 0},     // north: body toward -z
        {1, 0, 270},    // east
        {0, 1, 180},    // south
        {-1, 0, 90},    // west
    };

    /**
     * The four corners of a cell, as the two sides that meet there and the turn
     * that puts the post's quarter-tile body into the stone outside them.
     */
    private static final int[][] CORNERS = {
        // cornerX (0|1), cornerY (0|1), yaw
        {0, 0, 0},
        {0, 1, 90},
        {1, 1, 180},
        {1, 0, 270},
    };

    /**
     * Every piece needed to draw {@code grid}.
     *
     * <p>Ordered by cell, then floor before walls before corners, so two runs of
     * the same map build the same scene in the same order.
     */
    static List<Placement> of(PathGrid grid) {
        var placements = new ArrayList<Placement>();
        if (grid == null) {
            return placements;
        }
        float cell = grid.getCellSize();
        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                if (solid(grid, cx, cy)) {
                    // Roofed, and roofed under itself. Stone has as good a place on
                    // the map as anything else does, and the fog is read at the
                    // place a piece stands rather than at the cell that owns it.
                    placements.add(new Placement(Piece.CAP, cx, cy,
                            (cx + 0.5f) * cell, (cy + 0.5f) * cell, 0f));
                    continue; // the stone itself is not drawn; it is what the walls face
                }
                placements.add(new Placement(Piece.FLOOR, cx, cy,
                        (cx + 0.5f) * cell, (cy + 0.5f) * cell, 0f));
                addWalls(placements, grid, cx, cy, cell);
                addCorners(placements, grid, cx, cy, cell);
            }
        }
        return placements;
    }

    private static void addWalls(List<Placement> into, PathGrid grid, int cx, int cy, float cell) {
        for (var side : SIDES) {
            if (!solid(grid, cx + side[0], cy + side[1])) {
                continue;
            }
            // Halfway to the neighbour: the face of the wall lands on the very
            // line the pathfinder will not let anyone cross.
            float x = (cx + 0.5f + side[0] * 0.5f) * cell;
            float z = (cy + 0.5f + side[1] * 0.5f) * cell;
            into.add(new Placement(Piece.WALL, cx, cy, x, z, side[2]));
        }
    }

    /**
     * A post where two walls meet at right angles.
     *
     * <p>Each wall covers half the depth of the cell beyond it, so two
     * perpendicular walls leave a square notch in the stone between their ends.
     * From inside the room that notch is a hole straight through the wall.
     */
    private static void addCorners(List<Placement> into, PathGrid grid, int cx, int cy, float cell) {
        for (var corner : CORNERS) {
            int dx = corner[0] == 0 ? -1 : 1;
            int dy = corner[1] == 0 ? -1 : 1;
            if (!solid(grid, cx + dx, cy) || !solid(grid, cx, cy + dy)) {
                continue; // only where both sides are walled does a notch exist
            }
            into.add(new Placement(Piece.CORNER, cx, cy,
                    (cx + corner[0]) * cell, (cy + corner[1]) * cell, corner[2]));
        }
    }

    /** Off the map counts as stone, so the outermost rooms are walled in. */
    private static boolean solid(PathGrid grid, int cx, int cy) {
        return cx < 0 || cy < 0 || cx >= grid.getWidth() || cy >= grid.getHeight()
                || grid.isBlocked(cx, cy);
    }
}
