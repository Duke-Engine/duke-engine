package uz.dukeengine.core.partition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import uz.dukeengine.core.SubsystemInterface;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.Footprint;
import uz.dukeengine.core.thing.GameObject;

/**
 * Answers spatial questions about the world, ported from SAGE's
 * {@code PartitionManager}: "which objects are within range of here?", "what is
 * the nearest enemy?".
 *
 * <p>It reads the live object set from a supplier (the simulation) so it never
 * owns object lifetime. One made {@link #indexed} keeps SAGE's cells as well — the
 * things by where they stand ({@link SpatialIndex}), told by its world each thing
 * that comes in, moves and goes — and asks a question only of the things near the
 * place asked about, so a question costs what is near it, not the world's size.
 * One made plain looks at every thing.
 *
 * <p>Determinism: candidates are asked in the source's order (object creation
 * order), and the closest-thing questions break distance ties by lowest object id,
 * so results never depend on hash or iteration order — the same, thing for thing,
 * indexed or not.
 */
public final class PartitionManager extends SubsystemInterface {

    private final Supplier<List<GameObject>> objectSource;
    /** Where the things stand, or null for a manager that looks at every thing. */
    private final SpatialIndex index;

    public PartitionManager(Supplier<List<GameObject>> objectSource) {
        this(objectSource, null);
    }

    private PartitionManager(Supplier<List<GameObject>> objectSource, SpatialIndex index) {
        this.objectSource = objectSource;
        this.index = index;
    }

    /** A manager that keeps where the things stand — told of each by {@link #added}, {@link #moved}, {@link #removed}. */
    public static PartitionManager indexed(Supplier<List<GameObject>> objectSource) {
        return new PartitionManager(objectSource, new SpatialIndex());
    }

    /** {@code thing} came into the world. */
    public void added(GameObject thing) {
        if (index != null) {
            index.add(thing);
        }
    }

    /** {@code thing} stands somewhere else now. */
    public void moved(GameObject thing) {
        if (index != null) {
            index.moved(thing);
        }
    }

    /** {@code thing} left the world. */
    public void removed(GameObject thing) {
        if (index != null) {
            index.remove(thing);
        }
    }

    /** Every thing left the world. */
    public void cleared() {
        if (index != null) {
            index.clear();
        }
    }

    /**
     * The things whose middle may stand within {@code reach} of the ground box, in creation order: those near it where
     * the index knows, every thing where it does not.
     */
    private List<GameObject> candidates(float minX, float minY, float maxX, float maxY, float reach) {
        if (index == null) {
            return objectSource.get();
        }
        var near = index.near(minX - reach, minY - reach, maxX + reach, maxY + reach);
        return near == null ? objectSource.get() : near;
    }

    /** The things whose middle may stand within {@code range} of {@code center}, measured along the ground. */
    private List<GameObject> candidates(Coord3D center, float range) {
        return candidates(center.x(), center.y(), center.x(), center.y(), range);
    }

    /** The things whose footprint may come within {@code reach} of {@code footprint}'s. */
    private List<GameObject> candidates(Footprint footprint, float reach) {
        float from = footprint.shape().footprintRadius() + reach + (index == null ? 0f : index.widest());
        return candidates(footprint.center(), from);
    }

    @Override
    public void init() {
    }

    @Override
    public void reset() {
    }

    @Override
    public void update() {
    }

    /** All objects within {@code range} of {@code center} that pass {@code filter}. */
    public List<GameObject> objectsInRange(Coord3D center, float range, PartitionFilter filter) {
        var result = new ArrayList<GameObject>();
        for (var candidate : candidates(center, range)) {
            if (center.distance(candidate.getPosition()) <= range && filter.accept(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * Every object whose physical {@link Footprint} overlaps {@code footprint}
     * and passes {@code filter}, in creation order.
     */
    public List<GameObject> objectsOverlapping(Footprint footprint, PartitionFilter filter) {
        var result = new ArrayList<GameObject>();
        for (var candidate : candidates(footprint, 0f)) {
            if (filter.accept(candidate) && footprint.overlaps(Footprint.of(candidate))) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * The first object standing in the way of {@code footprint}, or {@code null}
     * if the space is clear — the question movement asks every frame, so it stops
     * at the first hit instead of collecting them all.
     */
    public GameObject firstOverlapping(Footprint footprint, PartitionFilter filter) {
        for (var candidate : candidates(footprint, 0f)) {
            if (filter.accept(candidate) && footprint.overlaps(Footprint.of(candidate))) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The nearest object whose shape comes within {@code reach} of {@code from}'s
     * shape — "what can I touch from here?", the question a weapon, a repair arm
     * or a capture attempt all ask.
     *
     * <p>Distances are surface to surface on the ground plane, so a wide building
     * is in reach as soon as its wall is, not its centre. Objects with no
     * geometry measure centre to centre, exactly as {@link #closestObject} does.
     * Ties broken by lowest object id.
     */
    public GameObject closestWithinReach(Footprint from, float reach, PartitionFilter filter) {
        GameObject best = null;
        float bestSeparation = Float.MAX_VALUE;
        for (var candidate : candidates(from, reach)) {
            if (!filter.accept(candidate)) {
                continue;
            }
            float separation = from.separation(Footprint.of(candidate));
            if (separation > reach) {
                continue;
            }
            if (separation < bestSeparation
                    || (separation == bestSeparation && best != null
                        && candidate.getId().value() < best.getId().value())) {
                best = candidate;
                bestSeparation = separation;
            }
        }
        return best;
    }

    /**
     * The nearest object to {@code center} within {@code range} passing
     * {@code filter}, or {@code null} if none. Ties broken by lowest object id.
     */
    public GameObject closestObject(Coord3D center, float range, PartitionFilter filter) {
        GameObject best = null;
        float bestDistance = Float.MAX_VALUE;
        for (var candidate : candidates(center, range)) {
            if (!filter.accept(candidate)) {
                continue;
            }
            float distance = center.distance(candidate.getPosition());
            if (distance > range) {
                continue;
            }
            if (distance < bestDistance
                    || (distance == bestDistance && best != null
                        && candidate.getId().value() < best.getId().value())) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }
}
