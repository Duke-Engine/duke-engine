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
        CAP,
        /**
         * A flight of steps from one storey to the next.
         *
         * <p>Placed on the cell the grid marks as a ramp, turned so that it climbs
         * toward the higher of its neighbours. It is the only piece whose whole
         * job is height, and the one place a player is told, without a word, that
         * this map has more than one floor to it.
         */
        STAIR
    }

    /**
     * One piece, placed. {@code yaw} turns it about the vertical axis, in degrees.
     *
     * <p>{@code cellX}/{@code cellY} is the cell this piece was built for, which is
     * not always the cell it stands in — a wall stands on the boundary and a corner
     * post in the stone. It is a grouping and nothing more: the renderer decides
     * what to draw from where a piece actually <em>stands</em>, not from what it
     * was filed under.
     *
     * <p>{@code ground} is the height of the floor this piece belongs to, which is
     * not always the height of its own cell either: a wall holding up a raised
     * room stands on the lower floor beside it, and the lid over a piece of rock
     * sits level with the tallest floor around it. What the renderer adds on top
     * — how thick a tile is, how far up a kit's wall has to be nudged — is the
     * kit's business and stays there.
     */
    record Placement(Piece piece, int cellX, int cellY, float x, float z, float yaw,
            float ground) {
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
                            (cx + 0.5f) * cell, (cy + 0.5f) * cell, 0f,
                            highestFloorAround(grid, cx, cy)));
                    continue; // the stone itself is not drawn; it is what the walls face
                }
                float ground = grid.groundHeight(cx, cy);
                placements.add(new Placement(Piece.FLOOR, cx, cy,
                        (cx + 0.5f) * cell, (cy + 0.5f) * cell, 0f, ground));
                addWalls(placements, grid, cx, cy, cell);
                addCorners(placements, grid, cx, cy, cell);
                addStair(placements, grid, cx, cy, cell, ground);
            }
        }
        return placements;
    }

    /**
     * Walls, wherever a body cannot cross — which is not the same as wherever
     * there is stone.
     *
     * <p>A room standing a storey above the corridor beside it has an edge that
     * nobody may step over, and nothing there to say so: open floor, then open
     * floor, and a drop between them that the eye has no way to see. So the rule
     * is the pathfinder's own rule. Where {@code canStep} says no, something is
     * drawn — stone or no stone — and the room reads as being held up rather than
     * as floating.
     *
     * <p>The wall belongs to the higher of the two, and there is one for each
     * storey of the drop, stacked from the lower floor up.
     *
     * <p><b>And it faces the other way from a wall against stone.</b> A wall
     * piece has a front and a back, and its back is not drawn at all — the face
     * looks out of the solid side toward whoever can see it. Against stone the
     * solid side is the neighbour, so the face looks back into this room. Holding
     * up a raised floor the solid side is <em>this</em> cell, and whoever is
     * looking stands below in the corridor — so the same yaw would turn its face
     * into the earth and leave the drop showing as a black gap with nothing in it.
     */
    private static void addWalls(List<Placement> into, PathGrid grid, int cx, int cy, float cell) {
        for (var side : SIDES) {
            int nx = cx + side[0];
            int ny = cy + side[1];
            // Halfway to the neighbour: the face of the wall lands on the very
            // line the pathfinder will not let anyone cross.
            float x = (cx + 0.5f + side[0] * 0.5f) * cell;
            float z = (cy + 0.5f + side[1] * 0.5f) * cell;

            if (solid(grid, nx, ny)) {
                into.add(new Placement(Piece.WALL, cx, cy, x, z, side[2],
                        grid.groundHeight(cx, cy)));
                continue;
            }
            if (grid.canStep(cx, cy, nx, ny)) {
                continue; // open ground to open ground: nothing stands between them
            }
            float here = grid.groundHeight(cx, cy);
            float there = grid.groundHeight(nx, ny);
            float storey = grid.getLevelHeight();
            if (here <= there || storey <= 0f) {
                continue; // the higher of the two puts up the wall
            }
            float outward = (side[2] + 180f) % 360f;
            for (float foot = there; foot < here - storey * 0.5f; foot += storey) {
                into.add(new Placement(Piece.WALL, cx, cy, x, z, outward, foot));
            }
        }
    }

    /** A flight of steps on a ramp cell, turned toward whatever it climbs to. */
    private static void addStair(List<Placement> into, PathGrid grid, int cx, int cy, float cell,
            float ground) {
        // Asked of the grid rather than worked out again here: the height read
        // under a body climbing the stair comes from the same answer, and a
        // staircase drawn one way with a slope running the other is a body
        // walking up thin air beside its own steps.
        var rise = grid.rampDirection(cx, cy);
        if (rise == null) {
            return;
        }
        into.add(new Placement(Piece.STAIR, cx, cy,
                (cx + 0.5f) * cell, (cy + 0.5f) * cell, climbYaw(rise[0], rise[1]), ground));
    }

    /**
     * The turn that points a piece's own {@code +z} at a neighbour.
     *
     * <p>jME turns {@code +z} toward {@code (sin yaw, 0, cos yaw)}, and the grid's
     * y is the world's z, so this is the compass bearing of a step from one cell
     * to the next.
     */
    private static float climbYaw(int dx, int dy) {
        if (dx > 0) {
            return 90f;
        }
        if (dx < 0) {
            return 270f;
        }
        return dy > 0 ? 0f : 180f;
    }

    /**
     * How high the tallest floor touching a piece of rock stands.
     *
     * <p>The lid over the rock sits level with the tops of the walls around it, so
     * beside a raised room it has to rise with the room — otherwise the room's
     * own retaining wall stands proud of the rock behind it and the player is
     * looking over the top of the map.
     */
    private static float highestFloorAround(PathGrid grid, int cx, int cy) {
        float highest = 0f;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (!solid(grid, cx + dx, cy + dy)) {
                    highest = Math.max(highest, grid.groundHeight(cx + dx, cy + dy));
                }
            }
        }
        return highest;
    }

    /**
     * A post where two walls meet at right angles.
     *
     * <p>Each wall covers half the depth of the cell beyond it, so two
     * perpendicular walls leave a square notch in the stone between their ends.
     * From inside the room that notch is a hole straight through the wall.
     *
     * <p>A raised floor has the same corners and the same notch, so the rule is
     * the same one the walls use — wherever a body may not cross — and the post
     * is stacked down to whichever of the two neighbours lies lowest.
     */
    private static void addCorners(List<Placement> into, PathGrid grid, int cx, int cy, float cell) {
        for (var corner : CORNERS) {
            int dx = corner[0] == 0 ? -1 : 1;
            int dy = corner[1] == 0 ? -1 : 1;
            if (!walled(grid, cx, cy, cx + dx, cy) || !walled(grid, cx, cy, cx, cy + dy)) {
                continue; // only where both sides are walled does a notch exist
            }
            float here = grid.groundHeight(cx, cy);
            float storey = grid.getLevelHeight();
            float foot = Math.min(openGround(grid, cx + dx, cy, here),
                    openGround(grid, cx, cy + dy, here));
            for (float y = foot; y < here + 0.001f; y += Math.max(storey, 1f)) {
                into.add(new Placement(Piece.CORNER, cx, cy,
                        (cx + corner[0]) * cell, (cy + corner[1]) * cell, corner[2], y));
                if (storey <= 0f) {
                    break; // a flat map has one post and no stack
                }
            }
        }
    }

    /** Whether something has to stand between these two cells: stone, or a drop. */
    private static boolean walled(PathGrid grid, int cx, int cy, int nx, int ny) {
        return solid(grid, nx, ny) || !grid.canStep(cx, cy, nx, ny);
    }

    /** The floor of a neighbour, or {@code ifStone} where there is no floor to stand on. */
    private static float openGround(PathGrid grid, int cx, int cy, float ifStone) {
        return solid(grid, cx, cy) ? ifStone : grid.groundHeight(cx, cy);
    }

    /** Off the map counts as stone, so the outermost rooms are walled in. */
    private static boolean solid(PathGrid grid, int cx, int cy) {
        return cx < 0 || cy < 0 || cx >= grid.getWidth() || cy >= grid.getHeight()
                || grid.isBlocked(cx, cy);
    }
}
