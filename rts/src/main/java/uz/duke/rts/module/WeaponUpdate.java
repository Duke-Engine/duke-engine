package uz.duke.rts.module;

import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.module.DamageType;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
import uz.duke.core.thing.ObjectStatus;

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
 */
public final class WeaponUpdate extends UpdateModule {

    /**
     * INI configuration: {@code Damage}, {@code AttackRange}, {@code ReloadFrames},
     * a {@link DamageType}, and a {@code SplashRadius} for area damage. The shorter
     * forms default the trailing fields, keeping existing call sites working.
     */
    public record Data(float damage, float attackRange, int reloadFrames,
            DamageType damageType, float splashRadius) implements ModuleData {
        public Data(float damage, float attackRange, int reloadFrames) {
            this(damage, attackRange, reloadFrames, DamageType.NORMAL, 0f);
        }

        public Data(float damage, float attackRange, int reloadFrames, DamageType damageType) {
            this(damage, attackRange, reloadFrames, damageType, 0f);
        }
    }

    private static final class DataBuilder {
        float damage;
        float attackRange;
        int reloadFrames;
        DamageType damageType = DamageType.NORMAL;
        float splashRadius;

        Data build() {
            return new Data(damage, attackRange, reloadFrames, damageType, splashRadius);
        }
    }

    private static final FieldParseTable<DataBuilder> DATA_TABLE = new FieldParseTable<DataBuilder>()
            .add("Damage", Ini.real((b, v) -> b.damage = v))
            .add("AttackRange", Ini.real((b, v) -> b.attackRange = v))
            .add("ReloadFrames", Ini.integer((b, v) -> b.reloadFrames = v))
            .add("DamageType", Ini.enumeration(DamageType.class, (b, v) -> b.damageType = v))
            .add("SplashRadius", Ini.real((b, v) -> b.splashRadius = v));

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

    private ObjectId target;
    private int cooldown;

    public WeaponUpdate(GameObject owner, Data data) {
        super(owner);
        this.damage = data.damage();
        this.attackRange = data.attackRange();
        this.reloadFrames = data.reloadFrames();
        this.damageType = data.damageType();
        this.splashRadius = data.splashRadius();
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
        if (owner.getPosition().distance(victim.getPosition()) > attackRange) {
            return; // out of range — wait for movement to close in
        }
        if (cooldown > 0) {
            return; // still reloading
        }

        float dealt = damage;
        var experience = owner.findModule(ExperienceModule.class);
        if (experience != null) {
            dealt *= experience.getDamageMultiplier(); // veterancy bonus
        }
        var shooter = world.getPlayer(owner.getPlayerIndex());
        if (shooter != null) {
            dealt *= shooter.getWeaponDamageBonus(); // player-wide upgrade bonus
        }
        victim.getBody().damage(dealt, damageType); // scaled by the victim's armor
        cooldown = reloadFrames;

        if (splashRadius > 0f) {
            applySplash(world, owner, victim, dealt);
        }

        if (victim.isEffectivelyDead()) {
            grantKillExperience(owner, victim);
            target = null;
        }
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

    /** Pick the nearest living enemy within range as the new target, if any. */
    private void acquireTarget(uz.duke.core.thing.World world, GameObject owner) {
        var enemy = world.findClosest(owner.getPosition(), attackRange, candidate ->
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
