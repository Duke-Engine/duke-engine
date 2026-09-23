package uz.dukeengine.client3d;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.Pathfinder;

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
 * <p>The simulation now does the same for any mover it is sent somewhere it
 * cannot reach, by the same flood ({@link Pathfinder#nearestReachable}); it is
 * asked here as well because a group's formation is spread round the point, and
 * spread round the place they will really get to it holds together.
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
        if (grid == null || !grid.inBounds(grid.toCellX(from), grid.toCellY(from))) {
            return wanted; // no map, or off it: nothing here can improve on the click
        }
        return Pathfinder.nearestReachable(grid, from, wanted);
    }
}
