package uz.dukeengine.core.pathfind;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.NearestRoute.Searcher;

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
 *
 * <p>The entry point: the steps are {@code Steps}', the searches {@code GridSearch}, {@code SectorSearch}, {@code
 * BandSearch} and {@code DeckSearch}, the fallbacks to the nearest place {@code NearestRoute}'s, and the straight lines
 * {@code Lines}'.
 */
public final class Pathfinder {

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

    private Pathfinder() {
    }

    /**
     * Hold a search's arrays by the cell up to {@code cells} cells, and past them only for the cells it touches: for a
     * test to hold the two alike on a small grid. Returns what it was.
     */
    static int denseUpTo(int cells) {
        return SearchScratch.denseUpTo(cells);
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

        /** One more cell examined, where there is a tally to add it to. */
        static void examined(Tally tally) {
            if (tally != null) {
                tally.cells++;
            }
        }
    }

    /**
     * The cells a route from {@code from} to {@code to} goes through, in order, before it is pulled straight — what
     * the search itself chose, which straightening hides on open ground.
     */
    static List<Integer> cellsOf(PathGrid grid, Coord3D from, Coord3D to) {
        return GridSearch.cellsOf(grid, from, to);
    }

    /**
     * What walking from {@code from} to {@code to} costs a body with no width, in world units — a cell for a straight
     * step, 1.4 for a diagonal, and the turns — the search giving up after {@code mostCells} cells, with {@link
     * Integer#MAX_VALUE} then or where there is no way ({@code Pathfinder::checkPathCost}, which gives up at 500).
     */
    public static int walkCost(PathGrid grid, Coord3D from, Coord3D to, int mostCells) {
        return GridSearch.walkCost(grid, from, to, mostCells);
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
            return Room.escape(grid, from);
        }
        var path = GridSearch.search(grid, from, to, clearance, false, null, null);
        if (path.isEmpty() && clearance > 0f) {
            path = GridSearch.search(grid, from, to, 0f, false, null, null);
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
        return NearestRoute.find(grid, from, to, clearance, zones, tally, traffic, GridSearch::search, null, null);
    }

    /**
     * The same across a wide world, by its {@link Sectors}: each search first finds the sectors a way goes through,
     * piece by piece, and then looks cell by cell only in them and the ring of sectors round each — a route across the
     * world examines the ground along its way, not every cell it could reach. The route is the best within those
     * sectors, not always the best of the world; a world that is not {@code Sectored} is searched whole, as before.
     */
    public static Path findPathOrNearest(PathGrid grid, Coord3D from, Coord3D to, float clearance, Zones zones,
            Tally tally, Traffic traffic, Sectors sectors) {
        return NearestRoute.find(grid, from, to, clearance, zones, tally, traffic, through(sectors), null, null);
    }

    /**
     * The same by {@code sectors} laid for the body's width ({@link Sectors#of(PathGrid, int, float)}), and {@code
     * flat}, the same ground's sectors for a body of none: a goal its width is shut off from but no width is — a gate
     * narrower than it — is squeezed to, as a mover always squeezed where it must, found by the flat sectors.
     */
    public static Path findPathOrNearest(PathGrid grid, Coord3D from, Coord3D to, float clearance, Zones zones,
            Tally tally, Traffic traffic, Sectors sectors, Sectors flat) {
        return NearestRoute.find(grid, from, to, clearance, zones, tally, traffic, through(sectors), Zones.over(flat),
                through(flat));
    }

    /** A search a stretch at a time along the way {@code sectors} give. */
    private static Searcher through(Sectors sectors) {
        return (grid, from, to, clearance, orNearest, tally, traffic) ->
                SectorSearch.stretchByStretch(grid, sectors, from, to, clearance, orNearest, tally, traffic);
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
        return DeckSearch.findPathOrNearest(grid, from, fromFloor, to, toFloor, clearance, zones, tally);
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
        return BandSearch.findPathWithin(grid, from, toward, clearance, within, tally, traffic);
    }

    // ---- where can be walked to ----

    /**
     * Every cell a body of {@code clearance} can walk to from {@code from}, by the search's own steps: one flag
     * a cell, indexed {@code cy * width + cx}. From a blocked cell, from the nearest open one — the way it would
     * step out first.
     */
    public static boolean[] reachableFrom(PathGrid grid, Coord3D from, float clearance) {
        return Reach.reachableFrom(grid, from, clearance);
    }

    /**
     * {@code wanted} itself where it can be walked to from {@code from}; otherwise the middle of the reachable
     * cell nearest it, the first found walking outward from {@code from} winning a tie — the nearest place to a
     * point that cannot be reached.
     */
    public static Coord3D nearestReachable(PathGrid grid, Coord3D from, Coord3D wanted) {
        return Reach.nearestReachable(grid, from, wanted);
    }

    /** Whether a body of {@code clearance} radius walks straight from {@code a} to {@code b} past nothing that stops it. */
    public static boolean isClearLine(PathGrid grid, Coord3D a, Coord3D b, float clearance) {
        return Lines.isClearLine(grid, a, b, clearance, null);
    }

    /**
     * Whether a body of {@code clearance} radius can travel the straight line between two points without touching
     * stone — nor, given {@code traffic}, a cell its routes go round: a mover it is stuck behind, or an ally standing
     * still, sampled along it and asked at each cell it crosses what a step would be.
     */
    public static boolean isClearLine(PathGrid grid, Coord3D a, Coord3D b, float clearance, Traffic traffic) {
        return Lines.isClearLine(grid, a, b, clearance, traffic);
    }

    /**
     * Whether a cell can hold a body of {@code clearance} radius at its centre: open, with room from stone, and clear
     * of the circles standing in the way by its true distance — a body with no width only kept out of them.
     */
    static boolean fitsAt(PathGrid grid, int cx, int cy, float clearance) {
        return Room.fits(grid, cx, cy, clearance);
    }
}
