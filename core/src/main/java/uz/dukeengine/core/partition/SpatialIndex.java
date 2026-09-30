package uz.dukeengine.core.partition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Solid;

/**
 * The world's things by where they stand: square buckets of ground, each holding the things whose middle is in it —
 * SAGE's partition cells — so a question about a place looks only at the things near it, whatever the world's size.
 *
 * <p>Only ever a way to find candidates: the {@link PartitionManager} asks each candidate the question the whole world
 * was asked before, and answers in the order things came into the world ({@link GameObject#getEntered}), so an answer
 * is the same thing for thing as a look at every thing gave. Buckets are kept in a hash map, but nothing is ever
 * answered in its order.
 */
final class SpatialIndex {

    /** How wide a bucket is, in world units: a few cells, a little wider than most questions reach. */
    static final float BUCKET = 64f;

    private static final Comparator<GameObject> ENTERED = Comparator.comparingLong(GameObject::getEntered);

    private final HashMap<Long, List<GameObject>> buckets = new HashMap<>();
    /** The bucket each thing indexed stands in. */
    private final IdentityHashMap<GameObject, Long> entries = new IdentityHashMap<>();
    /** The widest footprint any thing indexed has had, from its middle: how far past a box a thing may reach into it. */
    private float widest;

    void add(GameObject thing) {
        if (entries.containsKey(thing)) {
            return;
        }
        long bucket = bucketOf(thing.getPosition().x(), thing.getPosition().y());
        entries.put(thing, bucket);
        buckets.computeIfAbsent(bucket, key -> new ArrayList<>()).add(thing);
        widest = Math.max(widest, Solid.of(thing.getTemplate()).footprintRadius());
    }

    void moved(GameObject thing) {
        var was = entries.get(thing);
        if (was == null) {
            return;
        }
        long bucket = bucketOf(thing.getPosition().x(), thing.getPosition().y());
        if (bucket == was) {
            return;
        }
        leave(thing, was);
        buckets.computeIfAbsent(bucket, key -> new ArrayList<>()).add(thing);
        entries.put(thing, bucket);
    }

    void remove(GameObject thing) {
        var was = entries.remove(thing);
        if (was != null) {
            leave(thing, was);
        }
    }

    void clear() {
        buckets.clear();
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    /** The widest footprint of any thing indexed, from its middle. */
    float widest() {
        return widest;
    }

    /**
     * Every thing whose middle may lie in the ground box — all of them for certain, a few beside — in the order they
     * came into the world; or null where the box spans more buckets than there are things, and a look at every thing is
     * the cheaper answer.
     */
    List<GameObject> near(float minX, float minY, float maxX, float maxY) {
        // A margin past the box: the question asked of each candidate is worked out in floats, and a thing its
        // rounding lets in must be a candidate to be let in.
        float marginX = 1f + Math.max(Math.abs(minX), Math.abs(maxX)) * 1e-4f;
        float marginY = 1f + Math.max(Math.abs(minY), Math.abs(maxY)) * 1e-4f;
        long fromX = cell(minX - marginX);
        long toX = cell(maxX + marginX);
        long fromY = cell(minY - marginY);
        long toY = cell(maxY + marginY);
        if (!Float.isFinite(minX) || !Float.isFinite(maxX) || !Float.isFinite(minY) || !Float.isFinite(maxY)
                || ((double) toX - fromX + 1) * ((double) toY - fromY + 1) > Math.max(16, entries.size())) {
            return null;
        }
        var found = new ArrayList<GameObject>();
        for (long x = fromX; x <= toX; x++) {
            for (long y = fromY; y <= toY; y++) {
                var bucket = buckets.get(key(x, y));
                if (bucket != null) {
                    found.addAll(bucket);
                }
            }
        }
        found.sort(ENTERED);
        return found;
    }

    /**
     * Every thing standing in one of the buckets {@code keys} names — each once, since a thing stands in one bucket —
     * in the order they came into the world.
     */
    List<GameObject> inBuckets(Iterable<Long> keys) {
        var found = new ArrayList<GameObject>();
        for (long key : keys) {
            var bucket = buckets.get(key);
            if (bucket != null) {
                found.addAll(bucket);
            }
        }
        found.sort(ENTERED);
        return found;
    }

    private void leave(GameObject thing, long bucket) {
        var things = buckets.get(bucket);
        if (things == null) {
            return;
        }
        things.remove(thing);
        if (things.isEmpty()) {
            buckets.remove(bucket);
        }
    }

    /** The bucket a coordinate falls in, along one axis. */
    static long cell(float coordinate) {
        return (long) Math.floor(coordinate / BUCKET);
    }

    private static long bucketOf(float x, float y) {
        return key(cell(x), cell(y));
    }

    /**
     * One number for the bucket at {@code (x, y)}, a different one for each: its two halves stirred so that buckets
     * side by side hash apart — packed plainly, the buckets along a diagonal all hashed alike.
     */
    static long key(long x, long y) {
        long z = (x << 32) ^ (y & 0xFFFFFFFFL);
        // SplitMix64's finaliser: every step undoes, so two buckets never share a key.
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
