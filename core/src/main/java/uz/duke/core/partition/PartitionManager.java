package uz.duke.core.partition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import uz.duke.core.SubsystemInterface;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.Footprint;
import uz.duke.core.thing.GameObject;

/**
 * Answers spatial questions about the world, ported from SAGE's
 * {@code PartitionManager}: "which objects are within range of here?", "what is
 * the nearest enemy?".
 *
 * <p>It reads the live object set from a supplier (the simulation) so it never
 * owns object lifetime. This implementation is an honest brute-force scan; the
 * faithful next step is SAGE's spatial cell grid for large object counts, but the
 * query API is what callers depend on and is what is fixed here.
 *
 * <p>Determinism: candidates are scanned in the source's order (object creation
 * order), and {@link #closestObject} breaks distance ties by lowest object id,
 * so results never depend on hash or iteration order.
 */
public final class PartitionManager extends SubsystemInterface {

    private final Supplier<List<GameObject>> objectSource;

    public PartitionManager(Supplier<List<GameObject>> objectSource) {
        this.objectSource = objectSource;
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
        for (var candidate : objectSource.get()) {
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
        for (var candidate : objectSource.get()) {
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
        for (var candidate : objectSource.get()) {
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
        for (var candidate : objectSource.get()) {
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
        for (var candidate : objectSource.get()) {
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
