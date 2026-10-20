package uz.dukeengine.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.ObstacleRules;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Geometry;
import uz.dukeengine.core.thing.Solid;

/**
 * What the still things lay on the ground's grid — closed cells, a class of ground, a circle kept off — as the game's
 * {@link ObstacleRules} say, with the scenery's boxes: laid again only when something that goes into it changed.
 */
final class Obstacles {

    private final Roster roster;
    private final Scenery scenery;
    private final Supplier<PathGrid> grid;
    private boolean dirty = true;
    /** What each still thing laid on the ground when it was last laid, or null for a ground to be laid afresh. */
    private List<Laying> lastLaid;
    /** What of the still things is in the way of a route. */
    private ObstacleRules rules = ObstacleRules.EVERYTHING;

    Obstacles(Roster roster, Scenery scenery, Supplier<PathGrid> grid) {
        this.roster = roster;
        this.scenery = scenery;
        this.grid = grid;
    }

    /** Something that may change what is laid has changed: looked at again before the next route. */
    void changed() {
        dirty = true;
    }

    /** Laid again before the next route, whatever the still things are. */
    void layAfresh() {
        dirty = true;
        lastLaid = null;
    }

    /** Lay the ground whole at its next laying, as though nothing had been laid. */
    void forget() {
        lastLaid = null;
    }

    void setRules(ObstacleRules rules) {
        this.rules = rules == null ? ObstacleRules.EVERYTHING : rules;
        layAfresh();
    }

    ObstacleRules rules() {
        return rules;
    }

    void wordsChanged() {
        if (!rules.laid().isEmpty()) {
            dirty = true; // a ruin's word laying its rubble, as the reference lays it again as it falls
        }
    }

    /**
     * Bake every immobile object into the navigation grid's obstacle layer, so
     * paths route around buildings instead of into them.
     *
     * <p>Rebuilt wholesale rather than tracked incrementally: the set is small
     * (only things that cannot move), and a full rebuild cannot drift out of sync
     * with the world the way a running tally can. Runs only when something has
     * actually changed.
     *
     * <p>A cell counts as occupied when the object's outline comes within half a
     * cell of the cell's centre. Erring toward under-blocking is deliberate: a
     * cell wrongly left open is caught by {@code MoveUpdate}'s per-step collision
     * check, whereas a cell wrongly closed can seal a building's own doorway.
     *
     * <p>Only what the game's {@link ObstacleRules} put in the way is laid, and a fence along its line alone; a still
     * thing turned or moved has its footprint laid again.
     */
    void refresh() {
        var pathGrid = grid.get();
        if (!dirty || pathGrid == null) {
            return;
        }
        dirty = false;
        var stillThings = roster.stillThings();
        var laid = new ArrayList<Laying>(stillThings.size());
        for (var object : stillThings) {
            laid.add(laidNow(object));
        }
        if (laid.equals(lastLaid)) {
            return; // what lays anything lays what it laid last: laid again, the ground would come out the same
        }
        lastLaid = laid;
        pathGrid.beginObstacles();
        float cellSize = pathGrid.getCellSize();
        float halfCell = cellSize * 0.5f;
        boolean finer = pathGrid.cellsPerMapCell() > 1;
        for (var still : laid) {
            if (!still.laying()) {
                continue; // a building lying dead while its death plays out is in nobody's way
            }
            var object = still.thing();
            var shape = Solid.of(object.getTemplate());
            if (object.getTemplate() instanceof Solid solid && solid.fenceWidth() > 0f) {
                layFence(pathGrid, object, solid.fenceWidth(), solid.fenceOffset());
                continue;
            }
            var ground = still.ground(); // a class it lays over its footprint instead of blocking it, or none
            if (ground == null && finer && round(shape)) {
                // Walked finer, a round thing is kept off by its true distance, as scenery is, and closes no cell: a
                // body goes between two where it fits, and comes up to one as near as its own outline.
                var at = object.getPosition();
                pathGrid.setObstacleCircle(at.x(), at.y(), shape.footprintRadius());
                continue;
            }
            var footprint = Footprint.of(object);
            var position = object.getPosition();
            float reach = shape.footprintRadius() + halfCell;
            int minX = (int) Math.floor((position.x() - reach) / cellSize);
            int maxX = (int) Math.floor((position.x() + reach) / cellSize);
            int minY = (int) Math.floor((position.y() - reach) / cellSize);
            int maxY = (int) Math.floor((position.y() + reach) / cellSize);
            for (int cy = minY; cy <= maxY; cy++) {
                for (int cx = minX; cx <= maxX; cx++) {
                    if (footprint.distanceTo(pathGrid.cellCenter(cx, cy)) > halfCell) {
                        continue;
                    }
                    if (ground != null) {
                        pathGrid.setLaid(cx, cy, ground);
                    } else {
                        pathGrid.setObstacle(cx, cy);
                    }
                }
            }
        }
        scenery.closeBoxes(pathGrid);
        pathGrid.commitObstacles();
    }

    /**
     * What a still thing lays on the ground, as far as its own state decides it: whether it lays anything — alive, out in
     * the open, in the way by the game's rules — the class it lays, where it stands and which way it faces. The ground is
     * laid again only where one of these changed, or the still things did, for nothing else goes into it.
     */
    private record Laying(GameObject thing, boolean laying, String ground, float x, float y, float orientation, float cos,
            float sin) {
    }

    private Laying laidNow(GameObject object) {
        if (object.isEffectivelyDead() || object.isContained() || !inTheWay(object)) {
            return new Laying(object, false, null, 0f, 0f, 0f, 0f, 0f);
        }
        var at = object.getPosition();
        return new Laying(object, true, laidBy(object), at.x(), at.y(), object.getOrientation(), object.getFacingCos(),
                object.getFacingSin());
    }

    private static boolean round(Geometry shape) {
        return switch (shape) {
            case Geometry.Cylinder _, Geometry.Sphere _ -> true;
            case Geometry.Box _ -> false;
        };
    }

    /**
     * Whether {@code thing} holds a mover's step: another mover, or a still thing the game's {@link ObstacleRules}
     * put in the way — the reference's movers pass every other still thing ({@code
     * Pathfinder::classifyObjectFootprint}, {@code AIUpdateInterface::processCollision}) — and not one laying a class
     * of ground instead, which the ground's classes answer for.
     */
    boolean holdsAStep(GameObject thing) {
        return thing.isMobile() || inTheWay(thing) && laidBy(thing) == null;
    }

    /** The class {@code object} lays over its footprint instead of blocking it, as the game's rules say; or null. */
    private String laidBy(GameObject object) {
        for (var rule : rules.laid()) {
            if (rule.word() != null && object.getConditions().contains(rule.word())
                    || rule.kind() != null && object.isKindOf(rule.kind())) {
                return rule.ground();
            }
        }
        return null;
    }

    private boolean inTheWay(GameObject object) {
        for (var kind : rules.outOfTheWay()) {
            if (object.isKindOf(kind)) {
                return false;
            }
        }
        if (!rules.inTheWay().isEmpty() && rules.inTheWay().stream().noneMatch(object::isKindOf)) {
            return false;
        }
        if (rules.aboveGround() <= 0f) {
            return true;
        }
        var pathGrid = grid.get();
        var at = object.getPosition();
        return at.z() - (pathGrid == null ? 0f : pathGrid.groundHeight(at)) <= rules.aboveGround();
    }

    /**
     * A fence in the way along its line alone — the reference's {@code Pathfinder::classifyFence}: points half a cell
     * apart along its facing, from its offset behind its position for its width, each closing the cell it falls in.
     */
    private static void layFence(PathGrid pathGrid, GameObject object, float width, float offset) {
        float cell = pathGrid.getCellSize();
        float step = cell * 0.5f;
        float halfThick = cell / 10f;
        double angle = object.getOrientation();
        float c = (float) StrictMath.cos(angle);
        float s = (float) StrictMath.sin(angle);
        int along = (int) Math.ceil(width / step);
        int across = (int) Math.ceil(2f * halfThick / step);
        var at = object.getPosition();
        float rowX = at.x() - offset * c - halfThick * s;
        float rowY = at.y() + halfThick * c - offset * s;
        for (int row = 0; row < across; row++, rowX += s * step, rowY -= c * step) {
            float x = rowX;
            float y = rowY;
            for (int point = 0; point < along; point++, x += c * step, y += s * step) {
                var there = new Coord3D(x, y, 0f);
                pathGrid.setObstacle(pathGrid.toCellX(there), pathGrid.toCellY(there));
            }
        }
    }
}
