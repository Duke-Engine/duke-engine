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
 * <p>Kept apart, but <b>not allowed to disagree</b> — {@link #sees} asks the three
 * questions the client's fog asks and gets the same three answers. That is worth
 * saying plainly because it was not true, twice over: the hero's bow was kept
 * shorter than his eyes so that the distance could go unchecked, and when floors
 * grew storeys nothing here learned about them, so he shot at monsters standing on
 * a raised room that the player was never shown.
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

    /**
     * Whether {@code looker} can see {@code at} — which is a different and larger
     * question than whether there is stone between them.
     *
     * <p>Three things, and they are the three the client's own fog asks, because
     * the point of this is that the two agree. Something a player is not shown is
     * something the hero must not shoot at, and a rule that held for only two of
     * the three would leave him firing into a dark the player is staring at.
     *
     * <ul>
     *   <li><b>Near enough.</b> His eyes reach as far as his template's
     *       {@code VisionRange} and no further, and it is the <em>cell</em> that is
     *       lit or not — the same cell the client lights or leaves black. This is
     *       what frees his bow: a weapon may now outrange his eyes, because what
     *       stops the shot is the dark rather than a second number kept politely
     *       below the first.
     *   <li><b>Nothing between.</b> Stone, as before.
     *   <li><b>Nothing standing higher.</b> A floor above his own is behind its own
     *       edge: from the corridor beneath it you cannot see onto it, and the
     *       client draws it as remembered stone with whoever is up there left out.
     *       Without this he shot at monsters standing on a raised room — visible to
     *       the simulation, drawn nowhere.
     * </ul>
     *
     * @param storeyHeight how far apart two storeys are; zero for a flat game,
     *     and then height never hides anything
     */
    public static boolean sees(GameObject looker, GameObject at, float storeyHeight) {
        var world = looker.getWorld();
        return world != null && sees(world, looker.getPosition(), at.getPosition(),
                looker.getVisionRange(), storeyHeight);
    }

    /** The same, between two points, for eyes that reach {@code radius}. */
    public static boolean sees(World world, Coord3D from, Coord3D to, float radius,
            float storeyHeight) {
        if (radius <= 0f) {
            return false; // no eyes at all: a thing that sees nothing shoots nothing
        }
        var lit = middleOf(cell(to.x()), cell(to.y()));
        float dx = lit.x() - from.x();
        float dy = lit.y() - from.y();
        if (dx * dx + dy * dy > radius * radius) {
            return false;
        }
        int eyes = storeyOf(world, from, storeyHeight);
        return storeyOf(world, lit, storeyHeight) <= eyes
                && walk(world, from, to, eyes, storeyHeight);
    }

    /** Whether {@code looker} has an unobstructed line to {@code at}. */
    public static boolean clear(GameObject looker, GameObject at) {
        var world = looker.getWorld();
        return world != null && clear(world, looker.getPosition(), at.getPosition());
    }

    /** The same, between two points — stone only, with no opinion about height. */
    public static boolean clear(World world, Coord3D from, Coord3D to) {
        return walk(world, from, to, 0, 0f);
    }

    /**
     * Every cell between the two, asked whether it stops the line.
     *
     * <p>{@code storeyHeight} of zero switches the height question off, which is
     * what leaves {@link #clear} asking only about stone.
     */
    private static boolean walk(World world, Coord3D from, Coord3D to, int eyes,
            float storeyHeight) {
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
            var middle = middleOf(x, y);
            if (world.isGroundBlocked(middle)) {
                return false;
            }
            if (storeyHeight > 0f && storeyOf(world, middle, storeyHeight) > eyes) {
                return false;
            }
        }
        return true;
    }

    /**
     * Which storey the ground at a point belongs to.
     *
     * <p>Rounded <em>down</em>, and that is the whole of what makes a staircase
     * something you can see up. A stair cell's floor climbs across it, so the
     * middle of one reads half a storey up — taken to the nearest it would be the
     * upper storey and the stair would hide itself, which is not what the client
     * does and not what anybody sees. A stair belongs to the floor it starts from.
     */
    private static int storeyOf(World world, Coord3D at, float storeyHeight) {
        if (storeyHeight <= 0f) {
            return 0;
        }
        return (int) Math.floor(world.groundHeight(at) / storeyHeight + 0.001f);
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
