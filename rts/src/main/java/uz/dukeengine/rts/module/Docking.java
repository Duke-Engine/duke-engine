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
    private final Dock dock;
    private final int waitingPlaces;
    /** The harvester let in, or null. */
    private ObjectId in;
    /** The first frame the next may be let in: the one after the last one's last act. */
    private int freeFrom;
    /** Those waiting by it, by place: a place given up is null, and the next to come takes the lowest such. */
    private final List<ObjectId> places = new ArrayList<>();
    /** Those that have come to their places. */
    private final Set<ObjectId> come = new HashSet<>();
    /** Closed: nobody new takes a place, and nobody is let in. */
    private boolean closed;

    Docking(GameObject owner, Dock dock) {
        this.owner = owner;
        this.dock = dock;
        this.waitingPlaces = dock.waitingPlaces();
    }

    /** Where its harvesters wait, in the world; null for beside it. */
    uz.dukeengine.core.math.Coord3D waitingPlace() {
        return dock.waitAt() == null ? null : dock.waitAt().in(owner);
    }

    /** Where the one let in loads or banks, in the world; null for where it waited. */
    uz.dukeengine.core.math.Coord3D actingPlace() {
        return dock.actAt() == null ? null : dock.actAt().in(owner);
    }

    /**
     * Close it, or open it again — the reference's {@code DockUpdate::setDockCrippled}, which grants nobody enter: the
     * one in finishes, those waiting keep their places, and nobody new takes one, so a harvester looking for a pile or
     * a depot looks past it. Opened, the lowest place come is let in as ever.
     */
    void setClosed(boolean closed) {
        this.closed = closed;
    }

    boolean isClosed() {
        return closed;
    }

    /** Whether {@code harvester} is in, has a place by it, or may have one: open, and a place free or any number. */
    boolean hasRoomFor(GameObject harvester) {
        tidy();
        var id = harvester.getId();
        return id.equals(in) || places.contains(id) || !closed && (waitingPlaces == 0 || waiting() < waitingPlaces);
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
            if (closed) {
                return false; // closed to newcomers: it looks for another
            }
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
        if (closed || in != null || frame < freeFrom) {
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

    /** Who is in, from when the next may be, the places, who has come — by id, so the save reads the same — and closed. */
    void save(uz.dukeengine.core.save.SaveOut out) {
        out.writeId(in).writeInt(freeFrom).writeIds(places)
                .writeIds(come.stream().sorted(java.util.Comparator.comparingInt(ObjectId::value)).toList())
                .writeBoolean(closed);
    }

    void load(uz.dukeengine.core.save.SaveIn in) {
        this.in = in.readId();
        freeFrom = in.readInt();
        places.clear();
        places.addAll(in.readIds());
        come.clear();
        come.addAll(in.readIds());
        closed = in.readBoolean();
    }

    /** {@code docking}'s state, or a no where there is none, for a module that may have one. */
    static void save(Docking docking, uz.dukeengine.core.save.SaveOut out) {
        out.writeBoolean(docking != null);
        if (docking != null) {
            docking.save(out);
        }
    }

    /** Read back what {@link #save(Docking, uz.dukeengine.core.save.SaveOut)} wrote, into the module's own. */
    static void load(Docking docking, uz.dukeengine.core.save.SaveIn in, String module) {
        if (in.readBoolean() != (docking != null)) {
            throw new uz.dukeengine.core.save.UnsavedModuleException(module, module
                    + " was saved with a dock where its template now has none, or none where it now has one");
        }
        if (docking != null) {
            docking.load(in);
        }
    }

    private int waiting() {
        int waiting = 0;
        for (var id : places) {
            waiting += id == null ? 0 : 1;
        }
        return waiting;
    }
}
