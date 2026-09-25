package uz.dukeengine.rts.module;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;

/**
 * An attack-move, as the reference's {@code AIAttackMoveToState} makes one: to a point, and whenever an enemy its
 * weapons may be fired at comes within its vision on the way, it takes that enemy on — chasing it no farther than the
 * guard's outer reach from where it took it on, nor longer than the guard's chase time ({@link GuardRules}) — and then
 * carries on to the point. Looks for an enemy as often as a guard holding its ground does.
 */
public final class AttackMoveOrder extends UpdateModule implements Errand {

    private final Coord3D destination;
    private ObjectId target;
    private Coord3D tookItOnAt;
    private Coord3D chasedTo;
    private int engagedAt;
    private int nextLook;
    private boolean over;

    AttackMoveOrder(GameObject unit, Coord3D destination) {
        super(unit);
        this.destination = destination;
    }

    /** Send each of the player's units that can walk and fight on an attack-move; how many went. */
    public static int order(RtsSimulation world, GameMessage.AttackMove order) {
        int sent = 0;
        for (var id : order.units()) {
            var unit = world.findObject(id);
            if (unit == null || unit.getPlayerIndex() != order.playerIndex() || unit.isEffectivelyDead()
                    || unit.getLocomotor() == null || unit.findModule(WeaponUpdate.class) == null) {
                continue;
            }
            Errand.giveUpAll(unit);
            unit.findModule(WeaponUpdate.class).holdFire();
            unit.addModule(new AttackMoveOrder(unit, order.destination()));
            sent++;
        }
        return sent;
    }

    /** Whether it has reached its point with nothing left to fight. */
    public boolean isOver() {
        return over;
    }

    @Override
    public void update() {
        var unit = getOwner();
        var world = unit.getWorld();
        var weapon = unit.findModule(WeaponUpdate.class);
        var legs = unit.getLocomotor();
        if (over || world == null || weapon == null || legs == null || unit.isEffectivelyDead()) {
            return;
        }
        var rules = Engaging.rules(world);
        float vision = unit.getVisionRange();
        if (target != null) {
            var victim = world.findObject(target);
            if (victim == null || victim.isEffectivelyDead() || !weapon.canFireAt(victim)
                    || victim.getPosition().distance(tookItOnAt) > vision * rules.outer(Engaging.computer(unit))
                    || world.getFrame() - engagedAt > rules.chaseFrames()) {
                weapon.holdFire(); // dealt with, or got away: on to the point
                target = null;
                chasedTo = null;
            } else {
                chasedTo = Engaging.close(unit, legs, weapon, victim, chasedTo);
                return;
            }
        }
        if (world.getFrame() >= nextLook) {
            nextLook = world.getFrame() + rules.lookWhileHolding();
            var found = Engaging.nearestEnemy(unit, weapon, unit.getPosition(), vision, false, false);
            if (found != null && weapon.attack(found.getId())) {
                target = found.getId();
                tookItOnAt = unit.getPosition();
                engagedAt = world.getFrame();
                legs.stop();
                return;
            }
        }
        if (unit.getPosition().distance(destination) <= world.cellSize() || legs.stoppedShort()) {
            over = true; // there, or as near as it can get
            return;
        }
        if (!legs.isMoving()) {
            legs.moveTo(destination);
        }
    }

    /** On an errand, it is at work: not asked to step aside. */
    @Override
    public boolean keepsBusy() {
        return true;
    }
}
