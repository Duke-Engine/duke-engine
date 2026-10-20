package uz.dukeengine.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.Path;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.Pathfinder;
import uz.dukeengine.core.pathfind.Sectors;
import uz.dukeengine.core.pathfind.Zones;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.Solid;

/**
 * The path-search front: a mover's route over the ground as it walks it — its passage for the classes it enters, the
 * sectors and zones of that passage for its width — and the cells a frame's searches may examine.
 */
final class Routes {

    private final Supplier<PathGrid> grid;
    private final Obstacles obstacles;
    private final Supplier<GroundCells> groundCells;
    /** How many of the map's cells a side a sector is, where routes are looked for by sectors; 0 where they are not. */
    private int routeSectors;
    /** Each passage's sectors, by the classes it enters and the clearance they are laid for. */
    private final Map<Long, Sectors> sectorsBySurfaces = new HashMap<>();
    private final Map<Sectors, Zones> sectorZones = new IdentityHashMap<>();
    /** Each passage's zones, by the classes it enters. */
    private final Map<Integer, Zones> passageZones = new HashMap<>();
    private Zones zones;
    private int budget = GameLogic.DEFAULT_PATHFIND_BUDGET;
    private int cellsThisFrame;
    private int cellsLastFrame;

    Routes(Supplier<PathGrid> grid, Obstacles obstacles, Supplier<GroundCells> groundCells) {
        this.grid = grid;
        this.obstacles = obstacles;
        this.groundCells = groundCells;
    }

    void setSectors(int mapCells) {
        this.routeSectors = Math.max(0, mapCells);
        forgetSectors();
    }

    int sectors() {
        return routeSectors;
    }

    /** Every passage's sectors laid afresh when next asked for: a new grid's. */
    void forgetSectors() {
        sectorsBySurfaces.clear();
        sectorZones.clear();
    }

    /** The classes of ground {@code mover} may enter, as the grid's bits: its locomotor's ({@code MoveUpdate}). */
    int surfacesOf(GameObject mover) {
        var pathGrid = grid.get();
        return pathGrid == null || !(mover.getLocomotor() instanceof MoveUpdate move)
                ? 0 : pathGrid.surfacesOf(move.surfaces());
    }

    /** The ground as {@code mover} walks it: the grid, or its passage for the classes it may enter. Null for none. */
    PathGrid gridFor(GameObject mover) {
        var pathGrid = grid.get();
        return pathGrid == null ? null : pathGrid.passage(surfacesOf(mover));
    }

    Sectors sectorsOf(PathGrid passage) {
        return sectorsOf(passage, 0f);
    }

    /**
     * The sectors of the ground as {@code passage} sees it, laid for a body of {@code clearance} radius — its pieces
     * only the cells such a body fits at, so a way across them is one it walks (E97) — and caught up with it; null
     * where routes are not looked for by sectors, or the grid has decks.
     */
    Sectors sectorsOf(PathGrid passage, float clearance) {
        if (routeSectors <= 0 || passage == null || passage.hasDecks()) {
            return null;
        }
        obstacles.refresh();
        long key = (long) passage.surfaces() << 32 | (Float.floatToIntBits(Math.max(0f, clearance)) & 0xFFFFFFFFL);
        var sectors = sectorsBySurfaces.get(key);
        if (sectors == null) {
            int size = Math.min(256, routeSectors * passage.cellsPerMapCell());
            sectors = Sectors.of(passage, Math.max(2, size), clearance);
            sectorsBySurfaces.put(key, sectors);
        } else {
            sectors.catchUp(passage);
        }
        return sectors;
    }

    private Zones zonesOver(Sectors sectors) {
        return sectorZones.computeIfAbsent(sectors, Zones::over);
    }

    /**
     * The zones of the ground as {@code passage} sees it — the reference's zones joined per set of surfaces
     * ({@code groundCliff}, {@code waterGround}, {@code groundRubble}): the grid's own for a mover of plain ground.
     */
    Zones zonesOf(PathGrid passage) {
        if (passage == null) {
            return null;
        }
        if (passage.surfaces() == 0) {
            return zones();
        }
        obstacles.refresh();
        var sectors = sectorsOf(passage);
        if (sectors != null) {
            return zonesOver(sectors);
        }
        var cached = passageZones.get(passage.surfaces());
        if (cached == null || !cached.isCurrent(passage)) {
            cached = Zones.of(passage);
            passageZones.put(passage.surfaces(), cached);
        }
        return cached;
    }

    /** The grid's connected zones as it stands; null for a world with no grid. */
    Zones zones() {
        var pathGrid = grid.get();
        if (pathGrid == null) {
            return null;
        }
        obstacles.refresh();
        var sectors = sectorsOf(pathGrid);
        if (sectors != null) {
            return zonesOver(sectors);
        }
        if (zones == null || !zones.isCurrent(pathGrid)) {
            zones = Zones.of(pathGrid);
        }
        return zones;
    }

    Path findPath(Coord3D from, Coord3D to) {
        var pathGrid = grid.get();
        if (pathGrid == null) {
            return new Path(List.of(to)); // open terrain: go straight there
        }
        obstacles.refresh();
        return Pathfinder.findPath(pathGrid, from, to);
    }

    /**
     * A route for {@code mover} to {@code to}, planned round the movers {@code round} too; null where the frame's
     * searching is spent.
     */
    Path findPath(GameObject mover, Coord3D to, Set<ObjectId> round) {
        if (grid.get() == null) {
            return new Path(List.of(to));
        }
        if (spent()) {
            return null; // the frame's searching is spent: it waits for the next
        }
        // A search once started runs to its end, as the reference's do (processPathfindQueue starts one only while
        // the frame's total is under PATHFIND_CELLS_PER_FRAME): a frame goes over by one search at most, and no
        // search is thrown away half done to be started again.
        return route(mover, to, round);
    }

    /**
     * A route into the band round {@code what} — the cells it examines counted against the frame's searching as any
     * route's are — or, where no cell of the band is found within its limit, a route to {@code straight}, the
     * straight-line answer, as near as there is one to.
     */
    Path findPathWithin(GameObject mover, GameObject what, float least, float most, Supplier<Coord3D> straight) {
        var pathGrid = grid.get();
        if (pathGrid == null) {
            return new Path(List.of(straight.get()));
        }
        if (spent()) {
            return null;
        }
        if (pathGrid.hasDecks()) {
            return route(mover, straight.get(), Set.of());
        }
        var tally = new Pathfinder.Tally();
        float clearance = Solid.of(mover.getTemplate()).footprintRadius();
        var traffic = traffic(mover, Set.of());
        var target = Footprint.of(what);
        // The mover as the circle round it: whichever way it faces as it arrives, it is then within both — a box
        // reaches no further than its circle, and is no nearer.
        var round = new Geometry.Cylinder(clearance, 1f);
        Pathfinder.Within within = at -> {
            float gap = new Footprint(round, at, 0f).separation(target);
            return gap > most ? gap - most : gap < least ? least - gap : 0f;
        };
        var path = Pathfinder.findPathWithin(gridFor(mover), mover.getPosition(), what.getPosition(), clearance,
                within, tally, traffic);
        cellsThisFrame += tally.cells();
        return path.isEmpty() ? route(mover, straight.get(), Set.of()) : path;
    }

    /** Straight past stone and past the still allies its routes go round, as its route was straightened. */
    boolean walksStraight(GameObject mover, Coord3D from, Coord3D to) {
        if (grid.get() == null) {
            return true;
        }
        return Pathfinder.isClearLine(gridFor(mover), from, to, Solid.of(mover.getTemplate()).footprintRadius(),
                traffic(mover, Set.of()));
    }

    /** The ground laid as it stands; whether the frame's searching is spent. */
    private boolean spent() {
        obstacles.refresh();
        return cellsThisFrame >= budgetThisGrid();
    }

    /** The movers {@code mover}'s route is planned round — those its cells say, and {@code round} — or null for none. */
    private Pathfinder.Traffic traffic(GameObject mover, Set<ObjectId> round) {
        var cells = groundCells.get();
        if (!cells.keepsCells(mover)) {
            return null;
        }
        var ids = new HashSet<Integer>();
        for (var id : round) {
            ids.add(id.value());
        }
        return cells.trafficFor(mover, ids);
    }

    private Path route(GameObject mover, Coord3D to, Set<ObjectId> round) {
        var passage = gridFor(mover);
        var tally = new Pathfinder.Tally();
        float clearance = Solid.of(mover.getTemplate()).footprintRadius();
        var traffic = traffic(mover, round);
        var sectors = sectorsOf(passage, clearance);
        Path path;
        if (passage.hasDecks()) {
            path = Pathfinder.findPathOrNearest(passage, mover.getPosition(), mover.getFloor(), to,
                    passage.floorAt(to), clearance, zonesOf(passage), tally);
        } else if (sectors == null) {
            path = Pathfinder.findPathOrNearest(passage, mover.getPosition(), to, clearance, zonesOf(passage), tally,
                    traffic);
        } else {
            var zones = zonesOver(sectors);
            path = clearance > 0f
                    ? Pathfinder.findPathOrNearest(passage, mover.getPosition(), to, clearance, zones, tally, traffic,
                            sectors, sectorsOf(passage))
                    : Pathfinder.findPathOrNearest(passage, mover.getPosition(), to, clearance, zones, tally, traffic,
                            sectors);
        }
        cellsThisFrame += tally.cells();
        return path;
    }

    void setBudget(int cells) {
        this.budget = Math.max(1, cells);
    }

    int budget() {
        return budget;
    }

    /** The budget in this grid's cells: that many more where the map is walked finer — the same ground a frame. */
    private int budgetThisGrid() {
        var pathGrid = grid.get();
        int finer = pathGrid == null ? 1 : pathGrid.cellsPerMapCell();
        return budget * finer * finer;
    }

    int cellsLastFrame() {
        return cellsLastFrame;
    }

    /** The frame is over: what its searches examined is the last frame's, and the next starts at none. */
    void frameEnded() {
        cellsLastFrame = cellsThisFrame;
        cellsThisFrame = 0;
    }
}
