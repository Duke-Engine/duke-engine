package uz.duke.core.thing;

import java.util.function.Predicate;
import uz.duke.core.event.WorldEvent;
import uz.duke.core.math.Coord3D;
import uz.duke.core.pathfind.Path;
import uz.duke.core.player.Player;
import uz.duke.core.player.Relationship;

/**
 * The simulation context a {@link GameObject} (and its modules) can query —
 * the role SAGE fills with the global {@code TheGameLogic}/{@code ThePlayerList}
 * singletons.
 *
 * <p>Exposed as an interface so modules can find other objects and check
 * diplomacy without the {@code thing} package depending on {@code GameLogic}
 * directly (which depends on {@code thing}) — breaking what would otherwise be a
 * package cycle. {@code GameLogic} implements this.
 */
public interface World {

    /** The live object with this id, or {@code null} if none. */
    GameObject findObject(ObjectId id);

    /** The current logic frame number. */
    int getFrame();

    /** The diplomatic stance player {@code a} holds toward player {@code b}. */
    Relationship getRelationship(int a, int b);

    /** The player at this index, or {@code null} if none. */
    Player getPlayer(int index);

    /** The unit template registered under this name, or {@code null}. */
    ThingTemplate findTemplate(String name);

    /** Create a new object from a template at a position, owned by a player. */
    GameObject spawn(ThingTemplate template, Coord3D position, int playerIndex);

    /**
     * Every live object, in creation order — the whole-world scan a module needs
     * when a range query will not do (tallying a player's assets, say). Prefer
     * {@link #findClosest} or {@link #objectsInRange} when they fit.
     */
    java.util.List<GameObject> getObjects();

    /**
     * The nearest object to {@code center} within {@code range} that satisfies
     * {@code filter}, or {@code null} if none. Lets modules acquire targets
     * without depending on the partition package (avoids a package cycle); the
     * filter is a plain {@link Predicate} so only {@code thing} types leak here.
     */
    GameObject findClosest(Coord3D center, float range, Predicate<GameObject> filter);

    /**
     * The nearest object {@code from} can reach within {@code reach}, measured
     * surface to surface rather than centre to centre — so a wide building counts
     * as soon as its wall is in range.
     *
     * <p>This is what anything with a working distance should use: weapons,
     * repair, capture, boarding. Objects with no {@link Geometry} measure centre
     * to centre, so data written before shapes existed behaves as it always did.
     */
    GameObject findClosestInReach(GameObject from, float reach, Predicate<GameObject> filter);

    /** How far {@code a} is from {@code b}, surface to surface; negative if they overlap. */
    static float reachBetween(GameObject a, GameObject b) {
        return Footprint.of(a).separation(Footprint.of(b));
    }

    /** Every object within {@code range} of {@code center} that satisfies {@code filter}. */
    java.util.List<GameObject> objectsInRange(Coord3D center, float range, Predicate<GameObject> filter);

    /**
     * A navigable path from {@code from} to {@code to} around terrain obstacles,
     * or a direct single-waypoint path when the world has no navigation grid.
     * Empty if no route exists.
     */
    Path findPath(Coord3D from, Coord3D to);

    /**
     * A route for {@code mover} to {@code to}, wide enough for its body and
     * pulled straight wherever it can see ahead.
     *
     * <p>The version to use for anything that actually walks. A path found for a
     * point grazes corners the mover then collides with, and comes out of cell
     * centres as a staircase across ground it could have crossed in a line.
     */
    Path findPath(GameObject mover, Coord3D to);

    /**
     * Increments whenever the navigable world changes shape — a building goes up
     * or comes down.
     *
     * <p>A path is a plan made against the world as it was. Rather than have the
     * simulation hunt down everything holding a stale plan, anything that keeps
     * one remembers this number and notices for itself that its route needs
     * rethinking.
     */
    int getNavigationVersion();

    /**
     * Announce that something happened, for the presentation layer to react to.
     *
     * <p>One way only: nothing in the simulation reads these back, so posting one
     * can never change what happens next. Modules use it to report moments a
     * snapshot cannot express — a shot fired, a wall breached.
     */
    void post(WorldEvent event);

    /**
     * The object that would be in the way if {@code mover} stood at
     * {@code position}, or {@code null} if the space is free — the question a
     * locomotor asks before every step.
     *
     * <p>{@code mover} never blocks itself, and neither do objects that are not
     * physically present: the dead (already leaving the world) and the contained
     * (riding inside something else). An object with no {@link Geometry} blocks
     * nothing and is blocked by nothing.
     */
    GameObject findBlocker(GameObject mover, Coord3D position);

    /**
     * Whether the ground at {@code position} cannot be walked on.
     *
     * <p>The question a locomotor has to ask before every step, and could not.
     * {@link #findBlocker} answers "who is in the way", which is only half of what
     * is in the way: walls were left entirely to pathfinding, so anything that
     * moved for a reason the path did not anticipate — swerving around a
     * neighbour, most of all — could walk into stone unopposed. Once there it was
     * beyond help, because a search that starts on blocked ground has nowhere to
     * begin.
     *
     * <p>False where a world has no navigation grid at all: open ground everywhere.
     */
    boolean isGroundBlocked(Coord3D position);

    /**
     * Somewhere at or near {@code near} where {@code shape} fits without
     * overlapping anything — where to put a newly produced unit, a dropped
     * passenger, or anything else that must appear beside something solid.
     *
     * <p>Searches {@code near} first, then outward in rings, and gives up at
     * {@code searchRadius} by returning {@code near} itself: better a unit that
     * has to walk out of a crowd than a unit that never appears.
     */
    Coord3D findClearPosition(Geometry shape, Coord3D near, float searchRadius);
}
