package uz.dukeengine.rts.module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;

/** A {@link Dock}'s harvesters as the game runs: the one let in, and those waiting by it by their places. */
final class Docking {

    private final GameObject owner;
    private final int waitingPlaces;
    /** The harvester let in, or null. */
    private ObjectId in;
    /** The first frame the next may be let in: the one after the last one's last act. */
    private int freeFrom;
    /** Those waiting by it, by place: a place given up is null, and the next to come takes the lowest such. */
    private final List<ObjectId> places = new ArrayList<>();
    /** Those that have come to their places. */
    private final Set<ObjectId> come = new HashSet<>();

    Docking(GameObject owner, Dock dock) {
        this.owner = owner;
        this.waitingPlaces = dock.waitingPlaces();
    }

    /** Whether {@code harvester} is in, has a place by it, or may have one: a place free, or any number. */
    boolean hasRoomFor(GameObject harvester) {
        tidy();
        var id = harvester.getId();
        return id.equals(in) || places.contains(id) || waitingPlaces == 0 || waiting() < waitingPlaces;
    }

    /**
     * {@code harvester} come to it: a place of its own taken, where it has none and one is free, and let in where
     * nobody is and no harvester at a lower place has come — in the game's frame {@code frame}. Whether it is in.
     */
    boolean letIn(GameObject harvester, int frame) {
        tidy();
        var id = harvester.getId();
        if (id.equals(in)) {
            return true;
        }
        int place = places.indexOf(id);
        if (place < 0) {
            int free = places.indexOf(null);
            if (free >= 0) {
                places.set(free, id);
                place = free;
            } else if (waitingPlaces == 0 || waiting() < waitingPlaces) {
                places.add(id);
                place = places.size() - 1;
            } else {
                return false; // no place: it looks for another
            }
        }
        come.add(id);
        if (in != null || frame < freeFrom) {
            return false;
        }
        for (int lower = 0; lower < place; lower++) {
            if (places.get(lower) != null && come.contains(places.get(lower))) {
                return false; // one at a lower place has come: it goes first
            }
        }
        places.set(place, null);
        come.remove(id);
        in = id;
        return true;
    }

    /** Out: {@code harvester}'s last act done in the game's frame {@code frame}, and the next let in from the next. */
    void out(GameObject harvester, int frame) {
        if (harvester.getId().equals(in)) {
            in = null;
            freeFrom = frame + 1;
        }
    }

    /** {@code harvester} gone from it — given up, or sent elsewhere — its place, or the dock, free at once. */
    void leave(GameObject harvester) {
        var id = harvester.getId();
        if (id.equals(in)) {
            in = null;
        }
        int place = places.indexOf(id);
        if (place >= 0) {
            places.set(place, null);
        }
        come.remove(id);
    }

    /** Those no longer docking here let go: gone from the world, dead, or at work elsewhere. */
    private void tidy() {
        if (in != null && !docking(in)) {
            in = null;
        }
        for (int place = 0; place < places.size(); place++) {
            var id = places.get(place);
            if (id != null && !docking(id)) {
                places.set(place, null);
                come.remove(id);
            }
        }
        while (!places.isEmpty() && places.getLast() == null) {
            places.removeLast();
        }
    }

    private boolean docking(ObjectId id) {
        var world = owner.getWorld();
        var harvester = world == null ? null : world.findObject(id);
        var harvest = harvester == null || harvester.isEffectivelyDead() ? null
                : harvester.findModule(HarvestUpdate.class);
        return harvest != null && harvest.docksAt() == owner;
    }

    private int waiting() {
        int waiting = 0;
        for (var id : places) {
            waiting += id == null ? 0 : 1;
        }
        return waiting;
    }
}
