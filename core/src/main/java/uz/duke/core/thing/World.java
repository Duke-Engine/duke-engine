package uz.duke.core.thing;

import java.util.function.Predicate;
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

    /** Every object within {@code range} of {@code center} that satisfies {@code filter}. */
    java.util.List<GameObject> objectsInRange(Coord3D center, float range, Predicate<GameObject> filter);

    /**
     * A navigable path from {@code from} to {@code to} around terrain obstacles,
     * or a direct single-waypoint path when the world has no navigation grid.
     * Empty if no route exists.
     */
    Path findPath(Coord3D from, Coord3D to);
}
