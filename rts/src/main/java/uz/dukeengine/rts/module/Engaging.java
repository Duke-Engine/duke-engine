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
     * Close on {@code victim} until the weapon reaches it, and stand there — left to {@link PursueUpdate} where the
     * unit has one. Planned again only when the victim has moved more than a cell from where it was planned to.
     *
     * @return where it is now closing to, for the next frame's comparison
     */
    static Coord3D close(GameObject unit, Locomotor legs, WeaponUpdate weapon, GameObject victim, Coord3D chasedTo) {
        if (unit.findModule(PursueUpdate.class) != null) {
            return chasedTo;
        }
        if (weapon.isInRange(victim)) {
            if (legs.isMoving()) {
                legs.stop();
            }
            return null;
        }
        var at = victim.getPosition();
        float cell = unit.getWorld().cellSize();
        if (chasedTo == null || at.distance(chasedTo) > cell || !legs.isMoving()) {
            legs.moveExactlyTo(legs.flies() || weapon.closesToTouch(victim) ? at
                    : unit.getWorld().standingNextTo(unit, victim));
            return at;
        }
        return chasedTo;
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
