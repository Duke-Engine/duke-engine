package uz.dukeengine.rts.module;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.MoveUpdate;
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
 * @param repathFrames how often it asks again where the target has got to. Every frame is a path a frame, and
 *     a target that has not moved is a path that has not changed; 0 means every frame
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class PursueUpdate extends UpdateModule {

    public record Data(float giveUpBeyond, int repathFrames) implements ModuleData {
    }

    private final float giveUpBeyond;
    private final int repathFrames;

    /** Where it stood when it took its current target, so a leash is measured from there and not from home. */
    private Coord3D tookItFrom;
    private int since;

    public PursueUpdate(GameObject owner, Data data) {
        super(owner);
        this.giveUpBeyond = Math.max(0f, data.giveUpBeyond());
        this.repathFrames = Math.max(0, data.repathFrames());
    }

    @Override
    public void update() {
        var owner = getOwner();
        var weapon = owner.findModule(WeaponUpdate.class);
        var legs = owner.findModule(MoveUpdate.class);
        var world = owner.getWorld();
        if (weapon == null || legs == null || world == null || owner.isEffectivelyDead()) {
            return;
        }
        var victim = weapon.getTarget() == null ? null : world.findObject(weapon.getTarget());
        if (victim == null || victim.isEffectivelyDead()) {
            tookItFrom = null;
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
            since = 0;
            return;
        }
        if (giveUpBeyond > 0f && tookItFrom.distance(owner.getPosition()) > giveUpBeyond) {
            weapon.holdFire();
            legs.stop();
            tookItFrom = null;
            return;
        }
        if (since > 0 && since < repathFrames) {
            since++;
            return;
        }
        since = 1;
        legs.moveTo(world.standingNextTo(owner, victim));
    }
}
