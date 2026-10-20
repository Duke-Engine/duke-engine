package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.Pathfinder.Tally;
import uz.dukeengine.core.pathfind.Pathfinder.Traffic;
import uz.dukeengine.core.pathfind.Pathfinder.Within;

/**
 * A route to a goal, or as near it as there is one — the reference's {@code Pathfinder::findClosestPath}: a goal out
 * of the mover's zone gone for at the nearest cell of its zone, a goal its width cannot stand on come at from the
 * nearest cell with room, and a route with room tried before one that squeezes. See {@link
 * Pathfinder#findPathOrNearest(PathGrid, Coord3D, Coord3D, float)}.
 */
final class NearestRoute {

    /** A search from one point to another: over the whole grid, or kept to the sectors a way goes through. */
    @FunctionalInterface
    interface Searcher {
        Path search(PathGrid grid, Coord3D from, Coord3D to, float clearance, boolean orNearest, Tally tally,
                Traffic traffic);
    }

    private NearestRoute() {
    }

    /**
     * A route by {@code searcher}, {@code flatZones} and {@code squeezer} the zones and the search of a body of no
     * width where {@code zones} are a width's: a goal those say is shut off and these say is not is squeezed to at
     * once. Either may be null, and {@code zones} too, to search without them.
     */
    static Path find(PathGrid grid, Coord3D from, Coord3D to, float clearance, Zones zones, Tally tally,
            Traffic traffic, Searcher searcher, Zones flatZones, Searcher squeezer) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        if (grid.isBlocked(startX, startY)) {
            return Room.escape(grid, from);
        }
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);
        int width = grid.getWidth();
        int zoneX = startX;
        int zoneY = startY;
        if (zones != null && clearance > 0f && zones.zoneOf(startX, startY) < 0) {
            // Zones laid for its width (Sectors.of with a clearance) hold no cell it does not fit at: read from beside it.
            int room = Room.nearestWithRoom(grid, startX, startY, clearance, null, -1);
            if (room >= 0) {
                zoneX = room % width;
                zoneY = room / width;
            }
        }
        int zone = zones == null ? -1 : zones.zoneOf(zoneX, zoneY);
        boolean shutOff = zone >= 0 && !zones.connected(zoneX, zoneY, goalX, goalY);
        if (shutOff && flatZones != null && squeezer != null && flatZones.connected(startX, startY, goalX, goalY)) {
            // Shut off from its width alone — a gate narrower than it: it squeezes through, as it always did.
            var squeezed = squeezer.search(grid, from, to, Room.squeezed(grid, from, clearance), true, tally, traffic);
            if (squeezed.reachesGoal()) {
                return squeezed;
            }
        }
        if (shutOff) {
            return toNearestOfZone(grid, from, clearance, zones, zone, zoneX, zoneY, goalX, goalY, tally, traffic,
                    searcher);
        }
        if (clearance > 0f && !Room.fits(grid, goalX, goalY, clearance)) {
            return intoTheTightSpot(grid, from, to, clearance, tally, traffic, searcher);
        }
        var path = searcher.search(grid, from, to, clearance, true, tally, traffic);
        if (path.reachesGoal() || clearance <= 0f) {
            return path;
        }
        return roomOrSqueezed(grid, from, to, clearance, tally, traffic, searcher, path);
    }

    /**
     * A goal out of the mover's zone: a route to the cell of its zone nearest it, with room there where there is any
     * near — as near to it as the movers on the ground let it come: where they shut that cell or every way to it — a
     * still enemy on a gate's threshold — the nearest cell the search reached, as a goal it can reach is gone for;
     * with room first, squeezing where that gets nearer.
     */
    private static Path toNearestOfZone(PathGrid grid, Coord3D from, float clearance, Zones zones, int zone,
            int zoneX, int zoneY, int goalX, int goalY, Tally tally, Traffic traffic, Searcher searcher) {
        int width = grid.getWidth();
        int nearest = zones.nearestIn(zone, goalX, goalY, zoneX, zoneY);
        if (nearest == grid.toCellY(from) * width + grid.toCellX(from) || nearest == zoneY * width + zoneX) {
            return Path.partial(List.of()); // nowhere nearer than where it stands
        }
        if (clearance > 0f && !Room.fits(grid, nearest % width, nearest / width, clearance)) {
            int room = Room.nearestWithRoom(grid, nearest % width, nearest / width, clearance, zones, zone);
            nearest = room >= 0 ? room : nearest; // no room near it: it squeezes there, as before
        }
        var there = grid.cellCenter(nearest);
        var way = searcher.search(grid, from, there, clearance, true, tally, traffic);
        if (!way.reachesGoal() && clearance > 0f) {
            way = roomOrSqueezed(grid, from, there, clearance, tally, traffic, searcher, way);
        }
        return Path.partial(way.getWaypoints());
    }

    /**
     * {@code withRoom}, a route at the mover's width that did not reach {@code to}, or one squeezed there: the squeezed
     * one where it reaches, or stops nearer — room winning a tie.
     */
    private static Path roomOrSqueezed(PathGrid grid, Coord3D from, Coord3D to, float clearance, Tally tally,
            Traffic traffic, Searcher searcher, Path withRoom) {
        var squeezed = searcher.search(grid, from, to, Room.squeezed(grid, from, clearance), true, tally, traffic);
        return squeezed.reachesGoal() || awayFrom(squeezed, from, to) < awayFrom(withRoom, from, to)
                ? squeezed : withRoom;
    }

    /**
     * A goal the mover's width cannot stand on: searched for once at its width, the route ending at the first cell
     * with room near the goal — as far as the body is wide and two cells more — from which a body with no width walks
     * straight onto it, and then on onto the goal itself, squeezed; so it comes in the way its route came, as it would
     * have squeezing the whole way. The reference moves such a goal to a cell the mover can stand on before its one
     * search ({@code adjustDestination}, {@code checkDestination}); a search for the goal itself at the mover's width
     * would walk every cell it can reach before it knew. With no such cell it can walk to with room, it squeezes the
     * whole way, as it always could.
     */
    private static Path intoTheTightSpot(PathGrid grid, Coord3D from, Coord3D to, float clearance, Tally tally,
            Traffic traffic, Searcher searcher) {
        float near = clearance + 2f * grid.getCellSize();
        if (Lines.across(from, to) <= near && Lines.isClearLine(grid, from, to, 0f, null)) {
            return new Path(List.of(to));
        }
        Within room = at -> {
            float off = Lines.across(at, to);
            return off > near ? off - near : Lines.isClearLine(grid, at, to, 0f, null) ? 0f : grid.getCellSize();
        };
        var way = BandSearch.findPathWithin(grid, from, to, clearance, room, tally, traffic);
        if (way.reachesGoal() && !way.isEmpty()) {
            var points = new ArrayList<Coord3D>(way.getWaypoints());
            points.add(to);
            return new Path(points);
        }
        return searcher.search(grid, from, to, Room.squeezed(grid, from, clearance), true, tally, traffic);
    }

    /** How far from {@code to} a route leaves its mover, across the ground, squared. */
    static float awayFrom(Path path, Coord3D from, Coord3D to) {
        var end = path.isEmpty() ? from : path.getDestination();
        float dx = end.x() - to.x();
        float dy = end.y() - to.y();
        return dx * dx + dy * dy;
    }
}
