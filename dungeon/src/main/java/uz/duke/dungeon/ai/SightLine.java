package uz.duke.dungeon.ai;

import uz.duke.core.math.Coord3D;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.World;

/**
 * Whether one thing can see another, or whether there is stone in the way.
 *
 * <p>The engine's own fog answers a different question — is it within so many
 * units — and answers it with a circle. That is right for an RTS, where a hill is
 * something you see over. In a dungeon it means an archer shooting through a wall
 * at something he has every reason not to know is there, which is the one thing a
 * dungeon must not allow: the whole game is not knowing what is round the corner.
 *
 * <p>The client already refuses to <em>draw</em> a room it cannot see into. This
 * is the same rule on the simulation's side, and it has to be a separate piece of
 * work: the two are deliberately kept apart, so what a player is shown can never
 * be what decides a fight.
 *
 * <p>Deterministic, and cheap enough to ask every frame: the line is walked in
 * whole cells by Bresenham, on integers, asking the world whether each one is
 * stone. No square roots, no trigonometry, and no new engine feature — the
 * pathfinder's grid is already there to be asked.
 *
 * <p>The far cell is allowed to be stone. A creature standing against a wall is
 * still a creature you can see; it is what lies <em>behind</em> the wall that is
 * hidden, so only the cells between the two are asked about.
 */
public final class SightLine {

    private SightLine() {
    }

    /** Whether {@code looker} has an unobstructed line to {@code at}. */
    public static boolean clear(GameObject looker, GameObject at) {
        var world = looker.getWorld();
        return world != null && clear(world, looker.getPosition(), at.getPosition());
    }

    /** The same, between two points. */
    public static boolean clear(World world, Coord3D from, Coord3D to) {
        int fromX = cell(from.x());
        int fromY = cell(from.y());
        int toX = cell(to.x());
        int toY = cell(to.y());

        int dx = Math.abs(toX - fromX);
        int dy = -Math.abs(toY - fromY);
        int stepX = fromX < toX ? 1 : -1;
        int stepY = fromY < toY ? 1 : -1;
        int error = dx + dy;
        int x = fromX;
        int y = fromY;
        while (x != toX || y != toY) {
            int doubled = 2 * error;
            if (doubled >= dy) {
                error += dy;
                x += stepX;
            }
            if (doubled <= dx) {
                error += dx;
                y += stepY;
            }
            if (x == toX && y == toY) {
                return true; // arrived; the far cell may be stone
            }
            if (world.isGroundBlocked(middleOf(x, y))) {
                return false;
            }
        }
        return true;
    }

    private static int cell(float world) {
        return (int) Math.floor(world / PathGrid.DEFAULT_CELL_SIZE);
    }

    /**
     * The middle of a cell, which is the point the world is asked about.
     *
     * <p>Its middle rather than its corner: a corner belongs to four cells, and
     * asking about one would make sight depend on which of the four the arithmetic
     * happened to land in.
     */
    private static Coord3D middleOf(int cellX, int cellY) {
        return new Coord3D(
                (cellX + 0.5f) * PathGrid.DEFAULT_CELL_SIZE,
                (cellY + 0.5f) * PathGrid.DEFAULT_CELL_SIZE, 0f);
    }
}
