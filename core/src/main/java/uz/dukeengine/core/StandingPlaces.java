package uz.dukeengine.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.Zones;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.Solid;

/**
 * Where a mover should stand on a world with ground to walk on: beside a thing, on ground it can stand on and walk to
 * — and remembered, so arriving there counts as beside — or aside from another's way.
 */
final class StandingPlaces {

    /**
     * Where a mover was last sent beside a thing: the thing, where it stood then, and the point — a thing that has
     * moved since, a unit closed on, has left the point behind.
     */
    private record SentBeside(int what, Coord3D whatAt, Coord3D at) {
    }

    private final Supplier<PathGrid> grid;
    private final Routes routes;
    private final Supplier<GroundCells> groundCells;
    private final Map<Integer, SentBeside> sentBeside = new HashMap<>();

    StandingPlaces(Supplier<PathGrid> grid, Routes routes, Supplier<GroundCells> groundCells) {
        this.grid = grid;
        this.routes = routes;
        this.groundCells = groundCells;
    }

    /**
     * Whether {@code who}, sent beside {@code what} and arrived, stopped within its close-enough distance of the point,
     * whichever way it faces, as the reference takes a unit within its {@code CloseEnoughDist} of its dock position as
     * there ({@code AIInternalMoveToState}).
     */
    boolean arrivedBeside(GameObject who, GameObject what) {
        var sent = sentBeside.get(who.getId().value());
        var legs = who.getLocomotor();
        if (sent == null || sent.what() != what.getId().value() || !what.getPosition().equals(sent.whatAt())
                || legs == null || legs.isMoving()) {
            return false;
        }
        float dx = who.getPosition().x() - sent.at().x();
        float dy = who.getPosition().y() - sent.at().y();
        float close = legs.closeEnough() + 1e-3f;
        return dx * dx + dy * dy <= close * close;
    }

    /** {@code who} is sent to {@code point} beside {@code what}, to be taken as beside it once there. */
    void sentBeside(GameObject who, GameObject what, Coord3D point) {
        sentBeside.put(who.getId().value(), new SentBeside(what.getId().value(), what.getPosition(), point));
    }

    void forget(ObjectId id) {
        sentBeside.remove(id.value());
    }

    /**
     * Where {@code who}, not beside {@code what} yet, should stand to work on it: {@code straight}, the straight-line
     * spot, where it can be stood on and walked to; else the cell beside the thing nearest it that can be reached;
     * else the reachable place nearest the straight-line spot.
     */
    Coord3D spotBeside(GameObject who, GameObject what, Coord3D straight) {
        var passage = routes.gridFor(who);
        var zones = routes.zonesOf(passage);
        var from = who.getPosition();
        int fromX = passage.toCellX(from);
        int fromY = passage.toCellY(from);
        if (passage.isBlocked(fromX, fromY)) {
            // Standing in stone, it steps out first: what it can reach is what that cell can.
            var way = nearestOpenCentre(passage, from);
            if (way == null) {
                return straight;
            }
            fromX = passage.toCellX(way);
            fromY = passage.toCellY(way);
        }
        int zone = zones.zoneOf(fromX, fromY);
        // The spot will do if it can be stood on and walked to — which its zone says at once, where it used to take a
        // search each time, and every frame for a unit closing on something.
        if (zones.zoneOf(passage.toCellX(straight), passage.toCellY(straight)) == zone && zone >= 0) {
            return straight;
        }
        var beside = besideCellNearest(passage, who, what, straight, zones, zone);
        return beside != null ? beside : reachableNearest(passage, straight, zones, zone, fromX, fromY);
    }

    /** The centre of the open cell nearest a point in stone, in rings outward, or null for none near. */
    private static Coord3D nearestOpenCentre(PathGrid passage, Coord3D from) {
        int cx = passage.toCellX(from);
        int cy = passage.toCellY(from);
        for (int ring = 1; ring <= 8 * passage.cellsPerMapCell(); ring++) {
            for (int dy = -ring; dy <= ring; dy++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) == ring && !passage.isBlocked(cx + dx, cy + dy)) {
                        return passage.cellCenter(cx + dx, cy + dy);
                    }
                }
            }
        }
        return null;
    }

    /** Of the cells beside {@code what} in the zone {@code zone}, the one nearest {@code wanted}; null for none. */
    private Coord3D besideCellNearest(PathGrid passage, GameObject who, GameObject what, Coord3D wanted, Zones zones,
            int zone) {
        float cellSize = grid.get().mapCellSize();
        var target = Footprint.of(what);
        float reach = target.shape().footprintRadius() + Solid.of(who.getTemplate()).footprintRadius() + 2f * cellSize;
        var middle = what.getPosition();
        int fromX = passage.toCellX(new Coord3D(middle.x() - reach, middle.y() - reach, 0f));
        int toX = passage.toCellX(new Coord3D(middle.x() + reach, middle.y() + reach, 0f));
        int fromY = passage.toCellY(new Coord3D(middle.x() - reach, middle.y() - reach, 0f));
        int toY = passage.toCellY(new Coord3D(middle.x() + reach, middle.y() + reach, 0f));
        Coord3D best = null;
        float nearest = Float.MAX_VALUE;
        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                if (zone < 0 || zones.zoneOf(x, y) != zone) {
                    continue;
                }
                var centre = passage.cellCenter(x, y);
                float gap = Footprint.of(who, centre).separation(target);
                if (gap < 0f || gap > cellSize) {
                    continue; // on top of it, or not near enough to work on it
                }
                float away = squaredAcross(centre, wanted);
                if (away < nearest) {
                    nearest = away;
                    best = centre;
                }
            }
        }
        return best;
    }

    /** The cell of the zone nearest {@code wanted}, or {@code wanted} where the zone is no zone at all. */
    private static Coord3D reachableNearest(PathGrid passage, Coord3D wanted, Zones zones, int zone, int fromX,
            int fromY) {
        int nearest = zone < 0 ? -1
                : zones.nearestIn(zone, passage.toCellX(wanted), passage.toCellY(wanted), fromX, fromY);
        return nearest < 0 ? wanted : passage.cellCenter(nearest % passage.getWidth(), nearest / passage.getWidth());
    }

    /**
     * A place for {@code mover} to step aside to, out of {@code from}'s {@code way} — the nearest block of its own it
     * may have whose middle keeps both bodies off every leg of the way — claimed as its goal; null for none, or a
     * mover that keeps no cells.
     */
    Coord3D placeAside(GameObject mover, GameObject from, List<Coord3D> way) {
        var cells = groundCells.get();
        if (!cells.keepsCells(mover)) {
            return null;
        }
        var pathGrid = grid.get();
        float room = Solid.of(mover.getTemplate()).footprintRadius() + Solid.of(from.getTemplate()).footprintRadius();
        float cell = pathGrid.getCellSize();
        var block = cells.nearest(mover, mover.getPosition(), candidate -> {
            var middle = cells.pointOf(candidate);
            float reach = room + candidate.half() * cell;
            return clearOf(way, middle, reach);
        });
        if (block == null) {
            return null;
        }
        pathGrid.movers().claimGoal(mover.getId().value(), block);
        return cells.pointOf(block);
    }

    /** Whether a point is further than {@code reach} from every leg of {@code way}. */
    private static boolean clearOf(List<Coord3D> way, Coord3D point, float reach) {
        for (int i = 1; i < way.size(); i++) {
            if (distanceToLeg(point, way.get(i - 1), way.get(i)) < reach) {
                return false;
            }
        }
        return way.size() != 1 || squaredAcross(point, way.getFirst()) >= reach * reach;
    }

    private static float distanceToLeg(Coord3D p, Coord3D a, Coord3D b) {
        float dx = b.x() - a.x();
        float dy = b.y() - a.y();
        float length = dx * dx + dy * dy;
        float t = length <= 0f ? 0f : Math.clamp(((p.x() - a.x()) * dx + (p.y() - a.y()) * dy) / length, 0f, 1f);
        float x = a.x() + dx * t - p.x();
        float y = a.y() + dy * t - p.y();
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float squaredAcross(Coord3D a, Coord3D b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }
}
