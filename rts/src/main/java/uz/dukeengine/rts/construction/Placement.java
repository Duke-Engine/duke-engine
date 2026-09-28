package uz.dukeengine.rts.construction;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.Solid;
import uz.dukeengine.core.thing.ThingTemplate;
import uz.dukeengine.core.thing.World;

/**
 * Whether a building may stand at a place, and if not, why.
 *
 * <p>Decided here, on the simulation's side, and only ever <em>shown</em> by a client. A ghost at the cursor
 * is red or green because of this; an order is refused because of this; and the two agree because they are
 * the one question asked of the one world. Every number that makes it a decision is the game's —
 * {@link PlacementRules}.
 */
public final class Placement {

    /** The answer, and the reason when it is no. */
    public enum Fit {
        FITS,
        /** Some of it would be off the map. */
        OFF_THE_MAP,
        /** Within the margin the game keeps clear along the edge. */
        TOO_NEAR_THE_EDGE,
        /**
         * Its middle on ground the placing side has never seen, where the game's rules say its sites need seen ground
         * ({@link PlacementRules#seenGround}) — the reference's {@code LBC_SHROUD}.
         */
        ON_UNSEEN_GROUND,
        /** On ground nothing can be built on. */
        ON_ROCK,
        /** The ground under it rises and falls further than the game allows. */
        TOO_STEEP,
        /** Something that does not move is already standing there. */
        IN_THE_WAY
    }

    private Placement() {
    }

    /**
     * Whether {@code template} may stand at {@code place}, facing {@code facing} degrees, for no side in particular:
     * everything but what a side has seen, which is asked only of a side's site.
     */
    public static Fit check(World world, PathGrid grid, ThingTemplate template, Coord3D place, float facing,
            PlacementRules rules) {
        return check(world, grid, template, place, facing, rules, -1);
    }

    /**
     * Whether {@code template} may stand at {@code place}, facing {@code facing} degrees, as a site of {@code
     * player}'s.
     *
     * <p>The ground is read cell by cell under the footprint — every cell whose middle it covers — and the
     * slope is the highest corner of those cells less the lowest, which is what a building standing across
     * them would have to bridge. What stands there is anything that does not move and takes up room: a
     * building, a rock, a tree. A unit standing there is not in the way; it steps aside.
     *
     * <p>Where the rules say the side's sites need seen ground ({@link PlacementRules#seenGround}), the cell under
     * its middle must be one the side's cells read seen or in sight, as the reference reads the one cell under its
     * site's place. Asked before the ground and what stands on it, so a site in the dark says nothing of either.
     */
    public static Fit check(World world, PathGrid grid, ThingTemplate template, Coord3D place, float facing,
            PlacementRules rules, int player) {
        var shape = Solid.of(template);
        var print = new Footprint(shape, place, (float) StrictMath.toRadians(facing));
        float reach = shape.footprintRadius();
        float cell = grid.getCellSize();
        float wide = grid.getWidth() * cell;
        float deep = grid.getHeight() * cell;
        if (place.x() - reach < 0f || place.y() - reach < 0f || place.x() + reach > wide || place.y() + reach > deep) {
            return Fit.OFF_THE_MAP;
        }
        float margin = rules.edgeMargin();
        if (place.x() - reach < margin || place.y() - reach < margin
                || place.x() + reach > wide - margin || place.y() + reach > deep - margin) {
            return Fit.TOO_NEAR_THE_EDGE;
        }
        if (neverSeen(world, rules, player, place)) {
            return Fit.ON_UNSEEN_GROUND;
        }
        int fromX = Math.max(0, (int) ((place.x() - reach) / cell));
        int toX = Math.min(grid.getWidth() - 1, (int) ((place.x() + reach) / cell));
        int fromY = Math.max(0, (int) ((place.y() - reach) / cell));
        int toY = Math.min(grid.getHeight() - 1, (int) ((place.y() + reach) / cell));
        for (int cy = fromY; cy <= toY; cy++) {
            for (int cx = fromX; cx <= toX; cx++) {
                if (print.contains(new Coord3D((cx + 0.5f) * cell, (cy + 0.5f) * cell, 0f))
                        && grid.isTerrainBlocked(cx, cy)) {
                    return Fit.ON_ROCK;
                }
            }
        }
        if (grid.isTerrainBlocked(grid.toCellX(place), grid.toCellY(place))) {
            return Fit.ON_ROCK; // a footprint too small to cover a cell's middle still stands on one
        }
        if (riseUnder(grid, print, place, reach, cell) > rules.maxRise()) {
            return Fit.TOO_STEEP;
        }
        // In the order the world holds them, which is creation order: the answer never depends on it, but a
        // question asked the same way twice is a question that cannot be answered two ways.
        for (var other : world.getObjects()) {
            if (refuses(other, rules) && Footprint.of(other).overlaps(print)) {
                return Fit.IN_THE_WAY;
            }
        }
        return Fit.FITS;
    }

    /**
     * Whether a site of {@code player}'s at {@code place} stands on ground its side has never seen, where the rules
     * ask it of that side: a person's side, or every side. The world's own cells, the same on every machine; none
     * kept, nothing refused.
     */
    private static boolean neverSeen(World world, PlacementRules rules, int player, Coord3D place) {
        if (player < 0 || rules.seenGround() == PlacementRules.SeenGround.NOBODY
                || !(world instanceof uz.dukeengine.rts.RtsSimulation rts) || rts.getSightCells() == null) {
            return false;
        }
        var side = rts.getRtsPlayer(player);
        if (rules.seenGround() == PlacementRules.SeenGround.PEOPLE && side != null && side.isComputer()) {
            return false; // a computer's site is sought with the shroud left out, as the reference's own search is
        }
        return rts.getSightCells().sight(player, place) == uz.dukeengine.core.SightCells.Sight.NEVER_SEEN;
    }

    /**
     * Whether {@code other} refuses a site standing over it: a thing that does not move, standing and taking up room,
     * and of no kind the rules let a site stand over ({@link PlacementRules#standsOver}).
     */
    static boolean refuses(uz.dukeengine.core.thing.GameObject other, PlacementRules rules) {
        if (other.isMobile() || other.isEffectivelyDead() || other.getGeometry() == null
                || other.getGeometry().footprintRadius() <= 0f) {
            return false;
        }
        for (var kind : rules.standsOver()) {
            if (other.isKindOf(kind)) {
                return false;
            }
        }
        return true;
    }

    /**
     * How far the ground rises and falls under a footprint: the highest point of it less the lowest, read at
     * the footprint's own outline and at every corner of the grid inside it.
     *
     * <p>The outline as well as the grid, because a small building can sit between four grid corners and
     * cover none of them, and the ground it stands on is then read nowhere at all; and the grid as well as
     * the outline, because a large one's middle can be a hill its edges never see. The outline is the
     * shape's own — a box's four corners, a circle's rim — and not the circle that bounds it, which for a
     * square building reaches a third again past its walls and reads ground it does not stand on.
     */
    private static float riseUnder(PathGrid grid, Footprint print, Coord3D place, float reach, float cell) {
        var points = outline(print);
        points.add(place);
        for (float gy = (float) Math.ceil((place.y() - reach) / cell) * cell; gy <= place.y() + reach; gy += cell) {
            for (float gx = (float) Math.ceil((place.x() - reach) / cell) * cell; gx <= place.x() + reach;
                    gx += cell) {
                var corner = new Coord3D(gx, gy, 0f);
                if (print.contains(corner)) {
                    points.add(corner);
                }
            }
        }
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (var point : points) {
            float h = grid.reliefHeight(point);
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        return high - low;
    }

    /** Points on a footprint's own edge: a box's corners, turned as it is turned, or eight round a circle. */
    private static java.util.List<Coord3D> outline(Footprint print) {
        var points = new java.util.ArrayList<Coord3D>();
        var at = print.center();
        if (print.shape() instanceof uz.dukeengine.core.thing.Geometry.Box box) {
            // StrictMath: this decides whether an order is accepted, which every machine must decide alike.
            float cos = (float) StrictMath.cos(print.orientation());
            float sin = (float) StrictMath.sin(print.orientation());
            for (int corner = 0; corner < 4; corner++) {
                float across = (corner % 2 == 0 ? 1f : -1f) * box.majorRadius();
                float along = (corner / 2 == 0 ? 1f : -1f) * box.minorRadius();
                points.add(new Coord3D(at.x() + across * cos - along * sin, at.y() + across * sin + along * cos, 0f));
            }
            return points;
        }
        float r = print.shape().footprintRadius();
        float slant = r * (float) StrictMath.sqrt(0.5);
        float[][] round = {{r, 0f}, {-r, 0f}, {0f, r}, {0f, -r}, {slant, slant}, {slant, -slant}, {-slant, slant},
            {-slant, -slant}};
        for (var offset : round) {
            points.add(new Coord3D(at.x() + offset[0], at.y() + offset[1], 0f));
        }
        return points;
    }
}
