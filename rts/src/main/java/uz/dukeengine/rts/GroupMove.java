package uz.dukeengine.rts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.pathfind.Pathfinder;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Solid;

/**
 * A group sent to one point, placed as the reference places it ({@code AIGroup::groupMoveToPosition}). A player's click
 * inside the middle of the group gathers it round the point. Otherwise each goes to its place about the member nearest
 * the point, pulled in toward the point and never behind a wall from it. A group going far, or a big one, walks one
 * shared route: its infantry in columns, and its vehicles too where the route bends. Legs are the reference's infantry,
 * wheels and treads its vehicles; everything else goes singly.
 *
 * @param gatherFactor          how much of the group's bounding rectangle, about its middle, a click must fall in to
 *                              gather it ({@code GroupMoveClickToGatherAreaFactor}); 0 never gathers
 * @param distanceRequiresGroup how far from the point, or how widely spread, a group walks one route whatever its size
 *                              ({@code DistanceRequiresGroup})
 * @param minDistanceForGroup   how far from the point its nearest member must be for it to walk one route at all
 *                              ({@code MinDistanceForGroup})
 * @param minInfantryForGroup   the fewest infantry that walk in columns ({@code MinInfantryForGroup})
 * @param minVehiclesForGroup   the fewest vehicles that do ({@code MinVehiclesForGroup})
 */
public record GroupMove(float gatherFactor, float distanceRequiresGroup, float minDistanceForGroup,
        int minInfantryForGroup, int minVehiclesForGroup) implements GroupLayout {

    /** The reference's numbers: GameData.ini's 0.5, and AIData.ini's 500, 100, 3 and 3. */
    public static final GroupMove REFERENCE = new GroupMove(0.5f, 500f, 100f, 3, 3);

    /** How wide the shared route is, in cells ({@code PATH_DIAMETER_IN_CELLS}); points nearer than it are one. */
    private static final int ROUTE_CELLS = 6;
    /** A group gathers only while its extent, in cells, squared is under this. */
    private static final int MOST_CELLS_TO_GATHER = 2000;
    /** More than this many infantry, or vehicles, walk one route however near they are. */
    private static final int MANY_INFANTRY = 6;
    private static final int MANY_VEHICLES = 4;
    /** From this many infantry, five columns; below this many vehicles, two at the end. */
    private static final int FIVE_COLUMNS_FROM = 16;
    private static final int THREE_COLUMNS_FROM = 5;

    private enum Kind { INFANTRY, VEHICLE }

    @Override
    public void send(RtsSimulation world, List<GameObject> units, Coord3D point, boolean click) {
        var movers = units.stream().filter(unit -> unit.getLocomotor() != null && !unit.isContained()).toList();
        if (movers.size() == 1) {
            movers.getFirst().getLocomotor().moveTo(point);
            return;
        }
        if (movers.isEmpty()) {
            return;
        }
        var bounds = Bounds.of(movers);
        if (click && gathers(world, bounds, point)) {
            byNearness(movers, point).forEach(unit -> unit.getLocomotor().moveTo(point));
            return;
        }
        var walked = new HashSet<GameObject>();
        var route = sharedRoute(world, movers, point, bounds);
        if (route != null) {
            walked.addAll(columns(world, route, movers, point, bounds, Kind.INFANTRY));
            walked.addAll(columns(world, route, movers, point, bounds, Kind.VEHICLE));
        }
        spread(world, movers.stream().filter(unit -> !walked.contains(unit)).toList(), point);
    }

    /**
     * Whether a click at {@code point} gathers the group: inside its bounding rectangle scaled about its middle by the
     * gather factor, the group no wider than the reference allows — which measures its width twice, never its height.
     */
    private boolean gathers(RtsSimulation world, Bounds bounds, Coord3D point) {
        if (gatherFactor <= 0f) {
            return false;
        }
        float halfWidth = (bounds.maxX - bounds.minX) * gatherFactor / 2f;
        float halfHeight = (bounds.maxY - bounds.minY) * gatherFactor / 2f;
        float middleX = (bounds.minX + bounds.maxX) / 2f;
        float middleY = (bounds.minY + bounds.maxY) / 2f;
        if (point.x() < middleX - halfWidth || point.x() > middleX + halfWidth
                || point.y() < middleY - halfHeight || point.y() > middleY + halfHeight) {
            return false;
        }
        int cells = (int) ((bounds.maxX - bounds.minX) / world.cellSize());
        return cells * cells < MOST_CELLS_TO_GATHER;
    }

    /**
     * Each where it stands about the member nearest the point, no further out than six times its size: its place
     * pulled toward the point and kept on the point's side of any wall ({@code computeIndividualDestination}).
     */
    private static void spread(RtsSimulation world, List<GameObject> rest, Coord3D point) {
        if (rest.isEmpty()) {
            return;
        }
        var sorted = byNearness(rest, point);
        var anchor = sorted.getFirst().getPosition();
        for (var unit : sorted) {
            float dx = unit.getPosition().x() - anchor.x();
            float dy = unit.getPosition().y() - anchor.y();
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            float most = 6f * Solid.of(unit.getTemplate()).footprintRadius();
            if (length > most) {
                dx *= most / length;
                dy *= most / length;
            }
            var place = new Coord3D(point.x() + dx, point.y() + dy, point.z());
            unit.getLocomotor().moveTo(world.takePlace(unit, place, point));
        }
    }

    /**
     * The route a group walks together, from its member nearest its middle — or none, where each goes singly: its
     * nearest member under the least distance from the point; or all of it near, few, and its infantry each with a
     * clear line to that member ({@code friend_computeGroundPath}).
     */
    private List<Coord3D> sharedRoute(RtsSimulation world, List<GameObject> movers, Coord3D point, Bounds bounds) {
        var grid = world.getPathGrid();
        if (grid == null) {
            return null;
        }
        float nearest = 4f * distanceRequiresGroup * distanceRequiresGroup;
        int infantry = 0;
        int vehicles = 0;
        GameObject middle = null;
        float middleAway = 0f;
        for (var unit : movers) {
            var kind = kindOf(unit);
            if (kind == null) {
                continue;
            }
            if (kind == Kind.INFANTRY) {
                infantry++;
            } else {
                vehicles++;
            }
            nearest = Math.min(nearest, squared(unit.getPosition(), point));
            float away = squared(unit.getPosition(), bounds.middle());
            if (middle == null || away < middleAway) {
                middle = unit;
                middleAway = away;
            }
        }
        if (middle == null) {
            return null;
        }
        float spread = (bounds.maxX - bounds.minX) * (bounds.maxX - bounds.minX)
                + (bounds.maxY - bounds.minY) * (bounds.maxY - bounds.minY);
        float far = distanceRequiresGroup * distanceRequiresGroup;
        if (spread > far) {
            nearest = spread;
        }
        if (nearest < minDistanceForGroup * minDistanceForGroup) {
            return null;
        }
        boolean together = nearest > far || infantry > MANY_INFANTRY || vehicles > MANY_VEHICLES;
        if (!together) {
            together = true;
            for (var unit : movers) {
                if (kindOf(unit) == Kind.INFANTRY
                        && !Pathfinder.isClearLine(grid, unit.getPosition(), middle.getPosition(), 0f)) {
                    together = false;
                }
            }
        }
        if (!together) {
            return null;
        }
        var path = Pathfinder.findPath(grid, middle.getPosition(), point, ROUTE_CELLS * world.cellSize() / 2f);
        if (path.isEmpty()) {
            return null;
        }
        var route = new ArrayList<Coord3D>();
        route.add(middle.getPosition());
        route.addAll(path.getWaypoints());
        return route;
    }

    /**
     * The group's infantry, or its vehicles, walking the shared route in columns and ending in columns across its last
     * leg ({@code friend_moveInfantryToPos}, {@code friend_moveVehicleToPos}); those it sent. Vehicles only on a route
     * that bends more than six cells from its end, and neither when there are too few of them.
     */
    private Set<GameObject> columns(RtsSimulation world, List<Coord3D> route, List<GameObject> movers, Coord3D point,
            Bounds bounds, Kind kind) {
        float cell = world.cellSize();
        float farEnough = ROUTE_CELLS * cell * ROUTE_CELLS * cell;
        var startPoint = route.getFirst();
        int startNode = -1;
        for (int i = 0; i < route.size(); i++) {
            if (squared(route.get(i), startPoint) > farEnough) {
                startNode = i;
                break;
            }
        }
        var endPoint = route.getLast();
        int endNode = -1;
        for (int i = 0; i < route.size(); i++) {
            if (squared(route.get(i), endPoint) > farEnough) {
                endNode = i;
            }
        }
        if (kind == Kind.VEHICLE && endNode == 0) {
            endNode = -1;
        }
        if (startNode < 0 || endNode < 0) {
            return Set.of();
        }
        float[] startVector = unit(route.get(startNode), startPoint);
        float[] endVector = unit(endPoint, route.get(endNode));
        var middle = bounds.middle();

        var units = new ArrayList<GameObject>();
        boolean useEnd = false;
        for (var unit : movers) {
            if (kindOf(unit) != kind) {
                continue;
            }
            units.add(unit);
            if (squared(unit.getPosition(), startPoint) > squared(unit.getPosition(), endPoint)) {
                useEnd = true;
            }
        }
        int n = units.size();
        if (n < (kind == Kind.INFANTRY ? minInfantryForGroup : minVehiclesForGroup)) {
            return Set.of();
        }
        float[] across = useEnd ? endVector : startVector;
        float[] normal = {-across[1], across[0]};
        var byAcross = sortedDescending(units, unit -> along(unit.getPosition(), middle, normal));

        var narrow = new HashMap<GameObject, Integer>();
        var wide = new HashMap<GameObject, Integer>();
        var alongKey = new HashMap<GameObject, Float>();
        for (int index = 0; index < n; index++) {
            var unit = byAcross.get(index);
            int column;
            int wideColumn;
            if (kind == Kind.INFANTRY) {
                column = Math.max(-1, 1 - index / Math.max(1, (n + 1) / 3));
                wideColumn = n < FIVE_COLUMNS_FROM ? column : Math.max(-2, 2 - index / Math.max(1, (n + 3) / 5));
            } else {
                column = 1 - index / Math.max(1, (n + 1) / 2);
                column = column == 0 ? -1 : column;
                wideColumn = n < THREE_COLUMNS_FROM ? column : Math.max(-1, 1 - index / Math.max(1, (n + 1) / 3));
            }
            narrow.put(unit, column);
            wide.put(unit, wideColumn);
            float back = kind == Kind.INFANTRY ? -100f * cell * stepsBack(unit) : 0f;
            alongKey.put(unit, back + along(unit.getPosition(), middle, across));
        }
        var ordered = sortedDescending(units, alongKey::get);
        evenOut(ordered, narrow, wide, kind, n);

        var sent = new HashSet<GameObject>();
        int[] inColumn = new int[5];
        float[] endNormal = {-endVector[1], endVector[0]};
        for (var unit : ordered) {
            int column = narrow.get(unit);
            int wideColumn = wide.get(unit);
            int factor = inColumn[wideColumn + 2]++;
            var way = cornerPoints(route, startNode, unit, column, factor, kind, cell, farEnough);

            float endOffset = kind == Kind.INFANTRY ? 2.2f * cell : n < THREE_COLUMNS_FROM ? 1.5f * cell : 3.2f * cell;
            int clamped = kind == Kind.INFANTRY ? Math.clamp(wideColumn, -2, 2) : Math.clamp(wideColumn, -3, 3);
            float x = point.x() + endOffset * clamped * endNormal[0];
            float y = point.y() + endOffset * clamped * endNormal[1];
            if ((factor & 1) != 0) {
                x += cell * endNormal[0];
                y += cell * endNormal[1];
            }
            float back = factor * endOffset + (kind == Kind.INFANTRY ? stepsBack(unit) * cell : 0f);
            x -= back * endVector[0];
            y -= back * endVector[1];
            while (!way.isEmpty()) {
                var last = way.getLast();
                if ((x - last.x()) * endVector[0] + (y - last.y()) * endVector[1] > 0f) {
                    break;
                }
                way.removeLast(); // a corner behind where it ends
            }
            unit.getLocomotor().moveThrough(way, new Coord3D(x, y, point.z()));
            sent.add(unit);
        }
        return sent;
    }

    /**
     * The columns evened out, each to the least-filled column nearest the one it was given: infantry by where their
     * locomotors walk, the front first; vehicles in three passes over them all — the reference's loop over the three
     * priorities, whose test for vehicles it compiled out.
     */
    private static void evenOut(List<GameObject> ordered, Map<GameObject, Integer> narrow,
            Map<GameObject, Integer> wide, Kind kind, int n) {
        int[] narrowCount = new int[3];
        int[] wideCount = new int[kind == Kind.INFANTRY ? 5 : 3];
        for (var priority : List.of(MoveUpdate.MovePriority.FRONT, MoveUpdate.MovePriority.MIDDLE,
                MoveUpdate.MovePriority.BACK)) {
            for (var unit : ordered) {
                if (kind == Kind.INFANTRY && movePriority(unit) != priority) {
                    continue;
                }
                int column = leastFilledNearest(narrowCount, 1 + narrow.get(unit), kind == Kind.INFANTRY ? 1 : 2) - 1;
                int wideHalf = wideCount.length / 2;
                int wideColumn = leastFilledNearest(wideCount, wideHalf + wide.get(unit), 1) - wideHalf;
                if (kind == Kind.INFANTRY ? n < FIVE_COLUMNS_FROM : n < THREE_COLUMNS_FROM) {
                    wideColumn = column;
                }
                narrow.put(unit, column);
                wide.put(unit, wideColumn);
            }
        }
    }

    /** Of the columns {@code 0, step, 2·step…} holding fewest, the one nearest {@code wanted} — the first of a tie — filled. */
    private static int leastFilledNearest(int[] count, int wanted, int step) {
        int least = Integer.MAX_VALUE;
        for (int i = 0; i < count.length; i += step) {
            least = Math.min(least, count[i]);
        }
        int best = -1;
        int bestAway = Integer.MAX_VALUE;
        for (int i = 0; i < count.length; i += step) {
            if (count[i] == least && Math.abs(wanted - i) < bestAway) {
                bestAway = Math.abs(wanted - i);
                best = i;
            }
        }
        count[best]++;
        return best;
    }

    /**
     * Where {@code unit} walks the route's corners: each point beyond six cells of the start, set across the leg round
     * it into the unit's column — 21 apart for infantry, 30 for vehicles — and half a cell to one side or the other by
     * its place in the column; one that would take it backwards left out.
     */
    private static List<Coord3D> cornerPoints(List<Coord3D> route, int startNode, GameObject unit, int column,
            int factor, Kind kind, float cell, float farEnough) {
        var way = new ArrayList<Coord3D>();
        float offset = kind == Kind.INFANTRY ? 2.1f * cell : 1.5f * cell;
        int previous = 0;
        var from = unit.getPosition();
        for (int node = startNode; node < route.size(); node++) {
            var dest = route.get(node);
            int next = -1;
            for (int i = node + 1; i < route.size(); i++) {
                if (squared(route.get(i), dest) > farEnough) {
                    next = i;
                    break;
                }
            }
            if (next < 0) {
                break;
            }
            float cornerX = route.get(next).x() - route.get(previous).x();
            float cornerY = route.get(next).y() - route.get(previous).y();
            float[] cornerNormal = unit(new Coord3D(-cornerY, cornerX, 0f), new Coord3D(0f, 0f, 0f));
            float side = offset * column + ((factor & 1) != 0 ? 0.5f : -0.5f) * cell;
            var there = new Coord3D(dest.x() + side * cornerNormal[0], dest.y() + side * cornerNormal[1], dest.z());
            if (cornerX * (there.x() - from.x()) + cornerY * (there.y() - from.y()) > 0f) {
                way.add(there);
                from = there;
            }
            for (int i = previous + 1; i <= node; i++) {
                if (squared(route.get(i), route.get(node + 1)) > farEnough) {
                    previous = i;
                }
            }
        }
        return way;
    }

    /** How many steps behind the front its locomotor walks in a group: 0 at the front, 1 in the middle, 2 at the back. */
    private static int stepsBack(GameObject unit) {
        return MoveUpdate.MovePriority.FRONT.ordinal() - movePriority(unit).ordinal();
    }

    private static MoveUpdate.MovePriority movePriority(GameObject unit) {
        return unit.getLocomotor() instanceof MoveUpdate walking ? walking.movePriority()
                : MoveUpdate.MovePriority.MIDDLE;
    }

    private static Kind kindOf(GameObject unit) {
        if (!(unit.getLocomotor() instanceof MoveUpdate walking)) {
            return null;
        }
        return switch (walking.gait()) {
            case LEGS -> Kind.INFANTRY;
            case TREADS, WHEELS -> Kind.VEHICLE;
            case OTHER -> null;
        };
    }

    private static List<GameObject> byNearness(List<GameObject> units, Coord3D point) {
        return units.stream().sorted(Comparator.<GameObject>comparingDouble(unit -> squared(unit.getPosition(), point))
                .thenComparingInt(unit -> unit.getId().value())).toList();
    }

    private static List<GameObject> sortedDescending(List<GameObject> units,
            java.util.function.Function<GameObject, Float> key) {
        return units.stream().sorted(Comparator.<GameObject>comparingDouble(unit -> -key.apply(unit))
                .thenComparingInt(unit -> unit.getId().value())).toList();
    }

    private static float along(Coord3D at, Coord3D from, float[] direction) {
        return (at.x() - from.x()) * direction[0] + (at.y() - from.y()) * direction[1];
    }

    /** The direction from {@code from} to {@code to}, of length 1 — or none, where they are one point. */
    private static float[] unit(Coord3D to, Coord3D from) {
        float dx = to.x() - from.x();
        float dy = to.y() - from.y();
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        return length == 0f ? new float[] {0f, 0f} : new float[] {dx / length, dy / length};
    }

    private static float squared(Coord3D a, Coord3D b) {
        float dx = a.x() - b.x();
        float dy = a.y() - b.y();
        return dx * dx + dy * dy;
    }

    /** Where a group stands: the corners of the rectangle round it, and its middle — the mean of where they stand. */
    private record Bounds(float minX, float minY, float maxX, float maxY, Coord3D middle) {
        static Bounds of(List<GameObject> movers) {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            float sumX = 0f;
            float sumY = 0f;
            for (var unit : movers) {
                var at = unit.getPosition();
                minX = Math.min(minX, at.x());
                minY = Math.min(minY, at.y());
                maxX = Math.max(maxX, at.x());
                maxY = Math.max(maxY, at.y());
                sumX += at.x();
                sumY += at.y();
            }
            return new Bounds(minX, minY, maxX, maxY, new Coord3D(sumX / movers.size(), sumY / movers.size(), 0f));
        }
    }
}
