package uz.dukeengine.rts.module;

import uz.dukeengine.core.module.DamageType;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.player.Relationship;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.event.WeaponFired;
import uz.dukeengine.rts.player.RtsPlayer;

/**
 * Fires at a target object, applying damage on a reload cycle — a lean fusion of
 * SAGE's {@code Weapon}/{@code WeaponTemplate} and the {@code AIUpdate} attack
 * state.
 *
 * <p>Given a target (typically from an {@code AttackObject} command), each frame
 * it counts down its reload, and when the target is a valid, non-allied,
 * in-range, living object it deals {@code damage} to the target's body and starts
 * the reload again. It deliberately does not move the owner into range — that is
 * {@link MoveUpdate}'s job — it only fires when able.
 *
 * <p>Targeting reads the world through {@link GameObject#getWorld()}, the role
 * SAGE's global {@code TheGameLogic} plays.
 *
 * <p>A weapon fires on the move unless its data says otherwise. {@code
 * AttackOnTheMove = No} is for the ones that have to be stood still for — a drawn
 * bow, a deployed gun — and it belongs to the weapon rather than to whatever is
 * steering the unit, because a weapon finds its own target and fires in one call:
 * a script that disarmed it would be undone before the script ran again.
 *
 * <p>{@link WeaponHold} is the same idea for a moment rather than a movement: any
 * module on the unit may say it is busy, and the weapon keeps quiet while it is.
 *
 * <p><b>What it may be fired at.</b> A weapon is built for some targets and useless against others: in the RTS
 * this was measured in, 47 of 363 weapons can hit aircraft, 27 cannot hit anything on the ground, and a
 * bulldozer's mine-clearing charge (range 5, damage 1) would otherwise attack every enemy that came near it.
 * So a weapon may name the classes it is fired at ({@link Data#targets}), and the game says which classes each
 * thing has ({@link TargetRule}). Acquiring a target, keeping one, and taking an order to attack one all ask
 * the same question — {@link #canFireAt} — and an order to attack something it cannot hit is refused.
 *
 * <p><b>Its clip.</b> Most RTS weapons fire from a clip, and the clip is the rate of fire: in the RTS this was
 * measured in, 314 of 363 weapons give a clip size, 112 hold one round — so they fire once a reload and never
 * wait their delay — 20 hold thirty (bursts, then a pause), and 13 are refilled only when their aircraft lands.
 * See {@link Clip} for the rule; {@link #getStatus}, {@link #getRounds} and {@link #refill} are the weapon's
 * side of it. Left out, a weapon has no clip and waits {@code ReloadFrames} after every shot, as it always did.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class WeaponUpdate extends UpdateModule {

    /**
     * INI configuration: {@code Damage}, {@code AttackRange}, {@code ReloadFrames},
     * a {@link DamageType}, a {@code SplashRadius} for area damage, and whether the
     * weapon may be used on the move. The shorter forms default the trailing
     * fields, keeping existing call sites working.
     *
     * @param attackOnTheMove  whether it fires while its owner is walking. Yes for
     *     everything by default, which is what an RTS unit does and what every
     *     weapon did before this existed. No is for the weapons that have to be
     *     stood still for — a drawn bow, a deployed siege gun: the owner keeps its
     *     target and keeps reloading, but the shot waits until it stops.
     * @param targets  the classes it may be fired at, words the game's {@link TargetRule}s give things —
     *     {@code Targets = [GROUND, AIRBORNE_VEHICLE]}. None, the default, is anything at all, which is
     *     what every weapon was before this existed; the reference game reads none as ground only, and a
     *     game that wants that says so
     * @param reloadFrames  the wait after a shot that leaves rounds in the clip — after every shot, for a weapon
     *     with no clip. The reference's delay between shots
     * @param reloadFramesMax  the most that wait may be: with this above {@code ReloadFrames}, each wait is drawn
     *     from the two, both included, from the simulation's own random numbers. Left out, the wait is exactly
     *     {@code ReloadFrames} and nothing is drawn
     * @param clipSize  rounds before a reload. 0, the default, is no clip
     * @param clipReloadFrames  how long refilling an emptied clip takes, from the shot that emptied it
     * @param autoReload  whether it refills itself. Yes by default; no, and an emptied clip stays empty —
     *     {@link WeaponStatus#OUT} — until {@link #refill}
     */
    public record Data(float damage, float attackRange, int reloadFrames,
            DamageType damageType, float splashRadius,
            boolean attackOnTheMove, java.util.List<String> targets,
            int reloadFramesMax, int clipSize, int clipReloadFrames, boolean autoReload) implements ModuleData {
        /** What a block leaves out: plain damage, no splash, a shot taken on the move, at anything, no clip. */
        static final Data DEFAULTS = new Data(0f, 0f, 0, DamageType.NORMAL, 0f, true, java.util.List.of(),
                0, 0, 0, true);

        public Data {
            damageType = damageType == null ? DamageType.NORMAL : damageType;
            targets = targets == null ? java.util.List.of() : java.util.List.copyOf(targets);
        }

        public Data(float damage, float attackRange, int reloadFrames) {
            this(damage, attackRange, reloadFrames, DamageType.NORMAL, 0f);
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType) {
            this(damage, attackRange, reloadFrames, damageType, 0f);
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType,
                float splashRadius) {
            this(damage, attackRange, reloadFrames, damageType, splashRadius, true);
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType,
                float splashRadius, boolean attackOnTheMove) {
            this(damage, attackRange, reloadFrames, damageType, splashRadius, attackOnTheMove, java.util.List.of());
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType,
                float splashRadius, boolean attackOnTheMove, java.util.List<String> targets) {
            this(damage, attackRange, reloadFrames, damageType, splashRadius, attackOnTheMove, targets,
                    0, 0, 0, true);
        }
    }

    private final float damage;
    private final float attackRange;
    private final DamageType damageType;
    private final float splashRadius;
    private final boolean attackOnTheMove;
    private final java.util.List<String> targets;

    private final Clip clip;

    private ObjectId target;

    public WeaponUpdate(GameObject owner, Data data) {
        super(owner);
        this.damage = data.damage();
        this.attackRange = data.attackRange();
        this.damageType = data.damageType();
        this.splashRadius = data.splashRadius();
        this.attackOnTheMove = data.attackOnTheMove();
        this.targets = data.targets();
        this.clip = new Clip(data.clipSize(), data.reloadFrames(), data.reloadFramesMax(), data.clipReloadFrames(),
                data.autoReload());
    }

    /** Whether it may fire now, is waiting between shots, is refilling its clip, or is out. */
    public WeaponStatus getStatus() {
        return clip.status();
    }

    /** Rounds left in its clip; 0 for a weapon with no clip, which counts none. */
    public int getRounds() {
        return clip.rounds();
    }

    /** Fill its clip and make it ready at once — what a game does when an aircraft lands at base, or on a crate. */
    public void refill() {
        clip.refill();
    }

    /**
     * Order this weapon to engage {@code target} — refused, and whatever it was doing kept, when the target is
     * something it may not be fired at ({@link #canFireAt}).
     *
     * @return whether it took the order
     */
    public boolean attack(ObjectId target) {
        var world = getOwner().getWorld();
        var victim = world == null || target == null ? null : world.findObject(target);
        if (victim != null && !canFireAt(victim)) {
            return false;
        }
        this.target = target;
        return true;
    }

    /**
     * Whether this weapon may be fired at {@code victim} at all — its class, not its range or its side: one of
     * the classes the world's {@link TargetRule}s give it is among the ones this weapon names. A weapon that
     * names none may be fired at anything.
     */
    public boolean canFireAt(GameObject victim) {
        if (targets.isEmpty()) {
            return true;
        }
        var rules = getOwner().getWorld() instanceof uz.dukeengine.rts.RtsSimulation rts
                ? rts.getTargetRules() : java.util.List.<TargetRule>of();
        for (var named : TargetRule.classesOf(rules, victim)) {
            if (targets.contains(named)) {
                return true;
            }
        }
        return false;
    }

    public void holdFire() {
        this.target = null;
    }

    public boolean isAttacking() {
        return target != null;
    }

    /**
     * Whether {@code victim} is close enough to fire on, surface to surface, as this weapon measures it.
     *
     * <p>Asked rather than told: the range is the weapon's and whoever is steering the unit has no business
     * keeping a second copy of it. See {@link PursueUpdate}, which is the one caller.
     */
    public boolean isInRange(GameObject victim) {
        return victim != null && rangeTo(getOwner(), victim) <= attackRange;
    }

    public ObjectId getTarget() {
        return target;
    }

    @Override
    public void update() {
        if (getOwner().isEffectivelyDead() || getOwner().isContained()
                || getOwner().hasStatus(ObjectStatus.DISABLED)) {
            return; // dead, inside a transport, or disabled — hold fire
        }
        clip.tick();

        var owner = getOwner();
        if (heldByAModule(owner)) {
            return; // busy with something else — see WeaponHold
        }
        if (!attackOnTheMove && isWalking(owner)) {
            // Reloading on the way, and keeping whatever it was aimed at, but not
            // firing. This has to live here rather than in whatever is steering the
            // unit: a weapon acquires its own target and fires in the same call, so
            // anything outside it can only ever disarm it a frame too late.
            return;
        }
        var world = owner.getWorld();
        if (world == null || clip.status() == WeaponStatus.OUT) {
            return; // an empty gun looks for nothing, and keeps what it had for when it is refilled
        }

        if (target == null) {
            acquireTarget(world, owner);
            if (target == null) {
                return;
            }
        }

        var victim = world.findObject(target);
        if (victim == null || victim.isEffectivelyDead() || victim.getBody() == null) {
            target = null;
            return;
        }
        if (world.getRelationship(owner.getPlayerIndex(), victim.getPlayerIndex()) == Relationship.ALLIES) {
            target = null; // never fire on allies
            return;
        }
        if (!canFireAt(victim)) {
            target = null; // it took off, or was never something this could hit
            return;
        }
        if (rangeTo(owner, victim) > attackRange) {
            return; // out of range — wait for movement to close in
        }
        if (clip.status() != WeaponStatus.READY) {
            return; // between shots, or refilling its clip
        }

        float dealt = damage * damageModifiers(owner);
        var shooter = RtsPlayer.of(world, owner.getPlayerIndex());
        if (shooter != null) {
            dealt *= shooter.getWeaponDamageBonus(); // player-wide upgrade bonus
        }

        // A shot was fired either way — the reload runs and the moment is
        // announced — but whether it lands now is the launcher's to decide.
        boolean inFlight = handOver(owner, victim, dealt);
        if (!inFlight) {
            victim.getBody().damage(dealt, damageType); // scaled by the victim's armor
        }
        clip.fired(world.random(), rateOfFire(owner));
        world.post(new WeaponFired(world.getFrame(), owner.getId(), victim.getId(),
                owner.getPosition(), victim.getPosition()));

        if (inFlight) {
            return; // nothing has been hit yet; splash and the kill wait with it
        }
        if (splashRadius > 0f) {
            applySplash(world, owner, victim, dealt);
        }

        if (victim.isEffectivelyDead()) {
            grantKillExperience(owner, victim);
            target = null;
        }
    }

    /**
     * Whether anything on this unit is holding its fire.
     *
     * <p>Walked in module order, which is fixed when the object is built — the
     * same rule {@link #damageModifiers} follows, and for the same reason.
     */
    private static boolean heldByAModule(GameObject owner) {
        for (var module : owner.getModules()) {
            if (module instanceof WeaponHold hold && hold.holdingFire()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the owner is under way — nothing to say if it cannot move at all. */
    private static boolean isWalking(GameObject owner) {
        var locomotor = owner.findModule(MoveUpdate.class);
        return locomotor != null && locomotor.isMoving();
    }

    /**
     * Offer the shot to a {@link ProjectileLauncher} on this unit, if it has one.
     *
     * <p>The first one found, in module order, which is fixed when the object is
     * built — so two peers hand the same shot to the same launcher.
     *
     * @return whether one took it
     */
    private boolean handOver(GameObject owner, GameObject victim, float dealt) {
        for (var module : owner.getModules()) {
            if (module instanceof ProjectileLauncher launcher
                    && launcher.launch(owner, victim, dealt, damageType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Everything attached to this unit that changes how hard it hits, multiplied
     * together.
     *
     * <p>Walked in module order, which is fixed when the object is built, so the
     * product is the same on every machine and in every replay.
     */
    private static float damageModifiers(GameObject owner) {
        float multiplier = 1f;
        for (var module : owner.getModules()) {
            if (module instanceof DamageModifier modifier) {
                multiplier *= modifier.damageMultiplier();
            }
        }
        return multiplier;
    }

    /** Everything on this unit that changes how fast it fires, multiplied together in module order. */
    private static float rateOfFire(GameObject owner) {
        float multiplier = 1f;
        for (var module : owner.getModules()) {
            if (module instanceof RateOfFireModifier modifier) {
                multiplier *= modifier.rateOfFireMultiplier();
            }
        }
        return multiplier;
    }

    /** Deal area damage to other enemies around the impact point. */
    private void applySplash(uz.dukeengine.core.thing.World world, GameObject owner, GameObject victim, float dealt) {
        int ownerPlayer = owner.getPlayerIndex();
        var caught = world.objectsInRange(victim.getPosition(), splashRadius, candidate ->
                candidate != victim
                        && candidate != owner
                        && !candidate.isContained()
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(ownerPlayer, candidate.getPlayerIndex()) == Relationship.ENEMIES);
        for (var bystander : caught) {
            bystander.getBody().damage(dealt, damageType);
            if (bystander.isEffectivelyDead()) {
                grantKillExperience(owner, bystander);
            }
        }
    }

    /** Award the killer the victim's experience value, if both track experience. */
    private static void grantKillExperience(GameObject killer, GameObject victim) {
        var killerXp = killer.findModule(ExperienceModule.class);
        var victimXp = victim.findModule(ExperienceModule.class);
        if (killerXp != null && victimXp != null) {
            killerXp.addExperience(victimXp.getExperienceValue());
        }
    }

    /**
     * How far the target is, measured wall to wall. A tank parked against a
     * barracks is at range 0 from it, not half a building away — the same rule
     * SAGE uses, and the reason a short-ranged unit can hit a big structure.
     */
    private static float rangeTo(GameObject owner, GameObject victim) {
        return uz.dukeengine.core.thing.World.reachBetween(owner, victim);
    }

    /** Pick the nearest living enemy within range that it may be fired at as the new target, if any. */
    private void acquireTarget(uz.dukeengine.core.thing.World world, GameObject owner) {
        var enemy = world.findClosestInReach(owner, attackRange, candidate ->
                candidate != owner
                        && !candidate.isContained()
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(owner.getPlayerIndex(), candidate.getPlayerIndex())
                                == Relationship.ENEMIES
                        && canFireAt(candidate));
        if (enemy != null) {
            target = enemy.getId();
        }
    }
}
