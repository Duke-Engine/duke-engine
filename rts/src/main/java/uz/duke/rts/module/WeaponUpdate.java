package uz.duke.rts.module;

import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.module.DamageType;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.module.ModuleGroups;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ObjectStatus;
import uz.duke.rts.event.WeaponFired;
import uz.duke.rts.player.RtsPlayer;

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
     */
    public record Data(float damage, float attackRange, int reloadFrames,
            DamageType damageType, float splashRadius,
            boolean attackOnTheMove) implements ModuleData {
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
    }

    private static final class DataBuilder {
        float damage;
        float attackRange;
        int reloadFrames;
        DamageType damageType = DamageType.NORMAL;
        float splashRadius;
        boolean attackOnTheMove = true;

        Data build() {
            return new Data(damage, attackRange, reloadFrames, damageType, splashRadius,
                    attackOnTheMove);
        }
    }

    private static final FieldParseTable<DataBuilder> DATA_TABLE = new FieldParseTable<DataBuilder>()
            .add("Damage", Ini.real((b, v) -> b.damage = v))
            .add("AttackRange", Ini.real((b, v) -> b.attackRange = v))
            .add("ReloadFrames", Ini.integer((b, v) -> b.reloadFrames = v))
            .add("DamageType", Ini.enumeration(DamageType.class, (b, v) -> b.damageType = v))
            .add("SplashRadius", Ini.real((b, v) -> b.splashRadius = v))
            .add("AttackOnTheMove", Ini.bool((b, v) -> b.attackOnTheMove = v));

    public static ModuleData parseData(Ini ini) {
        var builder = new DataBuilder();
        ini.initFromIni(builder, DATA_TABLE);
        return builder.build();
    }

    private final float damage;
    private final float attackRange;
    private final int reloadFrames;
    private final DamageType damageType;
    private final float splashRadius;
    private final boolean attackOnTheMove;

    private ObjectId target;
    private int cooldown;

    public WeaponUpdate(GameObject owner, Data data) {
        super(owner);
        this.damage = data.damage();
        this.attackRange = data.attackRange();
        this.reloadFrames = data.reloadFrames();
        this.damageType = data.damageType();
        this.splashRadius = data.splashRadius();
        this.attackOnTheMove = data.attackOnTheMove();
    }

    /** Order this weapon to engage {@code target}. */
    public void attack(ObjectId target) {
        this.target = target;
    }

    public void holdFire() {
        this.target = null;
    }

    public boolean isAttacking() {
        return target != null;
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
        if (cooldown > 0) {
            cooldown--;
        }

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
        if (world == null) {
            return;
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
        if (rangeTo(owner, victim) > attackRange) {
            return; // out of range — wait for movement to close in
        }
        if (cooldown > 0) {
            return; // still reloading
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
        cooldown = reloadFrames;
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

    /** Deal area damage to other enemies around the impact point. */
    private void applySplash(uz.duke.core.thing.World world, GameObject owner, GameObject victim, float dealt) {
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
        return uz.duke.core.thing.World.reachBetween(owner, victim);
    }

    /** Pick the nearest living enemy within range as the new target, if any. */
    private void acquireTarget(uz.duke.core.thing.World world, GameObject owner) {
        var enemy = world.findClosestInReach(owner, attackRange, candidate ->
                candidate != owner
                        && !candidate.isContained()
                        && candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(owner.getPlayerIndex(), candidate.getPlayerIndex())
                                == Relationship.ENEMIES);
        if (enemy != null) {
            target = enemy.getId();
        }
    }
}
