package uz.dukeengine.rts.module;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.Locomotor;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;

/**
 * Closes on whatever this unit's weapon is aimed at, and stops when it can fire.
 *
 * <p>{@link WeaponUpdate} acquires a target and fires when one is in range; when one is <em>not</em>, its own
 * comment says it waits "for movement to close in" — and until this module there was no movement to wait for.
 * Ordering an attack on something out of reach left the unit standing where it was. Every RTS closes: a
 * Generals tank sent at a building drives to it, a Warcraft footman walks. The dungeon has it too, hidden in a
 * brain script of its own, which is what a mechanism living in the wrong place looks like.
 *
 * <p><b>Opt-in, because closing is a decision.</b> A turret, a bunker, a siege weapon that must be unpacked —
 * none of them should follow anything, so a template says whether it does by holding this module or not. What
 * it holds is a mechanism; how far it is willing to go is the game's answer, written in the block.
 *
 * @param giveUpBeyond how far it will stray from where it took the target before letting go; 0 is as far as
 *     it takes, which is what a pursuing unit does in a game with no leash
 * @param repathFrames how often it plans again anyway, to keep up, staggered by the unit's id so a crowd does not
 *     plan on the same frame. It plans whenever it must besides: when it has no route to the target yet, when the
 *     target has moved more than a cell from where the route was planned to, and when the route is used up. 0 is
 *     only when it must — a unit closing on something that stands still plans once
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class PursueUpdate extends UpdateModule {

    public record Data(float giveUpBeyond, int repathFrames) implements ModuleData {
    }

    private final float giveUpBeyond;
    private final int repathFrames;

    /** Where it stood when it took its current target, so a leash is measured from there and not from home. */
    private Coord3D tookItFrom;
    /** Where the target stood when the route to it was planned, or null for no route to it yet. */
    private Coord3D plannedFor;
    /** How many routes it has planned, for a test to count. */
    private int plans;

    public PursueUpdate(GameObject owner, Data data) {
        super(owner);
        this.giveUpBeyond = Math.max(0f, data.giveUpBeyond());
        this.repathFrames = Math.max(0, data.repathFrames());
    }

    @Override
    public void update() {
        var owner = getOwner();
        var weapon = owner.findModule(WeaponUpdate.class);
        var legs = owner.getLocomotor();
        var world = owner.getWorld();
        if (weapon == null || legs == null || world == null || owner.isEffectivelyDead()
                || owner.hasStatus(uz.dukeengine.core.thing.ObjectStatus.HELD)) {
            return; // held, it fights from where it stands and chases nothing
        }
        var victim = weapon.getTarget() == null ? null : world.findObject(weapon.getTarget());
        if (victim == null || victim.isEffectivelyDead()) {
            tookItFrom = null;
            plannedFor = null;
            return;
        }
        if (tookItFrom == null) {
            tookItFrom = owner.getPosition();
        }
        if (weapon.isInRange(victim)) {
            // Close enough: stand and shoot. A unit that kept walking would drift past what it is firing at,
            // and one that may not fire on the move (see WeaponUpdate) would never fire at all.
            if (legs.isMoving()) {
                legs.stop();
            }
            plannedFor = null;
            return;
        }
        if (giveUpBeyond > 0f && tookItFrom.distance(owner.getPosition()) > giveUpBeyond) {
            weapon.holdFire();
            legs.stop();
            tookItFrom = null;
            plannedFor = null;
            return;
        }
        if (!mustPlan(owner, legs, victim, world)) {
            return;
        }
        plannedFor = victim.getPosition();
        plans++;
        // Something in the air goes straight to it; something on the ground to a spot beside it it can reach.
        legs.moveTo(legs.flies() ? victim.getPosition() : world.standingNextTo(owner, victim));
    }

    /**
     * Whether to plan a route now: none yet, the target more than a cell from where the route was planned to, the
     * route used up without its giving up short, or — every {@code repathFrames} frames, staggered by id — to keep up.
     */
    private boolean mustPlan(GameObject owner, Locomotor legs, GameObject victim, World world) {
        if (plannedFor == null) {
            return true;
        }
        var at = victim.getPosition();
        float dx = at.x() - plannedFor.x();
        float dy = at.y() - plannedFor.y();
        float cell = world.cellSize();
        if (dx * dx + dy * dy > cell * cell) {
            return true;
        }
        if (!legs.isMoving() && !legs.stoppedShort()) {
            return true;
        }
        return repathFrames > 0 && Math.floorMod(world.getFrame() + owner.getId().value(), repathFrames) == 0;
    }

    /** How many routes it has planned since it was made. */
    int plans() {
        return plans;
    }
}
