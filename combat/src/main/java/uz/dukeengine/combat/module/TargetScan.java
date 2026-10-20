package uz.dukeengine.combat.module;

import java.util.List;
import uz.dukeengine.combat.ArmedWorld;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;

/**
 * A thing looking for a target of its own: how often it looks, each thing on a clock of its own, and whom it picks
 * when it does. Nothing here holds the target — that is the {@link Engagement}'s.
 */
final class TargetScan {

    private final GameObject owner;
    private final Loadout loadout;
    /** How often it looks for a target, frames, as its template says; 0 for as often as the world says. */
    private final int scanFrames;
    /** The frame it looks for a target next — see {@link #looksNow}. */
    private int nextLook;
    /** Whether a look has moved its next look on yet: the first move is the one offset. */
    private boolean lookedBefore;
    /** Whether it held a target or walked as it last looked, to tell the frame it falls idle. A new thing did. */
    private boolean busy = true;

    TargetScan(GameObject owner, Loadout loadout, int scanFrames) {
        this.owner = owner;
        this.loadout = loadout;
        this.scanFrames = scanFrames;
    }

    /**
     * Whether it looks for a target now — the reference's mood check ({@code AIUpdateInterface::getNextMoodTarget}):
     * once the frame reaches its next look, which the look then moves on by its rate, its template's or else the
     * world's ({@code MoodAttackCheckRate}); the first move by up to half the rate more as well, drawn once from the
     * world's random numbers ({@code m_randomlyOffsetMoodCheck}), so things made together do not look together. A rate
     * of 1 looks every frame and draws nothing, as every thing did before its look had a clock.
     */
    boolean looksNow(World world) {
        int now = world.getFrame();
        if (now < nextLook) {
            return false;
        }
        int rate = scanFrames > 0 ? scanFrames
                : world instanceof ArmedWorld armed ? armed.armoury().getTargetScanFrames() : 1;
        int offset = lookedBefore ? 0 : world.random().nextInt(0, rate / 2);
        lookedBefore = true;
        nextLook = now + rate + offset;
        return true;
    }

    /**
     * Its next look brought to now and the world's idle frames, the frame it falls idle — neither holding a target nor
     * walking, its order done or stopped or what it fought gone, or new: the reference's {@code resetNextMoodCheckTime},
     * which its idle state and an idle turret run, so a thing looks almost at once.
     */
    void noticeFallingIdle(boolean holdsTarget) {
        boolean busyNow = holdsTarget || Hands.isWalking(owner);
        var world = owner.getWorld();
        if (busy && !busyNow && world != null) {
            nextLook = world.getFrame()
                    + (world instanceof ArmedWorld armed ? armed.armoury().getIdleTargetScanFrames() : 0);
        }
        busy = busyNow;
    }

    /**
     * The nearest living enemy that one of the weapons it may pick by itself may be fired at from where it
     * stands — within its reach and not within its least range — if any: the reference's
     * {@code getNextMoodTarget} for a person's unit ({@code WITHIN_ATTACK_RANGE}, {@code Weapon::isWithinAttackRange}).
     *
     * @return its id, or null for none
     */
    ObjectId acquire(World world, List<Armed> armed) {
        float reach = 0f;
        for (var one : armed) {
            if (loadout.mayLookWith(armed, one) && one.clip().status() != WeaponStatus.OUT) {
                reach = Math.max(reach, Hands.range(owner, one.weapon()));
            }
        }
        var enemy = world.findClosestInReach(owner, reach, candidate ->
                candidate != owner
                        && !candidate.isContained()
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(owner.getPlayerIndex(), candidate.getPlayerIndex())
                                == Relationship.ENEMIES
                        && mayPickFor(armed, candidate));
        return enemy == null ? null : enemy.getId();
    }

    /** Whether a weapon it may pick by itself, and has rounds for, may be fired at {@code candidate}. */
    private boolean mayPickFor(List<Armed> armed, GameObject candidate) {
        if (candidate.isDisguisedFrom(owner.getPlayerIndex())) {
            return false; // passed off to its side as none of its targets
        }
        float gap = Hands.rangeTo(owner, candidate);
        for (var one : armed) {
            if (loadout.mayLookWith(armed, one) && one.clip().status() != WeaponStatus.OUT
                    && gap <= Hands.range(owner, one.weapon()) && !Hands.insideLeast(owner, one.weapon(), gap)
                    && loadout.mayHit(one.weapon(), candidate)) {
                return true;
            }
        }
        return false;
    }
}
