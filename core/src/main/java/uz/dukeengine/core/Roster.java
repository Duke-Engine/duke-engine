package uz.dukeengine.core;

import static uz.dukeengine.core.CreationOrder.indexOf;
import static uz.dukeengine.core.CreationOrder.putIn;
import static uz.dukeengine.core.CreationOrder.takeOut;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import uz.dukeengine.core.partition.PartitionManager;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.Sighted;
import uz.dukeengine.core.thing.Solid;

/**
 * A world's live things: every one in the order it came in, and the lists a question walks instead of all of them —
 * by id, by owner, those every player sees round, those drawn along a line, those that stand still — each in that
 * order too, and the partition that finds them by where they stand.
 */
final class Roster {

    private final List<GameObject> objects = new ArrayList<>();
    /** The live objects by id — what {@link #find} answers from, where it looked at every object. */
    private final HashMap<Integer, GameObject> byId = new HashMap<>();
    /** Each player's live things, in the order they came into the world. */
    private final HashMap<Integer, List<GameObject>> byPlayer = new HashMap<>();
    /** The live things every player sees round, in the order they came in: lookers whatever their side. */
    private final List<GameObject> seenByAll = new ArrayList<>();
    /** The live things drawn along a line, in the order they came in. */
    private final List<GameObject> spanned = new ArrayList<>();
    /** The live things that stand still and have a shape, in the order they came in: what a route is laid round. */
    private final List<GameObject> stillThings = new ArrayList<>();
    private final PartitionManager partition = PartitionManager.indexed(this::copy);
    /** The copy {@link #copy} hands out, or null once the objects have changed since it was made. */
    private List<GameObject> objectsCopy;
    /** How many things have come into this world: the next one's {@link GameObject#getEntered}. */
    private long nextEntered;

    /** {@code object} comes into the world: last in its order, found by its id and where it stands. */
    void enter(GameObject object) {
        object.entered(nextEntered++);
        objects.add(object);
        byId.putIfAbsent(object.getId().value(), object);
        partition.added(object);
        byPlayer.computeIfAbsent(object.getPlayerIndex(), player -> new ArrayList<>()).add(object);
        if (Sighted.seenByAllOf(object.getTemplate()) > 0f) {
            seenByAll.add(object);
        }
        if (object.getSpan() != null) {
            spanned.add(object);
        }
        if (standsStill(object)) {
            stillThings.add(object);
        }
        objectsCopy = null;
    }

    /** {@code object} leaves every list it is in; whether it was one of the live things. */
    boolean leave(GameObject object) {
        if (!takeOut(objects, object)) {
            return false;
        }
        byId.remove(object.getId().value(), object);
        partition.removed(object);
        var owned = byPlayer.get(object.getPlayerIndex());
        if (owned != null) {
            takeOut(owned, object);
        }
        takeOut(seenByAll, object);
        takeOut(spanned, object);
        takeOut(stillThings, object);
        objectsCopy = null;
        return true;
    }

    void clear() {
        objects.clear();
        byId.clear();
        partition.cleared();
        byPlayer.clear();
        seenByAll.clear();
        spanned.clear();
        stillThings.clear();
        objectsCopy = null;
    }

    void ownerChanged(GameObject thing, int was) {
        var before = byPlayer.get(was);
        if (before == null || !takeOut(before, thing)) {
            return; // not one of this world's live things
        }
        putIn(byPlayer.computeIfAbsent(thing.getPlayerIndex(), player -> new ArrayList<>()), thing);
    }

    void spanChanged(GameObject thing) {
        if (!isHere(thing)) {
            return;
        }
        takeOut(spanned, thing);
        if (thing.getSpan() != null) {
            putIn(spanned, thing);
        }
    }

    void mobilityChanged(GameObject thing) {
        if (!isHere(thing)) {
            return;
        }
        boolean listed = indexOf(stillThings, thing) >= 0;
        if (standsStill(thing) && !listed) {
            putIn(stillThings, thing);
        } else if (!standsStill(thing) && listed) {
            takeOut(stillThings, thing);
        }
    }

    /** Whether {@code thing} may lay anything on the ground: it cannot move, and it has a shape. */
    private static boolean standsStill(GameObject thing) {
        return !thing.isMobile() && !Solid.of(thing.getTemplate()).isPoint();
    }

    /** Whether {@code thing} is one of this world's live things. */
    boolean isHere(GameObject thing) {
        return indexOf(objects, thing) >= 0;
    }

    GameObject find(ObjectId id) {
        return id == null ? null : byId.get(id.value());
    }

    /** The live things themselves, in creation order: the world's own list, changed as the world changes. */
    List<GameObject> list() {
        return objects;
    }

    /** Live objects, in creation order: the same unmodifiable copy until the objects next change. */
    List<GameObject> copy() {
        var copy = objectsCopy;
        if (copy == null) {
            copy = List.copyOf(objects);
            objectsCopy = copy;
        }
        return copy;
    }

    PartitionManager partition() {
        return partition;
    }

    /** Each player's live things, by player. */
    Map<Integer, List<GameObject>> byPlayer() {
        return byPlayer;
    }

    List<GameObject> of(int player) {
        var things = byPlayer.get(player);
        return things == null ? List.of() : Collections.unmodifiableList(things);
    }

    SortedSet<Integer> owners() {
        var owners = new TreeSet<Integer>();
        byPlayer.forEach((player, things) -> {
            if (!things.isEmpty()) {
                owners.add(player);
            }
        });
        return owners;
    }

    List<GameObject> seenByAll() {
        return seenByAll;
    }

    List<GameObject> spanned() {
        return Collections.unmodifiableList(spanned);
    }

    List<GameObject> stillThings() {
        return stillThings;
    }
}
