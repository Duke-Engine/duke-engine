package uz.dukeengine.rts.module;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.Locomotor;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;

/** What an errand that fights shares: finding the enemy to take on, and closing on it. */
final class Engaging {

    private Engaging() {
    }

    /**
     * The enemy nearest {@code around} within {@code within} that {@code unit}'s weapons may be fired at — in the
     * air only where {@code airborneOnly}, in its weapons' range already where {@code inRange} — the lower id of two
     * as near, or null.
     */
    static GameObject nearestEnemy(GameObject unit, WeaponUpdate weapon, Coord3D around, float within,
            boolean airborneOnly, boolean inRange) {
        var world = unit.getWorld();
        GameObject best = null;
        float nearest = Float.MAX_VALUE;
        for (var other : world.objectsInRange(around, within, candidate -> true)) {
            if (other == unit || other.isEffectivelyDead() || other.isContained()
                    || world.getRelationship(unit.getPlayerIndex(), other.getPlayerIndex()) != Relationship.ENEMIES
                    || airborneOnly && !other.hasStatus(ObjectStatus.AIRBORNE)
                    || !weapon.canFireAt(other) || inRange && !weapon.isInRange(other)) {
                continue;
            }
            float away = other.getPosition().distance(around);
            if (away < nearest || away == nearest && best != null && other.getId().value() < best.getId().value()) {
                nearest = away;
                best = other;
            }
        }
        return best;
    }

    /**
     * Where a unit closing on its victim was sent: where the victim stood when it was, and whether to a place it fires
     * from, which it goes on to though it may fire on the way.
     */
    record Chase(Coord3D at, boolean goingThere) {
    }

    /**
     * Close on {@code victim} until the weapon may fire at it, and stand there — left to {@link PursueUpdate} where the
     * unit has one. Planned again only when the victim has moved more than a cell from where it was planned to.
     *
     * @return where it is now closing to, for the next frame's comparison
     */
    static Chase close(GameObject unit, Locomotor legs, WeaponUpdate weapon, GameObject victim, Chase chased) {
        if (unit.findModule(PursueUpdate.class) != null) {
            return chased;
        }
        if (weapon.isInRange(victim)) {
            if (chased != null && chased.goingThere() && legs.isMoving()) {
                return chased; // on its way to where it fires from: it goes on there
            }
            if (legs.isMoving()) {
                legs.stop();
            }
            return null;
        }
        var at = victim.getPosition();
        float cell = unit.getWorld().cellSize();
        if (chased == null || at.distance(chased.at()) > cell || !legs.isMoving()) {
            return new Chase(at, approach(unit, legs, weapon, victim));
        }
        return chased;
    }

    /**
     * Send {@code unit} where its weapon fires at {@code victim} from: a weapon that must touch, straight at it;
     * something in the air, straight at it, or, too near it, to the point it fires from beyond ({@link
     * WeaponUpdate#awayInTheAir}); on the ground, by a route that ends at the nearest cell its weapon fires from —
     * at least its whole least range off, and a quarter cell inside its reach so the goal is not teetering on the edge
     * of it: the reference's attack path ({@code Pathfinder::findAttackPath}, {@code
     * Weapon::isGoalPosWithinAttackRange}).
     *
     * @return whether it was sent to a place it fires from, which it goes on to rather than stopping the moment it
     *     may fire — the reference's attack path ({@code m_stopIfInRange = !isAttackPath()}); straight at the
     *     victim, it stops once it may
     */
    static boolean approach(GameObject unit, Locomotor legs, WeaponUpdate weapon, GameObject victim) {
        if (weapon.closesToTouch(victim)) {
            legs.moveExactlyTo(victim.getPosition());
            return false;
        }
        if (legs.flies()) {
            var away = weapon.awayInTheAir(victim);
            legs.moveExactlyTo(away == null ? victim.getPosition() : away);
            return away != null;
        }
        float cell = unit.getWorld().cellSize();
        legs.moveWithin(victim, weapon.leastFor(victim), Math.max(0f, weapon.reachFor(victim) - cell / 4f));
        return true;
    }

    /** The game's guard numbers, or the reference's. */
    static GuardRules rules(World world) {
        return world instanceof uz.dukeengine.rts.RtsSimulation rts ? rts.getGuardRules() : GuardRules.DEFAULT;
    }

    /** Whether a computer plays the unit's side. */
    static boolean computer(GameObject unit) {
        var side = uz.dukeengine.rts.player.RtsPlayer.of(unit.getWorld(), unit.getPlayerIndex());
        return side != null && side.isComputer();
    }
}
