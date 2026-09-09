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
         */
        CAP
    }

    /**
     * One piece, placed. {@code yaw} turns it about the vertical axis, in degrees.
     *
     * <p>{@code cellX}/{@code cellY} is the open cell this piece belongs to, which
     * is not always the cell it stands in — a wall stands on the boundary and a
     * corner post in the stone. It is recorded because fog is per cell: a wall is
     * remembered by the room it was seen from.
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
                    addCap(placements, grid, cx, cy, cell);
                    continue; // stone itself is not drawn; it is what the walls face
                }
                placements.add(new Placement(Piece.FLOOR, cx, cy,
                        (cx + 0.5f) * cell, (cy + 0.5f) * cell, 0f));
                addWalls(placements, grid, cx, cy, cell);
                addCorners(placements, grid, cx, cy, cell);
            }
        }
        return placements;
    }

    /**
     * The eight neighbours of a cell, in a fixed order — which is what decides
     * which room owns a lid when several look at the same rock.
     */
    private static final int[][] AROUND = {
        {0, -1}, {1, 0}, {0, 1}, {-1, 0}, {-1, -1}, {1, -1}, {1, 1}, {-1, 1},
    };

    /**
     * Roof this piece of stone, if anyone can see it.
     *
     * <p>Only rock that touches open ground is covered. Deeper stone is behind a
     * lid already and would be a few hundred more tiles for a view nobody has.
     *
     * <p>The lid is <em>owned</em> by the room next to it rather than by the rock
     * it covers, because fog is per cell and rock has no cell to be fogged: a lid
     * appears when the room that overlooks it has been walked, and is remembered
     * with it. Diagonals count as overlooking, or the rock in a corner would stay
     * open while the walls either side of it were roofed.
     *
     * <p>One lid, one owner — the first open neighbour in {@link #AROUND} — since
     * two rooms roofing the same rock would put two tiles in the same place and
     * leave them fighting over which is in front.
     */
    private static void addCap(List<Placement> into, PathGrid grid, int cx, int cy, float cell) {
        for (var around : AROUND) {
            int nx = cx + around[0];
            int ny = cy + around[1];
            if (inside(grid, nx, ny) && !solid(grid, nx, ny)) {
                into.add(new Placement(Piece.CAP, nx, ny,
                        (cx + 0.5f) * cell, (cy + 0.5f) * cell, 0f));
                return;
            }
        }
    }

    private static boolean inside(PathGrid grid, int cx, int cy) {
        return cx >= 0 && cy >= 0 && cx < grid.getWidth() && cy < grid.getHeight();
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
