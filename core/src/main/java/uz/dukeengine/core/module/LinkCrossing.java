package uz.dukeengine.core.module;

import static uz.dukeengine.core.module.Steps.across;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.LinkPlanner;
import uz.dukeengine.core.pathfind.Path;
import uz.dukeengine.core.pathfind.TraversalLink;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;

/**
 * The traversal link a ground mover means to cross, and its crossing (E81): a route walked to the link's near end, a
 * carry over it in the link's frames, and the walk on to its goal from the far end.
 */
final class LinkCrossing {

    /** How near its near end a walker must stand to cross a link. */
    private static final float LINK_REACH = 6f;

    /** A link it means to cross: from which end, to which, and the goal it walks on to from there. */
    private record Plan(TraversalLink link, Coord3D entry, Coord3D exit, Coord3D goal) {
    }

    /** A link being crossed: from where, to where, over how many frames, how high, and the goal walked on to. */
    private static final class Crossing {
        final Coord3D from;
        final Coord3D to;
        final int frames;
        final float arc;
        final Coord3D goal;
        int gone;

        Crossing(Coord3D from, Coord3D to, int frames, float arc, Coord3D goal) {
            this.from = from;
            this.to = to;
            this.frames = frames;
            this.arc = arc;
            this.goal = goal;
        }
    }

    private Plan plan;
    private Crossing crossing;

    /** The link it means to cross, by where it stands in {@code world}'s links, and the crossing it is on. */
    void save(uz.dukeengine.core.save.SaveOut out, World world) {
        out.writeBoolean(plan != null);
        if (plan != null) {
            int link = world.traversalLinks().indexOf(plan.link());
            if (link < 0) {
                throw new uz.dukeengine.core.save.UnsavedModuleException(MoveUpdate.class.getName(), "a mover means to"
                        + " cross a link its world no longer has (setTraversalLinks): a load could not find it");
            }
            out.writeInt(link).writePlace(plan.entry()).writePlace(plan.exit()).writePlace(plan.goal());
        }
        out.writeBoolean(crossing != null);
        if (crossing != null) {
            out.writePlace(crossing.from).writePlace(crossing.to).writeInt(crossing.frames).writeFloat(crossing.arc)
                    .writePlace(crossing.goal).writeInt(crossing.gone);
        }
    }

    void load(uz.dukeengine.core.save.SaveIn in) {
        plan = null;
        if (in.readBoolean()) {
            int link = in.readInt();
            plan = new Plan(in.world().traversalLinks().get(link), in.readPlace(), in.readPlace(), in.readPlace());
        }
        crossing = null;
        if (in.readBoolean()) {
            crossing = new Crossing(in.readPlace(), in.readPlace(), in.readInt(), in.readFloat(), in.readPlace());
            crossing.gone = in.readInt();
        }
    }

    boolean isPlanned() {
        return plan != null;
    }

    boolean isCrossing() {
        return crossing != null;
    }

    /** A link it meant to cross, or was crossing, given up. */
    void forget() {
        plan = null;
        crossing = null;
    }

    /** A new order: whatever link it meant to cross is forgotten, unless the order is to where it was going. */
    void forgetUnlessTo(Coord3D destination) {
        if (plan != null && !plan.goal().equals(destination)) {
            plan = null;
        }
    }

    /**
     * The route to walk instead of {@code path}, planned for {@code owner} to {@code destination}: on its way to a
     * link, to its near end still; else, where a link makes the way shorter, to the near end of the first.
     */
    Path routeVia(World world, GameObject owner, Path path, Coord3D destination, float stepPerFrame) {
        if (plan != null) {
            var toEntry = world.findPath(owner, plan.entry());
            return toEntry != null ? toEntry : path; // planned again on its way to a link: still to its near end
        }
        if (world.traversalLinks().isEmpty()) {
            return path;
        }
        float walk = path.reachesGoal() ? lengthOf(owner.getPosition(), path) : Float.MAX_VALUE;
        var leg = LinkPlanner.first(world.traversalLinks(), owner.getPosition(), destination, world::joined,
                stepPerFrame, walk);
        if (leg == null) {
            return path;
        }
        var toEntry = world.findPath(owner, leg.entry());
        if (toEntry == null || !toEntry.reachesGoal()) {
            return path;
        }
        plan = new Plan(leg.link(), leg.entry(), leg.exit(), destination);
        return toEntry; // to the near end first; the rest planned again from the far end
    }

    /** Carried across the link it stands at — or, where it is not at its near end, given it up and stood. */
    void cross(GameObject owner, float closeEnough) {
        var at = plan;
        plan = null;
        if (across(owner.getPosition(), at.entry()) > Math.max(closeEnough, LINK_REACH)) {
            return; // it never came to the near end
        }
        var link = at.link();
        owner.busy(link.clip(), link.frames());
        // Carried here, as a ScriptedMove carries a thing, rather than by one: no module joins it mid-update.
        crossing = new Crossing(owner.getPosition(), at.exit(), link.frames(), link.arcHeight(), at.goal());
    }

    /**
     * A frame across the link: along the line, an arc over it — and, at the far end, stood there.
     *
     * @return the goal to walk on to once over, or null while still crossing
     */
    Coord3D carry(GameObject owner) {
        var going = crossing;
        going.gone++;
        float t = going.gone / (float) going.frames;
        if (going.gone >= going.frames) {
            crossing = null;
            Steps.stepTo(owner, going.to, -1);
            return going.goal;
        }
        float x = going.from.x() + (going.to.x() - going.from.x()) * t;
        float y = going.from.y() + (going.to.y() - going.from.y()) * t;
        var world = owner.getWorld();
        float ground = world == null ? 0f : world.groundHeight(new Coord3D(x, y, 0f));
        owner.setPosition(new Coord3D(x, y, ground + 4f * going.arc * t * (1f - t)));
        return null;
    }

    /** How long a walk along {@code path} from {@code here} is. */
    private static float lengthOf(Coord3D here, Path path) {
        float length = 0f;
        var at = here;
        for (var point : path.getWaypoints()) {
            length += across(at, point);
            at = point;
        }
        return length;
    }
}
