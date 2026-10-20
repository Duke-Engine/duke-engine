package uz.dukeengine.core.module;

import static uz.dukeengine.core.module.Steps.across;
import static uz.dukeengine.core.module.Steps.angleBetween;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.Path;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;

/**
 * The route a ground mover follows: its waypoints, how far along them it is, whether it waits its turn for one, and
 * where on it a mover on a gait steers — the reference's {@code Path} as its {@code AIUpdateInterface} walks it.
 */
final class Route {

    /** How far off its route, in cells, a mover steers straight at it rather than along it: the reference's three. */
    private static final float PATH_ERROR_CELLS = 3f;
    /** Facing its way within this, it is not turning: the reference's {@code PI / 30}, 6 degrees. */
    static final float FACING_ITS_WAY = (float) (StrictMath.PI / 30.0);

    private List<Coord3D> waypoints = List.of();
    /** Where it stood when it was given its waypoints: where the first leg of its route starts. */
    private Coord3D from;
    /** The route the waypoints came from, for the floor each is on; null for a way given rather than planned. */
    private Path path;
    private int index;
    /** The world's shape when this route was planned. */
    private int navigationVersion;
    /** Whether the route ends where it was sent, rather than as near to it as it gets. */
    private boolean goalReachable = true;
    /**
     * Whether it asked for a route while the frame's searching was spent, and waits its turn: it keeps walking the
     * route it had, or stands, and asks again each frame until it is given one.
     */
    private boolean waiting;
    /** How many routes it has been given, for a test to count. */
    private int plans;

    /** A planned route to follow from its first waypoint. */
    void follow(Path planned, int version) {
        plans++;
        waypoints = planned.getWaypoints();
        path = planned;
        goalReachable = planned.reachesGoal();
        navigationVersion = version;
        index = 0;
    }

    /** Straight to {@code to}, with no world to plan a route in. */
    void straight(Coord3D to) {
        waypoints = List.of(to);
        path = null;
        goalReachable = true;
    }

    /** Straight to {@code way}, a way given rather than planned, from where it stands. */
    void given(Coord3D way, Coord3D standing, int version) {
        waypoints = List.of(way);
        from = standing;
        path = null;
        goalReachable = true;
        navigationVersion = version;
    }

    /** Set off along it from {@code standing}. */
    void setOff(Coord3D standing) {
        index = 0;
        from = standing;
    }

    /** No route, and none waited for. */
    void clear() {
        waiting = false;
        waypoints = List.of();
        path = null;
        index = 0;
    }

    boolean waiting() {
        return waiting;
    }

    void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }

    /** Whether it has waypoints left to walk. */
    boolean walking() {
        return index < waypoints.size();
    }

    boolean goalReachable() {
        return goalReachable;
    }

    int plans() {
        return plans;
    }

    int index() {
        return index;
    }

    Coord3D next() {
        return waypoints.get(index);
    }

    /** On to the next waypoint. */
    void pass() {
        index++;
    }

    boolean onLastLeg() {
        return index == waypoints.size() - 1;
    }

    /** Walked to its end where it stands. */
    void walkedOut() {
        index = waypoints.size();
    }

    /** The floor the next waypoint is on, or -1 for a way given rather than planned. */
    int floorOfNext() {
        return path == null ? -1 : path.floorOf(index);
    }

    /** True when the world has changed shape since it was planned, so its waypoints may lead through what is now solid. */
    boolean isStale(World world) {
        return world != null && world.getNavigationVersion() != navigationVersion;
    }

    /** The way on from {@code position}: it, and the waypoints left. */
    List<Coord3D> ahead(Coord3D position) {
        var way = new ArrayList<Coord3D>();
        way.add(position);
        way.addAll(waypoints.subList(Math.min(index, waypoints.size()), waypoints.size()));
        return way;
    }

    /** How far it goes on past the next waypoint: the legs between the waypoints after it. */
    float legsAfterNext() {
        float length = 0f;
        for (int i = index + 1; i < waypoints.size(); i++) {
            var a = waypoints.get(i - 1);
            var b = waypoints.get(i);
            length += (float) Math.sqrt((b.x() - a.x()) * (b.x() - a.x()) + (b.y() - a.y()) * (b.y() - a.y()));
        }
        return length;
    }

    /** Whether it has to turn to face the way on: more than 6 degrees off its next waypoint ({@code needToRotate}). */
    boolean needsToTurn(GameObject owner) {
        if (waiting || index >= waypoints.size()) {
            return waiting;
        }
        var next = waypoints.get(index);
        var at = owner.getPosition();
        float way = (float) StrictMath.atan2(next.y() - at.y(), next.x() - at.x());
        return Math.abs(angleBetween(owner.getOrientation(), way)) > FACING_ITS_WAY;
    }

    /**
     * Where a mover on a gait steers this frame, as the reference's {@code Path::computePointOnPath}: the segment of
     * its route nearest it is found, from the one it is on — a waypoint it went past, or could not turn onto, is passed
     * rather than circled — and it steers at that segment's end where it walks straight there, or, past half of it, at
     * the middle of the next; off its route, at a point on it, the nearer the farther off it is.
     */
    Coord3D steerAt(GameObject owner, Coord3D position) {
        int last = waypoints.size() - 1;
        int nearest = -1;
        float nearestSq = Float.MAX_VALUE;
        for (int end = index; end <= last; end++) {
            var start = legStart(end, position);
            var to = waypoints.get(end);
            float sx = to.x() - start.x();
            float sy = to.y() - start.y();
            float length = (float) Math.sqrt(sx * sx + sy * sy);
            if (length <= 0f) {
                continue;
            }
            float along = (sx * (position.x() - start.x()) + sy * (position.y() - start.y())) / length;
            if (along > length && end != last) {
                continue; // past this leg's end: the next has it, a sharp bend's start or a gentle one's middle
            }
            float t = Math.clamp(along, 0f, length) / length;
            float dx = position.x() - (start.x() + sx * t);
            float dy = position.y() - (start.y() + sy * t);
            if (dx * dx + dy * dy < nearestSq) {
                nearestSq = dx * dx + dy * dy;
                nearest = end;
            }
        }
        if (nearest < 0) {
            return waypoints.get(index);
        }
        if (nearest > index) {
            index = nearest; // the waypoints before it passed
        }
        var start = legStart(nearest, position);
        var to = waypoints.get(nearest);
        float length = across(to, start);
        float nx = (to.x() - start.x()) / length;
        float ny = (to.y() - start.y()) / length;
        float tx = position.x() - start.x();
        float ty = position.y() - start.y();
        float along = Math.max(0f, nx * tx + ny * ty);
        float offSq = tx * tx + ty * ty - along * along;
        float off = offSq <= 0f ? 0f : (float) Math.sqrt(offSq);
        float k = Math.min(1f, off / (PATH_ERROR_CELLS * Steps.cellSize(owner)));
        var world = owner.getWorld();
        if (world == null || world.walksStraight(owner, position, to)) {
            boolean veryClose = length - along < 1f;
            boolean ahead = veryClose || along > length * 0.5f && oneFloor(nearest);
            if (ahead && nearest < last) {
                var next = waypoints.get(nearest + 1);
                var middle = new Coord3D((to.x() + next.x()) * 0.5f, (to.y() + next.y()) * 0.5f, to.z());
                if (veryClose || world == null || world.walksStraight(owner, position, middle)) {
                    return middle;
                }
            }
            return to;
        }
        if (k > 0.5f) {
            float halfway = along + 0.5f * (length - along);
            var onIt = new Coord3D(start.x() + nx * halfway, start.y() + ny * halfway, to.z());
            if (world.walksStraight(owner, position, onIt)) {
                return onIt;
            }
        }
        along += (1f - k) * (length - along);
        var onIt = new Coord3D(start.x() + nx * along, start.y() + ny * along, to.z());
        if (Math.abs(position.x() - onIt.x()) < 1f && Math.abs(position.y() - onIt.y()) < 1f && nearest < last) {
            return waypoints.get(nearest + 1);
        }
        return onIt;
    }

    /** Where the leg ending at waypoint {@code end} starts: the waypoint before, or where it set off. */
    private Coord3D legStart(int end, Coord3D position) {
        return end > 0 ? waypoints.get(end - 1) : from != null ? from : position;
    }

    /** Whether waypoint {@code at} and the next are on one floor: the reference steers ahead past no change. */
    private boolean oneFloor(int at) {
        return path == null || at + 1 >= waypoints.size() || path.floorOf(at) == path.floorOf(at + 1);
    }
}
