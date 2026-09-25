package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import uz.dukeengine.core.math.Coord3D;

/**
 * A* search over a {@link PathGrid}, ported from SAGE's {@code Pathfinder}.
 *
 * <p>Finds a short cell path between two world positions over an 8-connected
 * grid, costed as SAGE costs it ({@code PathfindCell::costSoFar}, {@code
 * costToGoal}): a step 10, a diagonal 14, and a change of direction 4 more at 45
 * degrees, 8 at 90 and 16 at 135, so of two routes of one length the one that
 * turns fewer times is taken; the estimate to the goal 10 times the longer side
 * and 5 times the shorter; a diagonal step taken past one open side of it, not
 * only between two. It returns the route as world-space {@link Path} waypoints.
 *
 * <p>Two things happen after the search, and both matter more than the search
 * itself does to how movement looks.
 *
 * <p><b>The route is pulled straight.</b> A grid search can only answer in cells,
 * so its waypoints are cell centres and a walk across open floor comes out as a
 * staircase — right, diagonal, right, diagonal — even where a straight line was
 * free the whole way. So once the cells are known, the corners are pulled out of
 * them: keep only the waypoints you cannot see past. On open ground that leaves
 * exactly one, and the unit walks straight there.
 *
 * <p><b>The route respects the mover's width.</b> Cells are points to a search
 * and units are not. A path that grazes a corner is fine for a point and wrong
 * for a body, and the body then discovers it by colliding, which comes out as a
 * unit arcing around things for no visible reason. Given a {@code clearance} the
 * search and the straightening both keep that much room from stone.
 *
 * <p>Determinism is essential for lock-step: the open set is ordered by
 * {@code f = g + h} and ties are broken by cell index, neighbours are always
 * visited in the same fixed order, costs are integers, and the straightening
 * samples at a fixed fraction of a cell — so the same grid and endpoints always
 * yield the same path on every machine.
 */
public final class Pathfinder {

    private static final int ORTHOGONAL_COST = 10;
    private static final int DIAGONAL_COST = 14;

    /**
     * What the movers on the ground add to a step onto a cell for the mover a route is sought for: a cost, or {@link
     * #CLOSED} for a cell it may not step onto at all — see {@code GameLogic}. Asked of every cell a search reaches.
     */
    @FunctionalInterface
    public interface Traffic {
        int CLOSED = -1;

        int costOf(int cx, int cy);

        /**
         * Whether an ally stands still on the cell: dearer to the search, and a line pulled straight goes round it as
         * the route did rather than through it.
         */
        default boolean allyStill(int cx, int cy) {
            return false;
        }
    }

    /**
     * The places a route may end at short of the thing it heads for — within a band of distance of it, as a route to
     * fight ends at the first place from which a weapon reaches ({@link #findPathWithin}).
     */
    @FunctionalInterface
    public interface Within {
        /** How far the place {@code at} lies outside the band: 0 within it. */
        float outside(Coord3D at);
    }

    /**
     * The most cells a route into a band examines before it gives up: the reference's {@code ATTACK_CELL_LIMIT}, "a
     * rather expensive operation, so limit the search".
     */
    public static final int BAND_CELL_LIMIT = 2500;

    /** How finely a straight line is sampled when testing whether it is clear. */
    private static final float LINE_SAMPLE_FRACTION = 0.25f;

    // Fixed neighbour order: orthogonals first, then diagonals.
    private static final int[][] NEIGHBOURS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private Pathfinder() {
    }

    /**
     * The cells searches examined, added up — what a world holds a frame's searching to, as the reference holds
     * it to {@code PATHFIND_CELLS_PER_FRAME}.
     */
    public static final class Tally {
        private int cells;

        /** How many cells the searches it was handed examined. */
        public int cells() {
            return cells;
        }
    }

    /**
     * The cells a route from {@code from} to {@code to} goes through, in order, before it is pulled straight — what
     * the search itself chose, which straightening hides on open ground.
     */
    static List<Integer> cellsOf(PathGrid grid, Coord3D from, Coord3D to) {
        var cells = new ArrayList<Integer>();
        search(grid, from, to, 0f, false, null, null, cells);
        return cells;
    }

    /**
     * What walking from {@code from} to {@code to} costs a body with no width, in world units — a cell for a straight
     * step, 1.4 for a diagonal, and the turns — the search giving up after {@code mostCells} cells, with {@link
     * Integer#MAX_VALUE} then or where there is no way ({@code Pathfinder::checkPathCost}, which gives up at 500).
     */
    public static int walkCost(PathGrid grid, Coord3D from, Coord3D to, int mostCells) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);
        if (grid.isBlocked(startX, startY) || grid.isBlocked(goalX, goalY)) {
            return Integer.MAX_VALUE;
        }
        int width = grid.getWidth();
        int start = grid.index(startX, startY);
        int goal = grid.index(goalX, goalY);
        var cost = new java.util.HashMap<Integer, Integer>();
        var cameFrom = new java.util.HashMap<Integer, Integer>();
        var closed = new java.util.HashSet<Integer>();
        var open = new PriorityQueue<int[]>((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0])
                : Integer.compare(a[1], b[1]));
        cost.put(start, 0);
        open.add(new int[] {heuristic(startX, startY, goalX, goalY), start});
        while (!open.isEmpty() && closed.size() < mostCells) {
            int current = open.poll()[1];
            if (current == goal) {
                return Math.round(cost.get(current) * grid.getCellSize() / ORTHOGONAL_COST);
            }
            if (!closed.add(current)) {
                continue;
            }
            int cx = current % width;
            int cy = current / width;
            for (var step : NEIGHBOURS) {
                int next = grid.index(cx + step[0], cy + step[1]);
                if (!canTake(grid, cx, cy, step, 0f) || closed.contains(next)) {
                    continue;
                }
                int tentative = cost.get(current) + (step[0] != 0 && step[1] != 0 ? DIAGONAL_COST : ORTHOGONAL_COST)
                        + turnCost(grid, cameFrom.getOrDefault(current, -1), cx, cy, step[0], step[1]);
                if (tentative >= cost.getOrDefault(next, Integer.MAX_VALUE)) {
                    continue;
                }
                cost.put(next, tentative);
                cameFrom.put(next, current);
                open.add(new int[] {tentative + heuristic(cx + step[0], cy + step[1], goalX, goalY), next});
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Find a path for something with no width — a marker, a camera, a test. */
    public static Path findPath(PathGrid grid, Coord3D from, Coord3D to) {
        return findPath(grid, from, to, 0f);
    }

    /**
     * Find a path from {@code from} to {@code to} wide enough for a body of
     * {@code clearance} radius, or {@link Path#EMPTY} if there is none.
     *
     * <p>If no route wide enough exists, the search is run again ignoring width
     * rather than reporting no route at all. A unit that has to squeeze is still
     * better off than a unit that refuses to move — and the alternative is a
     * mover that silently stops working the moment it grows.
     */
    public static Path findPath(PathGrid grid, Coord3D from, Coord3D to, float clearance) {
        // Standing on blocked ground, the only useful first move is off it. A
        // search from there finds nothing at all, which used to leave anything
        // that ended up inside a wall unable to plan its way out for the rest of
        // the game — permanently frozen rather than merely wedged.
        if (grid.isBlocked(grid.toCellX(from), grid.toCellY(from))) {
            return escape(grid, from);
        }
        var path = search(grid, from, to, clearance, false, null, null);
        if (path.isEmpty() && clearance > 0f) {
            path = search(grid, from, to, 0f, false, null, null);
        }
        return path;
    }

    /**
     * A route to {@code to}, or — where there is none — to the place nearest it that there is one to, as the
     * reference game's pathfinder does ({@code Pathfinder::findClosestPath}). {@link Path#reachesGoal} says
     * which it is.
     *
     * <p>What a mover is given. Sent at a spot it cannot reach — a cliff, the far side of a wall, a pocket
     * with no way in — it used to be handed no route at all, and stood where it was with its goal still set:
     * an order that did nothing, and an errand waiting on it that never finished. Now it goes as near as it
     * can and stops there, and whatever sent it can tell that from arriving.
     *
     * <p>Nearest by the grid's straight-line distance to the goal's cell, a tie going to the cell that is the
     * shorter walk and then to the lower cell index, so every machine settles on the same spot. Width is
     * treated as {@link #findPath} treats it: a route with room first, one that squeezes if that is the only
     * way there, and of two routes that both stop short, the one that stops nearer — room winning a tie.
     */
    public static Path findPathOrNearest(PathGrid grid, Coord3D from, Coord3D to, float clearance) {
        return findPathOrNearest(grid, from, to, clearance, null, null);
    }

    /**
     * The same, knowing the grid's {@link Zones}: a goal in another zone than the mover's is known to be out of
     * reach at once, and the search goes straight for the cell of the mover's zone nearest it — rather than walking
     * every cell it can reach to find that out.
     *
     * @param zones the grid's zones as it stands, or {@code null} to search as before
     * @param tally where the cells the search examined are added, or {@code null}
     */
    public static Path findPathOrNearest(PathGrid grid, Coord3D from, Coord3D to, float clearance, Zones zones,
            Tally tally) {
        return findPathOrNearest(grid, from, to, clearance, zones, tally, null);
    }

    /** The same, each step costed also by the movers on the ground, as {@code traffic} says; null for none. */
    public static Path findPathOrNearest(PathGrid grid, Coord3D from, Coord3D to, float clearance, Zones zones,
            Tally tally, Traffic traffic) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        if (grid.isBlocked(startX, startY)) {
            return escape(grid, from);
        }
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);
        int zone = zones == null ? -1 : zones.zoneOf(startX, startY);
        if (zone >= 0 && !zones.connected(startX, startY, goalX, goalY)) {
            int nearest = zones.nearestIn(zone, goalX, goalY, startX, startY);
            int width = grid.getWidth();
            if (nearest == startY * width + startX) {
                return Path.partial(List.of()); // nowhere nearer than where it stands
            }
            if (clearance > 0f && !fits(grid, nearest % width, nearest / width, clearance)) {
                int room = nearestWithRoom(grid, nearest % width, nearest / width, clearance, zones, zone);
                nearest = room >= 0 ? room : nearest; // no room near it: it squeezes there, as before
            }
            var there = grid.cellCenter(nearest % width, nearest / width);
            var way = search(grid, from, there, clearance, false, tally, traffic);
            if (way.isEmpty() && clearance > 0f) {
                way = search(grid, from, there, 0f, false, tally, traffic);
            }
            return Path.partial(way.getWaypoints());
        }
        if (clearance > 0f && !fits(grid, goalX, goalY, clearance)) {
            return intoTheTightSpot(grid, from, to, clearance, tally, traffic);
        }
        var path = search(grid, from, to, clearance, true, tally, traffic);
        if (path.reachesGoal() || clearance <= 0f) {
            return path;
        }
        var squeezed = search(grid, from, to, 0f, true, tally, traffic);
        if (squeezed.reachesGoal()) {
            return squeezed;
        }
        return awayFrom(squeezed, from, to) < awayFrom(path, from, to) ? squeezed : path;
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
            Traffic traffic) {
        float near = clearance + 2f * grid.getCellSize();
        if (across(from, to) <= near && isClearLine(grid, from, to, 0f)) {
            return new Path(List.of(to));
        }
        Within room = at -> {
            float off = across(at, to);
            return off > near ? off - near : isClearLine(grid, at, to, 0f) ? 0f : grid.getCellSize();
        };
        var way = findPathWithin(grid, from, to, clearance, room, tally, traffic);
        if (way.reachesGoal() && !way.isEmpty()) {
            var points = new ArrayList<Coord3D>(way.getWaypoints());
            points.add(to);
            return new Path(points);
        }
        return search(grid, from, to, 0f, true, tally, traffic);
    }

    /**
     * The cell nearest cell ({@code cx}, {@code cy}) that a body of {@code clearance} fits at — and, zones given, in
     * zone {@code zone} — ring by ring outward as far as the body is wide and two cells more, the nearest to the
     * cell's middle of a ring and then the lower index winning; -1 for none.
     */
    private static int nearestWithRoom(PathGrid grid, int cx, int cy, float clearance, Zones zones, int zone) {
        int rings = (int) Math.ceil(clearance / grid.getCellSize()) + 2;
        for (int ring = 1; ring <= rings; ring++) {
            int best = -1;
            long bestAway = Long.MAX_VALUE;
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue;
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    if (!grid.inBounds(x, y) || !fits(grid, x, y, clearance)
                            || zones != null && zone >= 0 && zones.zoneOf(x, y) != zone) {
                        continue;
                    }
                    long away = (long) dx * dx + (long) dy * dy;
                    int index = grid.index(x, y);
                    if (away < bestAway || away == bestAway && index < best) {
                        best = index;
                        bestAway = away;
                    }
                }
            }
            if (best >= 0) {
                return best;
            }
        }
        return -1;
    }

    /**
     * A route from {@code from} heading for {@code toward} that ends at the nearest cell within a band of it — the
     * reference's {@code Pathfinder::findAttackPath}, a route to fight ending at the first cell from which the weapon
     * reaches, not at a spot beside the target. The cell must have room for a body of {@code clearance} and is never
     * the one the mover stands in; the search heads for the band itself, so it goes as readily away from a thing it
     * stands too near as toward one it stands too far from.
     *
     * <p>Held to {@link #BAND_CELL_LIMIT} cells: where no cell of the band is found within them — the band lies
     * beyond a wall, or all of it is too tight — {@link Path#EMPTY}, and the caller heads somewhere plainer.
     */
    public static Path findPathWithin(PathGrid grid, Coord3D from, Coord3D toward, float clearance, Within within,
            Tally tally, Traffic traffic) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        if (grid.isBlocked(startX, startY)) {
            return escape(grid, from);
        }
        int width = grid.getWidth();
        int cellCount = width * grid.getHeight();
        int[] gScore = new int[cellCount];
        int[] fScore = new int[cellCount];
        int[] cameFrom = new int[cellCount];
        boolean[] closed = new boolean[cellCount];
        Arrays.fill(gScore, Integer.MAX_VALUE);
        Arrays.fill(cameFrom, -1);
        int startIndex = grid.index(startX, startY);
        gScore[startIndex] = 0;
        fScore[startIndex] = bandEstimate(grid, within, toward, startX, startY);
        var open = new PriorityQueue<Integer>((a, b) -> {
            int byF = Integer.compare(fScore[a], fScore[b]);
            return byF != 0 ? byF : Integer.compare(a, b);
        });
        open.add(startIndex);
        float half = grid.getCellSize() / 2f;
        int examined = 0;
        while (!open.isEmpty()) {
            int current = open.poll();
            if (closed[current]) {
                continue;
            }
            int cx = current % width;
            int cy = current / width;
            var centre = grid.cellCenter(cx, cy);
            if (current != startIndex && within.outside(centre) <= 0f
                    && across(centre, from) >= half) {
                return reconstruct(grid, cameFrom, current, startIndex, from, centre, clearance, traffic);
            }
            if (tally != null) {
                tally.cells++;
            }
            closed[current] = true;
            if (++examined > BAND_CELL_LIMIT) {
                continue; // no more expanding: what is open is looked at, and then it gives up
            }
            for (var step : NEIGHBOURS) {
                int nx = cx + step[0];
                int ny = cy + step[1];
                if (!fits(grid, nx, ny, clearance) || !grid.canStep(cx, cy, nx, ny)) {
                    continue;
                }
                boolean diagonal = step[0] != 0 && step[1] != 0;
                if (diagonal && grid.isBlocked(cx + step[0], cy) && grid.isBlocked(cx, cy + step[1])) {
                    continue;
                }
                int next = grid.index(nx, ny);
                if (closed[next]) {
                    continue;
                }
                int tentative = gScore[current] + (diagonal ? DIAGONAL_COST : ORTHOGONAL_COST)
                        + turnCost(grid, cameFrom[current], cx, cy, step[0], step[1]);
                if (traffic != null) {
                    int extra = traffic.costOf(nx, ny);
                    if (extra == Traffic.CLOSED) {
                        continue;
                    }
                    tentative += extra;
                }
                if (tentative >= gScore[next]) {
                    continue;
                }
                cameFrom[next] = current;
                gScore[next] = tentative;
                fScore[next] = tentative + bandEstimate(grid, within, toward, nx, ny);
                open.add(next);
            }
        }
        return Path.EMPTY;
    }

    /**
     * What a walk from cell ({@code cx}, {@code cy}) into the band is estimated to cost: the search's own estimate to
     * the goal ({@link #heuristic}) for the way from the cell to the band's nearest point, straight toward {@code
     * toward} or straight away from it, as far as the cell lies outside the band.
     */
    private static int bandEstimate(PathGrid grid, Within within, Coord3D toward, int cx, int cy) {
        var centre = grid.cellCenter(cx, cy);
        float outside = within.outside(centre);
        if (outside <= 0f) {
            return 0;
        }
        float dx = toward.x() - centre.x();
        float dy = toward.y() - centre.y();
        float span = (float) Math.sqrt(dx * dx + dy * dy);
        float cells = outside / grid.getCellSize();
        if (span < 1e-3f) {
            return (int) (cells * ORTHOGONAL_COST);
        }
        float across = Math.abs(dx) / span * cells;
        float along = Math.abs(dy) / span * cells;
        return (int) (ORTHOGONAL_COST * Math.max(across, along) + ORTHOGONAL_COST * Math.min(across, along) / 2f);
    }

    /** Across the ground from {@code a} to {@code b}. */
    private static float across(Coord3D a, Coord3D b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * The same, over the ground and the grid's decks together: from {@code from} on {@code fromFloor} to {@code to} on
     * {@code toFloor}, onto a deck only at an entry and off at another — see {@link PathGrid#enters}. A grid with no
     * deck is searched as {@link #findPathOrNearest(PathGrid, Coord3D, Coord3D, float, Zones, Tally)} searches it.
     */
    public static Path findPathOrNearest(PathGrid grid, Coord3D from, int fromFloor, Coord3D to, int toFloor,
            float clearance, Zones zones, Tally tally) {
        if (!grid.hasDecks()) {
            return findPathOrNearest(grid, from, to, clearance, zones, tally);
        }
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);
        int zone = zones == null ? -1 : zoneOn(grid, zones, fromFloor, startX, startY);
        if (zone >= 0 && zone != zoneOn(grid, zones, toFloor, goalX, goalY)) {
            // Out of reach, known at once as on the ground: straight for the ground of its zone nearest the goal.
            int nearest = zones.nearestIn(zone, goalX, goalY, startX, startY);
            int width = grid.getWidth();
            if (nearest < 0 || fromFloor == 0 && nearest == startY * width + startX) {
                return Path.partial(List.of());
            }
            var there = grid.cellCenter(nearest % width, nearest / width);
            var way = layered(grid, from, fromFloor, there, 0, clearance, tally);
            if (way.isEmpty() && clearance > 0f) {
                way = layered(grid, from, fromFloor, there, 0, 0f, tally);
            }
            return Path.partial(way.getWaypoints());
        }
        var path = layered(grid, from, fromFloor, to, toFloor, clearance, tally);
        if (path.reachesGoal() || clearance <= 0f) {
            return path;
        }
        var squeezed = layered(grid, from, fromFloor, to, toFloor, 0f, tally);
        return squeezed.reachesGoal() || awayFrom(squeezed, from, to) < awayFrom(path, from, to) ? squeezed : path;
    }

    /**
     * The zone of a cell on a floor: the ground's own, or — on a deck — that of the ground at its entries, which its
     * zones join; -1 for none.
     */
    private static int zoneOn(PathGrid grid, Zones zones, int floor, int cx, int cy) {
        if (floor == 0) {
            return zones.zoneOf(cx, cy);
        }
        int width = grid.getWidth();
        for (var deck : grid.decks()) {
            if (deck.floor() != floor) {
                continue;
            }
            for (var entry : deck.entries()) {
                int z = zones.zoneOf(entry[0] % width, entry[0] / width);
                if (z >= 0) {
                    return z;
                }
            }
        }
        return -1;
    }

    /**
     * A* over (floor, cell): the ground's cells and each deck's, a step along a floor or onto another at an entry.
     * The arrays of a floor are made the first time the search touches it, so a map of many decks costs what the decks
     * a route goes near cost. Nearest the goal where it cannot be reached, as {@link #search} with {@code orNearest}.
     */
    private static Path layered(PathGrid grid, Coord3D from, int fromFloor, Coord3D to, int toFloor, float clearance,
            Tally tally) {
        int width = grid.getWidth();
        int cells = width * grid.getHeight();
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);
        if (!grid.inBounds(startX, startY)) {
            return Path.EMPTY;
        }
        if (fromFloor == 0 && grid.isBlocked(startX, startY)) {
            return escape(grid, from);
        }
        int start = fromFloor * cells + grid.index(startX, startY);
        int goal = grid.inBounds(goalX, goalY) ? toFloor * cells + grid.index(goalX, goalY) : -1;
        if (start == goal) {
            return new Path(List.of(to));
        }
        int floors = grid.decks().size() + 1;
        var g = new int[floors][];
        var f = new int[floors][];
        var came = new int[floors][];
        var closed = new boolean[floors][];
        touch(g, f, came, closed, fromFloor, cells);
        g[fromFloor][start % cells] = 0;
        f[fromFloor][start % cells] = heuristic(startX, startY, goalX, goalY);
        var open = new PriorityQueue<Integer>((a, b) -> {
            int byF = Integer.compare(f[a / cells][a % cells], f[b / cells][b % cells]);
            return byF != 0 ? byF : Integer.compare(a, b);
        });
        open.add(start);
        int nearest = start;
        long nearestAway = away(startX, startY, goalX, goalY);
        while (!open.isEmpty()) {
            int current = open.poll();
            if (current == goal) {
                return layeredPath(grid, came, cells, current, start, from, fromFloor, to, clearance, true);
            }
            int floor = current / cells;
            int cell = current % cells;
            if (closed[floor][cell]) {
                continue;
            }
            if (tally != null) {
                tally.cells++;
            }
            closed[floor][cell] = true;
            int cx = cell % width;
            int cy = cell / width;
            long away = away(cx, cy, goalX, goalY);
            if (away < nearestAway || away == nearestAway && current < nearest) {
                nearest = current;
                nearestAway = away;
            }
            for (var step : NEIGHBOURS) {
                int nx = cx + step[0];
                int ny = cy + step[1];
                if (!grid.inBounds(nx, ny)) {
                    continue;
                }
                boolean diagonal = step[0] != 0 && step[1] != 0;
                int cost = g[floor][cell] + (diagonal ? DIAGONAL_COST : ORTHOGONAL_COST);
                boolean along = floor == 0
                        ? canTake(grid, cx, cy, step, clearance)
                        : grid.walks(floor, cx, cy, nx, ny) && (!diagonal
                                || grid.walks(floor, cx, cy, nx, cy) || grid.walks(floor, cx, cy, cx, ny));
                if (along) {
                    relax(g, f, came, closed, open, floor, nx, ny, cells, width, cost, current, goalX, goalY);
                }
                // Onto another floor: a deck from the ground at its entry, the ground from a deck's end.
                if (floor != 0) {
                    if (grid.enters(floor, 0, cx, cy, nx, ny) && fits(grid, nx, ny, clearance)) {
                        relax(g, f, came, closed, open, 0, nx, ny, cells, width, cost, current, goalX, goalY);
                    }
                    continue;
                }
                for (var deck : grid.decks()) {
                    if (grid.enters(0, deck.floor(), cx, cy, nx, ny)) {
                        relax(g, f, came, closed, open, deck.floor(), nx, ny, cells, width, cost, current, goalX,
                                goalY);
                    }
                }
            }
        }
        if (nearest == start) {
            return Path.partial(List.of());
        }
        int at = nearest % cells;
        var there = grid.cellCenter(at % width, at / width);
        var there3 = new Coord3D(there.x(), there.y(), grid.heightOn(nearest / cells, there));
        return layeredPath(grid, came, cells, nearest, start, from, fromFloor, there3, clearance, false);
    }

    /** A step onto (floor, cell) at {@code cost}: kept where it is the cheapest way there yet. */
    private static void relax(int[][] g, int[][] f, int[][] came, boolean[][] closed, PriorityQueue<Integer> open,
            int onto, int nx, int ny, int cells, int width, int cost, int current, int goalX, int goalY) {
        touch(g, f, came, closed, onto, cells);
        int next = ny * width + nx;
        if (closed[onto][next] || cost >= g[onto][next]) {
            return;
        }
        came[onto][next] = current;
        g[onto][next] = cost;
        f[onto][next] = cost + heuristic(nx, ny, goalX, goalY);
        open.add(onto * cells + next);
    }

    private static void touch(int[][] g, int[][] f, int[][] came, boolean[][] closed, int floor, int cells) {
        if (g[floor] == null) {
            g[floor] = new int[cells];
            f[floor] = new int[cells];
            came[floor] = new int[cells];
            closed[floor] = new boolean[cells];
            Arrays.fill(g[floor], Integer.MAX_VALUE);
            Arrays.fill(came[floor], -1);
        }
    }

    /**
     * The route the search found, as waypoints at the height of each one's floor, straightened a floor at a time: a
     * step onto another floor is kept, where it is, since the line past it crosses from one floor to another.
     */
    private static Path layeredPath(PathGrid grid, int[][] came, int cells, int goal, int start, Coord3D from,
            int fromFloor, Coord3D to, float clearance, boolean reaches) {
        int width = grid.getWidth();
        var nodes = new ArrayList<Integer>();
        for (int node = goal; node != -1 && node != start; node = came[node / cells][node % cells]) {
            nodes.add(node);
        }
        Collections.reverse(nodes);
        int count = nodes.size();
        var points = new Coord3D[count];
        var floors = new int[count];
        for (int i = 0; i < count; i++) {
            int node = nodes.get(i);
            floors[i] = node / cells;
            int cell = node % cells;
            var centre = grid.cellCenter(cell % width, cell / width);
            points[i] = i == count - 1 ? to
                    : new Coord3D(centre.x(), centre.y(), grid.heightOn(floors[i], centre));
        }
        var straightened = new ArrayList<Coord3D>();
        var onFloor = new ArrayList<Integer>();
        var anchor = from;
        int i = 0;
        while (i < count) {
            int runEnd = i;
            while (runEnd + 1 < count && floors[runEnd + 1] == floors[i]) {
                runEnd++;
            }
            int floor = floors[i];
            int k = i;
            if (i > 0 || floor != fromFloor) {
                straightened.add(points[i]); // where it stepped onto this floor: kept
                onFloor.add(floor);
                anchor = points[i];
                k = i + 1;
            }
            while (k <= runEnd) {
                int furthest = k;
                for (int j = runEnd; j > k; j--) {
                    if (clearOn(grid, floor, anchor, points[j], clearance)) {
                        furthest = j;
                        break;
                    }
                }
                anchor = points[furthest];
                straightened.add(anchor);
                onFloor.add(floor);
                k = furthest + 1;
            }
            i = runEnd + 1;
        }
        return Path.onFloors(straightened, onFloor.stream().mapToInt(Integer::intValue).toArray(),
                reaches && !straightened.isEmpty());
    }

    /** Whether the straight line between two points of one floor is walkable on it. */
    private static boolean clearOn(PathGrid grid, int floor, Coord3D a, Coord3D b, float clearance) {
        if (floor == 0) {
            return isClearLine(grid, a, b, clearance, null);
        }
        var deck = grid.deck(floor);
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        int samples = Math.max(1, (int) Math.ceil(distance / (grid.getCellSize() * LINE_SAMPLE_FRACTION)));
        for (int s = 0; s <= samples; s++) {
            float t = (float) s / samples;
            int cx = (int) Math.floor((a.x() + dx * t) / grid.getCellSize());
            int cy = (int) Math.floor((a.y() + dy * t) / grid.getCellSize());
            if (deck == null || !deck.walkable(cx, cy)) {
                return false;
            }
        }
        return true;
    }

    /** How far from {@code to} a route leaves its mover, across the ground. */
    private static float awayFrom(Path path, Coord3D from, Coord3D to) {
        var end = path.isEmpty() ? from : path.getDestination();
        float dx = end.x() - to.x();
        float dy = end.y() - to.y();
        return dx * dx + dy * dy;
    }

    /**
     * Off the blocked cell it stands on: the only useful first move from there, and a route that ends short
     * of wherever it was going, since it is only the way out.
     */
    private static Path escape(PathGrid grid, Coord3D from) {
        var way = nearestOpenCell(grid, from);
        return way == null ? Path.EMPTY : Path.partial(List.of(way));
    }

    // ---- where can be walked to ----

    /**
     * Every cell a body of {@code clearance} can walk to from {@code from}, by the search's own steps: one flag
     * a cell, indexed {@code cy * width + cx}. From a blocked cell, from the nearest open one — the way it would
     * step out first.
     */
    public static boolean[] reachableFrom(PathGrid grid, Coord3D from, float clearance) {
        var reached = new boolean[grid.getWidth() * grid.getHeight()];
        var flood = flood(grid, from, clearance);
        for (int at = 0; at < flood.count(); at++) {
            reached[flood.cells()[at]] = true;
        }
        return reached;
    }

    /**
     * {@code wanted} itself where it can be walked to from {@code from}; otherwise the middle of the reachable
     * cell nearest it, the first found walking outward from {@code from} winning a tie — the nearest place to a
     * point that cannot be reached.
     */
    public static Coord3D nearestReachable(PathGrid grid, Coord3D from, Coord3D wanted) {
        var flood = flood(grid, from, 0f);
        if (flood.count() == 0) {
            return wanted;
        }
        int width = grid.getWidth();
        int goalX = grid.toCellX(wanted);
        int goalY = grid.toCellY(wanted);
        int best = flood.cells()[0];
        long nearest = Long.MAX_VALUE;
        for (int at = 0; at < flood.count(); at++) {
            int cell = flood.cells()[at];
            int cx = cell % width;
            int cy = cell / width;
            if (cx == goalX && cy == goalY) {
                return wanted;
            }
            long away = away(cx, cy, goalX, goalY);
            if (away < nearest) {
                nearest = away;
                best = cell;
            }
        }
        var reached = grid.cellCenter(best % width, best / width);
        return new Coord3D(reached.x(), reached.y(), wanted.z());
    }

    /** The cells a flood reached, in the order it reached them: {@code cells[0..count)}. */
    private record Flood(int[] cells, int count) {
    }

    /**
     * Outward from {@code from}, a ring at a time and in the search's fixed neighbour order, by exactly the steps
     * the search would take — so a cell this reaches is a cell a route can be found to, on every machine alike.
     */
    private static Flood flood(PathGrid grid, Coord3D from, float clearance) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        if (grid.isBlocked(startX, startY)) {
            var way = nearestOpenCell(grid, from);
            if (way == null) {
                return new Flood(new int[0], 0);
            }
            startX = grid.toCellX(way);
            startY = grid.toCellY(way);
        }
        int width = grid.getWidth();
        var seen = new boolean[width * grid.getHeight()];
        var queue = new int[seen.length];
        int head = 0;
        int tail = 0;
        int start = grid.index(startX, startY);
        seen[start] = true;
        queue[tail++] = start;
        while (head < tail) {
            int cell = queue[head++];
            int cx = cell % width;
            int cy = cell / width;
            for (var step : NEIGHBOURS) {
                int nx = cx + step[0];
                int ny = cy + step[1];
                if (!grid.inBounds(nx, ny) || seen[grid.index(nx, ny)] || !canTake(grid, cx, cy, step, clearance)) {
                    continue;
                }
                seen[grid.index(nx, ny)] = true;
                queue[tail++] = grid.index(nx, ny);
            }
            // Across an open deck, end to end: its every entry is reached from any of them.
            for (var deck : grid.decks()) {
                if (!deck.isOpen() || deck.entries().stream().noneMatch(entry -> entry[0] == cell)) {
                    continue;
                }
                for (var entry : deck.entries()) {
                    if (!seen[entry[0]] && !grid.isBlocked(entry[0] % width, entry[0] / width)) {
                        seen[entry[0]] = true;
                        queue[tail++] = entry[0];
                    }
                }
            }
        }
        return new Flood(queue, tail);
    }

    /**
     * Whether the search would take this step: room at the far end, a step the grid allows, and — for a diagonal — one
     * of its two sides open, as the reference's {@code examineNeighboringCells} asks.
     */
    private static boolean canTake(PathGrid grid, int cx, int cy, int[] step, float clearance) {
        int nx = cx + step[0];
        int ny = cy + step[1];
        if (!fits(grid, nx, ny, clearance) || !grid.canStep(cx, cy, nx, ny)) {
            return false;
        }
        boolean diagonal = step[0] != 0 && step[1] != 0;
        return !diagonal || !grid.isBlocked(cx + step[0], cy) || !grid.isBlocked(cx, cy + step[1]);
    }

    /** How far apart two cells are, squared, which is enough to compare them. */
    private static long away(int cx, int cy, int goalX, int goalY) {
        long dx = cx - goalX;
        long dy = cy - goalY;
        return dx * dx + dy * dy;
    }

    /**
     * The centre of the closest cell that can be stood on, or {@code null} if the
     * search gives up.
     *
     * <p>Rings outward from the mover, and within a ring in a fixed order, so two
     * machines pick the same way out. Bounded because a world can be solid stone,
     * and a mover in the middle of one should stop rather than scan it all.
     */
    private static Coord3D nearestOpenCell(PathGrid grid, Coord3D from) {
        int cx = grid.toCellX(from);
        int cy = grid.toCellY(from);
        for (int ring = 1; ring <= ESCAPE_RINGS; ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != ring) {
                        continue; // only the edge of this ring; the inside was searched
                    }
                    int x = cx + dx;
                    int y = cy + dy;
                    if (!grid.isBlocked(x, y)) { // out of bounds counts as blocked
                        return grid.cellCenter(x, y);
                    }
                }
            }
        }
        return null;
    }

    /** How far to look for a way out before deciding there is none. */
    private static final int ESCAPE_RINGS = 8;

    /**
     * A* from {@code from} to {@code to}. With {@code orNearest}, a goal that cannot be reached — blocked, or
     * walled off — gives a {@linkplain Path#partial partial} route to the searched cell nearest it rather than
     * none: the search has walked every cell it could reach by the time it gives up, so the answer costs
     * nothing more than the failure did.
     */
    private static Path search(PathGrid grid, Coord3D from, Coord3D to, float clearance, boolean orNearest,
            Tally tally, Traffic traffic) {
        return search(grid, from, to, clearance, orNearest, tally, traffic, null);
    }

    private static Path search(PathGrid grid, Coord3D from, Coord3D to, float clearance, boolean orNearest,
            Tally tally, Traffic traffic, List<Integer> cellsChosen) {
        int startX = grid.toCellX(from);
        int startY = grid.toCellY(from);
        int goalX = grid.toCellX(to);
        int goalY = grid.toCellY(to);

        if (grid.isBlocked(startX, startY) || grid.isBlocked(goalX, goalY) && !orNearest) {
            return Path.EMPTY;
        }
        if (startX == goalX && startY == goalY) {
            return new Path(List.of(to));
        }

        int width = grid.getWidth();
        int cellCount = width * grid.getHeight();
        int[] gScore = new int[cellCount];
        int[] fScore = new int[cellCount];
        int[] cameFrom = new int[cellCount];
        boolean[] closed = new boolean[cellCount];
        Arrays.fill(gScore, Integer.MAX_VALUE);
        Arrays.fill(cameFrom, -1);

        int startIndex = grid.index(startX, startY);
        int goalIndex = grid.index(goalX, goalY);
        gScore[startIndex] = 0;
        fScore[startIndex] = heuristic(startX, startY, goalX, goalY);

        var open = new PriorityQueue<Integer>((a, b) -> {
            int byF = Integer.compare(fScore[a], fScore[b]);
            return byF != 0 ? byF : Integer.compare(a, b); // deterministic tie-break
        });
        open.add(startIndex);

        int nearest = startIndex;
        long nearestAway = away(startX, startY, goalX, goalY);
        while (!open.isEmpty()) {
            int current = open.poll();
            if (current == goalIndex) {
                if (cellsChosen != null) {
                    for (int cell = current; cell != -1 && cell != startIndex; cell = cameFrom[cell]) {
                        cellsChosen.addFirst(cell);
                    }
                }
                return reconstruct(grid, cameFrom, current, startIndex, from, to, clearance, traffic);
            }
            if (closed[current]) {
                continue; // stale entry from a superseded g-score
            }
            if (tally != null) {
                tally.cells++;
            }
            closed[current] = true;

            int cx = current % width;
            int cy = current / width;
            long away = away(cx, cy, goalX, goalY);
            if (away < nearestAway || away == nearestAway && (gScore[current] < gScore[nearest]
                    || gScore[current] == gScore[nearest] && current < nearest)) {
                nearest = current;
                nearestAway = away;
            }
            for (var step : NEIGHBOURS) {
                expand(grid, gScore, fScore, cameFrom, closed, open,
                        cx, cy, step[0], step[1], goalX, goalY, clearance, traffic);
            }
        }
        if (!orNearest) {
            return Path.EMPTY;
        }
        if (nearest == startIndex) {
            return Path.partial(List.of()); // nowhere nearer than where it stands
        }
        var there = grid.cellCenter(nearest % width, nearest / width);
        return Path.partial(reconstruct(grid, cameFrom, nearest, startIndex, from, there, clearance, traffic)
                .getWaypoints());
    }

    private static void expand(PathGrid grid, int[] gScore, int[] fScore, int[] cameFrom,
            boolean[] closed, PriorityQueue<Integer> open,
            int cx, int cy, int dx, int dy, int goalX, int goalY, float clearance, Traffic traffic) {
        int nx = cx + dx;
        int ny = cy + dy;
        if (!fits(grid, nx, ny, clearance) || !grid.canStep(cx, cy, nx, ny)) {
            return;
        }
        boolean diagonal = dx != 0 && dy != 0;
        if (diagonal && grid.isBlocked(cx + dx, cy) && grid.isBlocked(cx, cy + dy)) {
            return; // a diagonal needs one of its two sides open
        }

        int neighbour = grid.index(nx, ny);
        if (closed[neighbour]) {
            return;
        }
        int here = grid.index(cx, cy);
        int tentative = gScore[here] + (diagonal ? DIAGONAL_COST : ORTHOGONAL_COST) + turnCost(grid, cameFrom[here],
                cx, cy, dx, dy);
        if (traffic != null) {
            int extra = traffic.costOf(nx, ny);
            if (extra == Traffic.CLOSED) {
                return;
            }
            tentative += extra;
        }
        if (tentative >= gScore[neighbour]) {
            return;
        }
        cameFrom[neighbour] = grid.index(cx, cy);
        gScore[neighbour] = tentative;
        fScore[neighbour] = tentative + heuristic(nx, ny, goalX, goalY);
        open.add(neighbour);
    }

    /**
     * What a change of direction adds to a step: the reference's 4 at 45 degrees, 8 at 90 and 16 at 135 ({@code
     * PathfindCell::costSoFar}); nothing going straight on, or on the first step.
     */
    private static int turnCost(PathGrid grid, int parent, int cx, int cy, int dx, int dy) {
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
    private static int heuristic(int cx, int cy, int goalX, int goalY) {
        int dx = Math.abs(cx - goalX);
        int dy = Math.abs(cy - goalY);
        return ORTHOGONAL_COST * Math.max(dx, dy) + ORTHOGONAL_COST * Math.min(dx, dy) / 2;
    }

    private static Path reconstruct(PathGrid grid, int[] cameFrom, int goal, int start,
            Coord3D exactFrom, Coord3D exactTo, float clearance, Traffic traffic) {
        int width = grid.getWidth();
        var cells = new ArrayList<Integer>();
        for (int cell = goal; cell != -1 && cell != start; cell = cameFrom[cell]) {
            cells.add(cell);
        }
        Collections.reverse(cells);

        var waypoints = new ArrayList<Coord3D>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            int cell = cells.get(i);
            // The final cell uses the caller's exact destination, not the cell centre.
            if (i == cells.size() - 1) {
                waypoints.add(exactTo);
            } else {
                waypoints.add(grid.cellCenter(cell % width, cell / width));
            }
        }
        return new Path(straighten(grid, exactFrom, waypoints, clearance, traffic));
    }

    /**
     * Drop every waypoint the mover can already see past.
     *
     * <p>The cells are a proof that a route exists, not an instruction to walk
     * their centres. Starting from where the mover stands, take the furthest
     * waypoint still reachable in a straight line, and throw away everything
     * between. Across open floor that collapses the whole staircase into the
     * destination.
     */
    private static List<Coord3D> straighten(PathGrid grid, Coord3D from,
            List<Coord3D> waypoints, float clearance, Traffic traffic) {
        if (waypoints.size() < 2) {
            return waypoints;
        }
        var straightened = new ArrayList<Coord3D>();
        var anchor = from;
        int i = 0;
        while (i < waypoints.size()) {
            int furthest = i;
            for (int j = waypoints.size() - 1; j > i; j--) {
                if (isClearLine(grid, anchor, waypoints.get(j), clearance, traffic)) {
                    furthest = j;
                    break;
                }
            }
            anchor = waypoints.get(furthest);
            straightened.add(anchor);
            i = furthest + 1;
        }
        return straightened;
    }

    /** Whether a body of {@code clearance} radius walks straight from {@code a} to {@code b} past nothing that stops it. */
    public static boolean isClearLine(PathGrid grid, Coord3D a, Coord3D b, float clearance) {
        return isClearLine(grid, a, b, clearance, null);
    }

    /**
     * Whether a body of {@code clearance} radius can travel the straight line
     * between two points without touching stone — nor, given {@code traffic}, a
     * cell its routes go round: a mover it is stuck behind, or an ally standing still.
     *
     * <p>Sampled rather than traced exactly. At zero clearance a sample can slip
     * past the very corner of a cell — which is harmless, because something with
     * no width has nothing to catch on it.
     *
     * <p>The line has to obey height as well, and this is the place it is easiest
     * to forget: the search itself climbs only where a ramp lets it, and then the
     * straightening throws away the waypoints that made it do so. A line drawn
     * from one floor to another without a stair under it looks perfectly clear
     * cell by cell — every one of them is open ground. So each time the line
     * crosses into a new cell, that crossing is asked the same question a step
     * would be asked.
     */
    public static boolean isClearLine(PathGrid grid, Coord3D a, Coord3D b, float clearance, Traffic traffic) {
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        int samples = Math.max(1,
                (int) Math.ceil(distance / (grid.getCellSize() * LINE_SAMPLE_FRACTION)));
        int lastX = grid.toCellX(a);
        int lastY = grid.toCellY(a);
        for (int i = 0; i <= samples; i++) {
            float t = (float) i / samples;
            float x = a.x() + dx * t;
            float y = a.y() + dy * t;
            if (!isClearAround(grid, x, y, clearance)) {
                return false;
            }
            int cx = (int) Math.floor(x / grid.getCellSize());
            int cy = (int) Math.floor(y / grid.getCellSize());
            if ((cx != lastX || cy != lastY) && !grid.canStep(lastX, lastY, cx, cy)) {
                return false;
            }
            if ((cx != lastX || cy != lastY) && traffic != null
                    && (traffic.costOf(cx, cy) == Traffic.CLOSED || traffic.allyStill(cx, cy))) {
                return false; // the route went round a mover: so does the line
            }
            lastX = cx;
            lastY = cy;
        }
        return true;
    }

    /** Whether a cell can hold a body of {@code clearance} radius at its centre. */
    private static boolean fits(PathGrid grid, int cx, int cy, float clearance) {
        if (grid.isBlocked(cx, cy)) {
            return false;
        }
        if (clearance <= 0f) {
            return true;
        }
        var center = grid.cellCenter(cx, cy);
        return isClearAround(grid, center.x(), center.y(), clearance);
    }

    /** Whether every cell a body of {@code clearance} radius would touch is free. */
    private static boolean isClearAround(PathGrid grid, float x, float y, float clearance) {
        float cell = grid.getCellSize();
        int minX = (int) Math.floor((x - clearance) / cell);
        int maxX = (int) Math.floor((x + clearance) / cell);
        int minY = (int) Math.floor((y - clearance) / cell);
        int maxY = (int) Math.floor((y + clearance) / cell);
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cx = minX; cx <= maxX; cx++) {
                if (grid.isBlocked(cx, cy)) {
                    return false;
                }
            }
        }
        return true;
    }
}
