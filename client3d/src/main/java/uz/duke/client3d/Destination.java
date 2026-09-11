package uz.duke.client3d;

import uz.duke.core.math.Coord3D;
import uz.duke.core.pathfind.PathGrid;

/**
 * Where a move order really sends him, when the player points at somewhere he
 * cannot get to.
 *
 * <p>A click into stone, onto a ledge with no stair up to it, or into a room
 * that is sealed used to be an order that quietly did nothing: the search finds
 * no route, the hero stands where he is, and nothing on screen tells a bad click
 * from a broken game. Pointing is how this game is played, so a point that
 * cannot be obeyed exactly is obeyed as nearly as the map allows — he walks to
 * the closest place to it that he can stand, and stops there.
 *
 * <p>Decided here rather than in the simulation because the simulation is not
 * asked a question, it is given an order, and by the time the order arrives the
 * choice of where to send him has already been made. The client is also where
 * the same kind of question is already answered for aiming a skill — see
 * {@code isOpenAndSeen} — about the same map and for the same reason.
 *
 * <p><b>The map, not the moment.</b> Stone and the furniture standing on it — a
 * pillar, a barrel, a chest — but never a creature in the doorway. Bodies move;
 * shortening an order because something happened to be in the way at the instant
 * of the click would be a worse fault than the one this fixes, and a much harder
 * one to see. The two are already apart in the grid: its obstacle layer is baked
 * from things that cannot move, so nothing alive is ever in it.
 *
 * <p>Counting the furniture is what makes a click on a barrel an order at all.
 * The mover's own search refuses a goal cell it cannot enter and comes back with
 * no route, so pointing at a barrel was a click that did nothing — which is
 * exactly the fault this class was written to end, surviving in the one place
 * nobody thought to look for it.
 */
final class Destination {

    /**
     * Orthogonals first, then diagonals — the same fixed order the pathfinder
     * uses, so the flood spreads the same way on every machine and the cell it
     * settles on is not a matter of luck.
     */
    private static final int[][] NEIGHBOURS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private Destination() {
    }

    /**
     * The point an order from {@code from} to {@code wanted} should really name.
     *
     * <p>{@code wanted} itself whenever there is any way of walking there, so an
     * ordinary click is passed through untouched and keeps the exact spot the
     * player picked. Otherwise the middle of the reachable cell that lies closest
     * to it.
     *
     * <p>Found by flooding out from where he stands rather than by casting about
     * near the click, which is the only way to tell "behind a wall" from "on the
     * far side of the map with no stair" — both look equally open from the cell
     * the player pointed at.
     */
    static Coord3D asCloseAsHeCanGet(PathGrid grid, Coord3D from, Coord3D wanted) {
        if (grid == null) {
            return wanted;
        }
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        if (!grid.inBounds(startX, startY)) {
            return wanted; // off the map; nothing here can improve on the click
        }
        int goalX = grid.toCellX(wanted);
        int goalY = grid.toCellY(wanted);

        int width = grid.getWidth();
        var seen = new boolean[width * grid.getHeight()];
        var queue = new int[seen.length];
        int head = 0;
        int tail = 0;
        int start = startY * width + startX;
        seen[start] = true;
        queue[tail++] = start;

        // The cell he is standing on is always an answer, if a poor one: a hero
        // walled in on every side has nowhere better to go than where he is.
        int bestX = startX;
        int bestY = startY;
        long best = away(startX, startY, goalX, goalY);

        while (head < tail) {
            int cell = queue[head++];
            int cx = cell % width;
            int cy = cell / width;
            if (cx == goalX && cy == goalY) {
                return wanted;
            }
            long distance = away(cx, cy, goalX, goalY);
            if (distance < best) {
                best = distance;
                bestX = cx;
                bestY = cy;
            }
            for (var step : NEIGHBOURS) {
                int nx = cx + step[0];
                int ny = cy + step[1];
                if (!grid.inBounds(nx, ny) || seen[ny * width + nx]
                        || !canWalk(grid, cx, cy, nx, ny)) {
                    continue;
                }
                seen[ny * width + nx] = true;
                queue[tail++] = ny * width + nx;
            }
        }

        var reached = grid.cellCenter(bestX, bestY);
        return new Coord3D(reached.x(), reached.y(), wanted.z());
    }

    /** How far apart two cells are, squared, which is enough to compare them. */
    private static long away(int cx, int cy, int goalX, int goalY) {
        long dx = cx - goalX;
        long dy = cy - goalY;
        return dx * dx + dy * dy;
    }

    /**
     * Whether the map allows a step from one cell to the next.
     *
     * <p>{@link PathGrid#canStep} in every respect but one: it asks the grid,
     * which knows the map and what is bolted to it, rather than the world, which
     * also knows who is standing where this instant. The mover's own search asks
     * whether it can go there now; this asks whether it could ever, and the
     * difference between the two questions is exactly a monster in a corridor.
     *
     * <p>The corner rule and the level rule are the mover's, because a route this
     * says exists and the mover then refuses to walk would put the hero back to
     * standing still — which is the whole of what is being fixed.
     */
    private static boolean canWalk(PathGrid grid, int fromX, int fromY, int toX, int toY) {
        if (grid.isBlocked(toX, toY)) {
            return false;
        }
        boolean diagonal = fromX != toX && fromY != toY;
        if (diagonal && (grid.isBlocked(toX, fromY) || grid.isBlocked(fromX, toY))) {
            return false; // no cutting round the corner of a wall
        }
        int climb = grid.level(toX, toY) - grid.level(fromX, fromY);
        if (climb == 0) {
            return true;
        }
        if (climb > 1 || climb < -1 || diagonal) {
            return false;
        }
        return grid.isRamp(fromX, fromY) || grid.isRamp(toX, toY);
    }
}
