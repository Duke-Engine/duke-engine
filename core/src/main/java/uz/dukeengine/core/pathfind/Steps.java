package uz.dukeengine.core.pathfind;

import uz.dukeengine.core.pathfind.Pathfinder.Traffic;

/**
 * A search's steps, as SAGE costs them ({@code PathfindCell::costSoFar}, {@code costToGoal}): which neighbours a cell
 * has and in which fixed order, which of them a body may step onto, and what a step and a turn cost — the one rule
 * every search here walks by, so a route found one way is a route found any other.
 */
final class Steps {

    static final int ORTHOGONAL_COST = 10;
    static final int DIAGONAL_COST = 14;

    // Fixed neighbour order: orthogonals first, then diagonals.
    static final int[][] NEIGHBOURS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private Steps() {
    }

    static boolean isDiagonal(int[] step) {
        return step[0] != 0 && step[1] != 0;
    }

    /**
     * Whether the search would take this step: room at the far end, a step the grid allows, and — for a diagonal — one
     * of its two sides open, as the reference's {@code examineNeighboringCells} asks.
     */
    static boolean canTake(PathGrid grid, int cx, int cy, int[] step, float clearance) {
        int nx = cx + step[0];
        int ny = cy + step[1];
        if (!Room.fits(grid, nx, ny, clearance) || !grid.canStep(cx, cy, nx, ny)) {
            return false;
        }
        return !isDiagonal(step) || !grid.isBlocked(cx + step[0], cy) || !grid.isBlocked(cx, cy + step[1]);
    }

    /** A step into {@code (nx, ny)}: its plain cost, times the cell's share where the grid costs its cells. */
    static int stepCost(PathGrid grid, boolean diagonal, int nx, int ny) {
        int plain = diagonal ? DIAGONAL_COST : ORTHOGONAL_COST;
        return grid.hasStepCosts() ? Math.max(1, plain * grid.stepPercent(nx, ny) / 100) : plain;
    }

    /**
     * What reaching the neighbour {@code step} from cell ({@code cx}, {@code cy}) costs, the cell reached at {@code
     * cost} from {@code parent}: the step, the turn, and what {@code traffic} adds; {@link Traffic#CLOSED} where the
     * traffic shuts the neighbour.
     */
    static int costVia(PathGrid grid, int cost, int parent, int cx, int cy, int[] step, Traffic traffic) {
        int nx = cx + step[0];
        int ny = cy + step[1];
        int tentative = cost + stepCost(grid, isDiagonal(step), nx, ny)
                + turnCost(grid, parent, cx, cy, step[0], step[1]);
        if (traffic == null) {
            return tentative;
        }
        int extra = traffic.costOf(nx, ny);
        return extra == Traffic.CLOSED ? Traffic.CLOSED : tentative + extra;
    }

    /**
     * What a change of direction adds to a step: the reference's 4 at 45 degrees, 8 at 90 and 16 at 135 ({@code
     * PathfindCell::costSoFar}); nothing going straight on, or on the first step.
     */
    static int turnCost(PathGrid grid, int parent, int cx, int cy, int dx, int dy) {
        if (parent < 0) {
            return 0;
        }
        int width = grid.getWidth();
        int wasX = cx - parent % width;
        int wasY = cy - parent / width;
        if (wasX == dx && wasY == dy) {
            return 0;
        }
        int dot = wasX * dx + wasY * dy;
        return dot > 0 ? 4 : dot == 0 ? 8 : 16;
    }

    /** The reference's estimate to the goal ({@code costToGoal}): 10 times the longer side and 5 times the shorter. */
    static int heuristic(int cx, int cy, int goalX, int goalY) {
        return estimate(Math.abs(cx - goalX), Math.abs(cy - goalY));
    }

    /** The same estimate across {@code dx} by {@code dy} cells, both at least 0. */
    static int estimate(int dx, int dy) {
        return ORTHOGONAL_COST * Math.max(dx, dy) + ORTHOGONAL_COST * Math.min(dx, dy) / 2;
    }

    /** How far apart two cells are, squared, which is enough to compare them. */
    static long away(int cx, int cy, int goalX, int goalY) {
        long dx = cx - goalX;
        long dy = cy - goalY;
        return dx * dx + dy * dy;
    }
}
