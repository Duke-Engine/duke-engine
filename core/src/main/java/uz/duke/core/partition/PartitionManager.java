package uz.duke.core.partition;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import uz.duke.core.SubsystemInterface;
import uz.duke.core.math.Coord3D;
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
