package uz.dukeengine.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.pathfind.SceneryBox;
import uz.dukeengine.core.pathfind.SceneryFootprint;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.Solid;

/**
 * The map's scenery that stands in the way on the ground: round pieces — trees, boulders — a route keeps clear of by
 * their true distance, which pieces of them have been taken away, and the boxes — walls — that close cells.
 */
final class Scenery {

    /** How far a mover may come into a piece of scenery's footprint: a touch, as into a mover ahead of it. */
    private static final float TOUCH = 1f;

    private final Supplier<PathGrid> grid;
    private List<SceneryFootprint> footprints = List.of();
    /** The pieces of scenery taken away, by their index. */
    private final TreeSet<Integer> removed = new TreeSet<>();
    private List<SceneryBox> boxes = List.of();

    Scenery(Supplier<PathGrid> grid) {
        this.grid = grid;
    }

    void setFootprints(List<SceneryFootprint> footprints) {
        var given = footprints == null ? List.<SceneryFootprint>of() : footprints;
        var numbered = new ArrayList<SceneryFootprint>(given.size());
        for (int at = 0; at < given.size(); at++) {
            var piece = given.get(at);
            numbered.add(piece.index() >= 0 ? piece
                    : new SceneryFootprint(piece.x(), piece.y(), piece.radius(), at));
        }
        this.footprints = List.copyOf(numbered);
        this.removed.clear(); // a new map's scenery all stands
        lay();
    }

    /** The standing pieces laid on the ground's grid, where there is one. */
    void lay() {
        var pathGrid = grid.get();
        if (pathGrid != null) {
            pathGrid.setSceneryFootprints(removed.isEmpty() ? footprints
                    : footprints.stream().filter(piece -> !removed.contains(piece.index())).toList());
        }
    }

    /** Take a piece away and lay what stands; whether it was standing. */
    boolean remove(int index) {
        if (!removed.add(index)) {
            return false;
        }
        lay();
        return true;
    }

    /** Put a piece back and lay what stands; whether it had been taken away. */
    boolean restore(int index) {
        if (!removed.remove(index)) {
            return false;
        }
        lay();
        return true;
    }

    boolean isRemoved(int index) {
        return removed.contains(index);
    }

    /** The pieces taken away, in order. */
    SortedSet<Integer> removed() {
        return Collections.unmodifiableSortedSet(removed);
    }

    void setRemoved(Collection<Integer> pieces) {
        removed.clear();
        if (pieces != null) {
            removed.addAll(pieces);
        }
        lay();
    }

    int near(Coord3D place, float reach) {
        int best = -1;
        float bestGap = Float.MAX_VALUE;
        for (var piece : footprints) {
            if (removed.contains(piece.index())) {
                continue;
            }
            float dx = piece.x() - place.x();
            float dy = piece.y() - place.y();
            float gap = (float) Math.sqrt(dx * dx + dy * dy) - piece.radius();
            if (gap <= reach && (gap < bestGap || gap == bestGap && piece.index() < best)) {
                best = piece.index();
                bestGap = gap;
            }
        }
        return best;
    }

    List<SceneryFootprint> within(Coord3D place, float reach) {
        var near = new ArrayList<SceneryFootprint>();
        for (var piece : footprints) {
            float dx = piece.x() - place.x();
            float dy = piece.y() - place.y();
            if (dx * dx + dy * dy <= reach * reach && !removed.contains(piece.index())) {
                near.add(piece);
            }
        }
        return near;
    }

    SceneryFootprint piece(int index) {
        for (var piece : footprints) {
            if (piece.index() == index) {
                return piece;
            }
        }
        return null;
    }

    void setBoxes(List<SceneryBox> boxes) {
        this.boxes = boxes == null ? List.of() : List.copyOf(boxes);
    }

    /** Every cell whose middle a piece of scenery's box covers, closed. */
    void closeBoxes(PathGrid pathGrid) {
        for (var box : boxes) {
            float reach = box.length() + box.depth();
            int minX = pathGrid.toCellX(new Coord3D(box.x() - reach, 0f, 0f));
            int maxX = pathGrid.toCellX(new Coord3D(box.x() + reach, 0f, 0f));
            int minY = pathGrid.toCellY(new Coord3D(0f, box.y() - reach, 0f));
            int maxY = pathGrid.toCellY(new Coord3D(0f, box.y() + reach, 0f));
            for (int cy = minY; cy <= maxY; cy++) {
                for (int cx = minX; cx <= maxX; cx++) {
                    var middle = pathGrid.cellCenter(cx, cy);
                    if (box.covers(middle.x(), middle.y())) {
                        pathGrid.setObstacle(cx, cy);
                    }
                }
            }
        }
    }

    /** Whether a ground mover's step from {@code from} to {@code to} goes deeper into a piece than a touch. */
    boolean inTheWay(GameObject mover, Coord3D from, Coord3D to) {
        var pathGrid = grid.get();
        if (pathGrid == null || footprints.isEmpty() || mover.getFloor() != 0
                || mover.hasStatus(ObjectStatus.AIRBORNE)) {
            return false;
        }
        var shape = Solid.of(mover.getTemplate());
        if (shape.isPoint()) {
            return false;
        }
        var here = Footprint.of(mover, from);
        var there = Footprint.of(mover, to);
        return pathGrid.anySceneryNear(to.x(), to.y(), shape.footprintRadius(), piece -> {
            var middle = new Coord3D(piece.x(), piece.y(), 0f);
            float into = piece.radius() - there.distanceTo(middle);
            return into > Math.max(TOUCH, piece.radius() - here.distanceTo(middle)) + 1e-4f;
        });
    }
}
