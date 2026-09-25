package uz.dukeengine.rts.module;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;

/**
 * A guard, as the reference's {@code AIGuardMachine} keeps one: a unit holds a point, or keeps to a thing it guards,
 * and looks for enemies; one within its vision times the inner reach it takes on; it chases no farther from the place
 * it guards than its vision times the outer reach, nor longer than the chase time, and then walks back. It looks
 * every so often while it holds its ground, and less often while it walks back. All of it the game's numbers
 * ({@link GuardRules}), a human's units and a computer's apart.
 *
 * <p>Without pursuit, it takes on only what its weapons already reach and never leaves its place; guarding against
 * the air, it takes on only what flies.
 */
public final class GuardOrder extends UpdateModule implements Errand {

    private final Coord3D place;
    private final ObjectId charge;
    private final GameMessage.Guard.Mode mode;
    private Coord3D lastPlace;
    private ObjectId target;
    private Engaging.Chase chased;
    private int engagedAt;
    private int nextLook;
    private boolean returning;

    GuardOrder(GameObject unit, Coord3D place, ObjectId charge, GameMessage.Guard.Mode mode) {
        super(unit);
        this.place = place;
        this.charge = charge;
        this.mode = mode == null ? GameMessage.Guard.Mode.NORMAL : mode;
        this.lastPlace = place != null ? place : unit.getPosition();
    }

    /** Set each of the player's units that can fight to guard; how many were set. */
    public static int order(RtsSimulation world, GameMessage.Guard order) {
        int set = 0;
        for (var id : order.units()) {
            var unit = world.findObject(id);
            if (unit == null || unit.getPlayerIndex() != order.playerIndex() || unit.isEffectivelyDead()
                    || unit.findModule(WeaponUpdate.class) == null) {
                continue;
            }
            Errand.giveUpAll(unit);
            unit.findModule(WeaponUpdate.class).holdFire();
            var at = order.place() != null || order.target() != null ? order.place() : unit.getPosition();
            unit.addModule(new GuardOrder(unit, at, order.target(), order.mode()));
            set++;
        }
        return set;
    }

    /** Whether it is walking back to where it guards. */
    public boolean isReturning() {
        return returning;
    }

    /** What it is taking on, or null. */
    public ObjectId getTarget() {
        return target;
    }

    @Override
    public void update() {
        var unit = getOwner();
        var world = unit.getWorld();
        var weapon = unit.findModule(WeaponUpdate.class);
        if (world == null || weapon == null || unit.isEffectivelyDead()) {
            return;
        }
        var legs = unit.getLocomotor();
        var rules = Engaging.rules(world);
        boolean computer = Engaging.computer(unit);
        float vision = unit.getVisionRange();
        var here = guarded(world);
        boolean pursues = mode != GameMessage.Guard.Mode.WITHOUT_PURSUIT && legs != null;

        if (target != null) {
            var victim = world.findObject(target);
            if (victim == null || victim.isEffectivelyDead() || !weapon.canFireAt(victim)
                    || victim.getPosition().distance(here) > vision * rules.outer(computer)
                    || world.getFrame() - engagedAt > rules.chaseFrames()
                    || !pursues && !weapon.isInRange(victim)) {
                weapon.holdFire();
                target = null;
                chased = null;
                nextLook = world.getFrame() + rules.lookWhileReturning();
            } else {
                if (pursues) {
                    chased = Engaging.close(unit, legs, weapon, victim, chased);
                }
                return;
            }
        }
        if (world.getFrame() >= nextLook) {
            nextLook = world.getFrame() + (returning ? rules.lookWhileReturning() : rules.lookWhileHolding());
            var found = Engaging.nearestEnemy(unit, weapon, here, vision * rules.inner(computer),
                    mode == GameMessage.Guard.Mode.FLYING_ONLY, !pursues);
            if (found != null && weapon.attack(found.getId())) {
                target = found.getId();
                engagedAt = world.getFrame();
                returning = false;
                if (legs != null) {
                    legs.stop();
                }
                return;
            }
        }
        if (legs == null) {
            return;
        }
        if (unit.getPosition().distance(here) > world.cellSize()) {
            returning = true;
            if (!legs.isMoving() && !legs.stoppedShort()) {
                legs.moveTo(here);
            }
        } else {
            returning = false;
        }
    }

    /** Where it guards now: its point, or where the thing it keeps to stands — where it last stood, once it is gone. */
    private Coord3D guarded(uz.dukeengine.core.thing.World world) {
        if (charge != null) {
            var kept = world.findObject(charge);
            if (kept != null && !kept.isEffectivelyDead()) {
                lastPlace = kept.getPosition();
            }
            return lastPlace;
        }
        return place;
    }

    /** On an errand, it is at work: not asked to step aside. */
    @Override
    public boolean keepsBusy() {
        return true;
    }
}
