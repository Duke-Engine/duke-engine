package uz.dukeengine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import uz.dukeengine.core.thing.GameObject;

/** Which of a world's things sleep by its {@link Sleep} rule, and the frame's update of those awake. */
final class Sleepers {

    /** Which things sleep, or null where none does. */
    private Sleep rule;
    /** Whether who sleeps was decided this frame, when the reap looks at every thing. */
    private boolean decidedThisFrame;
    /** The things awake, in creation order, since who sleeps was last decided; null where it is yet to be decided. */
    private List<GameObject> awake;
    private final Set<GameObject> awakeSet = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Sleepers a blow landed on while the things awake were being updated, woken once the update is done. */
    private final List<GameObject> hurtWhileUpdating = new ArrayList<>();
    private boolean updating;

    void set(Sleep rule) {
        this.rule = rule;
        this.awake = null;
        this.awakeSet.clear();
    }

    boolean isAsleep(GameObject thing) {
        return awake != null && !awakeSet.contains(thing);
    }

    /** The things awake, or {@code all} where who sleeps is yet to be decided. */
    List<GameObject> awakeOr(List<GameObject> all) {
        return awake == null ? all : awake;
    }

    /**
     * Tick the update modules of every thing awake once, in creation order — who sleeps decided first where the rule
     * says this frame, by cells of {@code cell}. Things created during this pass are not ticked until next frame, so
     * the frame's set is well-defined.
     */
    void update(int frame, Roster roster, float cell) {
        var objects = roster.list();
        decidedThisFrame = rule != null && (awake == null || frame % rule.everyFrames() == 0);
        if (decidedThisFrame) {
            decide(objects, cell);
        }
        var things = awakeOr(objects);
        int count = things.size();
        updating = true;
        try {
            for (int i = 0; i < count; i++) {
                things.get(i).updateModules();
            }
        } finally {
            updating = false;
        }
        for (var thing : hurtWhileUpdating) {
            if (!awakeSet.contains(thing) && roster.isHere(thing)) {
                wake(thing);
            }
        }
        hurtWhileUpdating.clear();
    }

    /**
     * Put every thing farther than the rule's reach from every waker to sleep, and wake the rest — a thing's cell and
     * each waker's by where they stand, counted the longer way across, in creation order.
     */
    private void decide(List<GameObject> objects, float cell) {
        var waking = new boolean[objects.size()];
        var wakerCells = new ArrayList<long[]>();
        for (int i = 0; i < objects.size(); i++) {
            var thing = objects.get(i);
            if (rule.wakers().test(thing)) {
                waking[i] = true;
                wakerCells.add(new long[] {cellOf(thing.getPosition().x(), cell), cellOf(thing.getPosition().y(), cell)});
            }
        }
        var now = new ArrayList<GameObject>();
        awakeSet.clear();
        for (int i = 0; i < objects.size(); i++) {
            var thing = objects.get(i);
            if (waking[i] || nearAWaker(thing, wakerCells, cell)) {
                now.add(thing);
                awakeSet.add(thing);
            }
        }
        awake = now;
    }

    private boolean nearAWaker(GameObject thing, List<long[]> wakerCells, float cell) {
        long x = cellOf(thing.getPosition().x(), cell);
        long y = cellOf(thing.getPosition().y(), cell);
        for (var waker : wakerCells) {
            if (Math.max(Math.abs(x - waker[0]), Math.abs(y - waker[1])) <= rule.cells()) {
                return true;
            }
        }
        return false;
    }

    private static long cellOf(float coordinate, float cell) {
        return (long) Math.floor(coordinate / cell);
    }

    /** {@code thing} came into the world: awake until who sleeps is next decided. */
    void entered(GameObject thing) {
        if (awake != null) {
            awake.add(thing);
            awakeSet.add(thing);
        }
    }

    void left(GameObject thing) {
        if (awake != null && awakeSet.remove(thing)) {
            CreationOrder.takeOut(awake, thing);
        }
    }

    void blowLanded(GameObject thing, Roster roster) {
        if (awake == null || awakeSet.contains(thing) || !roster.isHere(thing)) {
            return;
        }
        if (updating) {
            hurtWhileUpdating.add(thing); // the list being walked is not changed under it
            return;
        }
        wake(thing);
    }

    /** A sleeper wakes, until who sleeps is next decided. */
    private void wake(GameObject thing) {
        if (awakeSet.add(thing)) {
            CreationOrder.putIn(awake, thing);
        }
    }

    /** Every thing awake, as a world with no rule has it, until who sleeps is next decided. */
    void clear() {
        awake = null;
        awakeSet.clear();
        hurtWhileUpdating.clear();
    }

    /**
     * The things a reap looks at, in the order they came in: every thing, where none sleeps or who sleeps was decided
     * this frame; else those awake and, of {@code suspects}, those still here.
     */
    List<GameObject> lookedAtByTheReap(Roster roster, List<GameObject> suspects) {
        if (awake == null || decidedThisFrame) {
            return roster.list();
        }
        var asleep = new ArrayList<GameObject>();
        for (var thing : suspects) {
            if (!awakeSet.contains(thing) && roster.isHere(thing)) {
                asleep.add(thing);
            }
        }
        asleep.sort(Comparator.comparingLong(GameObject::getEntered));
        var once = new ArrayList<GameObject>(asleep.size()); // one marked twice is looked at once
        for (var thing : asleep) {
            if (once.isEmpty() || once.getLast() != thing) {
                once.add(thing);
            }
        }
        return CreationOrder.merged(awake, once);
    }
}
