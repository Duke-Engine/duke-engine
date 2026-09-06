package uz.duke.core.partition;

import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.KindOf;
import uz.duke.core.thing.World;

/**
 * A predicate that decides whether an object qualifies for a spatial query,
 * ported from SAGE's {@code PartitionFilter} hierarchy.
 *
 * <p>SAGE had dozens of filter subclasses (relationship, kind-of, alive, line of
 * sight…). Here a filter is a single-method interface with composable factory
 * filters, so a query like "nearest living enemy" is just
 * {@code enemiesOf(world, me).and(alive())}. Filters must be pure functions of
 * the candidate so query results stay deterministic.
 */
@FunctionalInterface
public interface PartitionFilter {

    boolean accept(GameObject candidate);

    /** Logical AND of two filters (short-circuiting). */
    default PartitionFilter and(PartitionFilter other) {
        return candidate -> this.accept(candidate) && other.accept(candidate);
    }

    /** Logical OR of two filters (short-circuiting). */
    default PartitionFilter or(PartitionFilter other) {
        return candidate -> this.accept(candidate) || other.accept(candidate);
    }

    /** Accepts everything. */
    static PartitionFilter any() {
        return candidate -> true;
    }

    /** Accepts objects that are not dead. */
    static PartitionFilter alive() {
        return candidate -> !candidate.isEffectivelyDead();
    }

    /** Rejects one specific object (typically the querying object itself). */
    static PartitionFilter excluding(GameObject self) {
        return candidate -> candidate != self;
    }

    /** Accepts objects carrying the given classification flag. */
    static PartitionFilter ofKind(KindOf kind) {
        return candidate -> candidate.isKindOf(kind);
    }

    /** Accepts objects the given player regards as {@code relationship}. */
    static PartitionFilter withRelationship(World world, int fromPlayer, Relationship relationship) {
        return candidate -> world.getRelationship(fromPlayer, candidate.getPlayerIndex()) == relationship;
    }

    /** Accepts objects that are enemies of the given player. */
    static PartitionFilter enemiesOf(World world, int fromPlayer) {
        return withRelationship(world, fromPlayer, Relationship.ENEMIES);
    }
}
