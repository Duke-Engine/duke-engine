package uz.duke.rts.module;

import uz.duke.core.ini.FieldParseTable;
import uz.duke.core.ini.Ini;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.player.Relationship;
import uz.duke.core.thing.GameObject;

/**
 * A rechargeable area-effect ability, ported in spirit from SAGE's
 * {@code SpecialPower} (fuel-air bomb, artillery barrage, etc.).
 *
 * <p>Carried by a structure. When {@link #fire} is invoked and the power is off
 * cooldown, it deals area damage to every living enemy within its radius of the
 * target point, then begins recharging. The cooldown ticks down each frame.
 * Friendly units are never hit.
 */
public final class SpecialPowerModule extends UpdateModule {

    /** INI config: {@code RechargeFrames}, {@code Radius}, {@code Damage}. */
    public record Data(int rechargeFrames, float radius, float damage) implements ModuleData {
    }

    private static final class DataBuilder {
        int rechargeFrames;
        float radius;
        float damage;

        Data build() {
            return new Data(rechargeFrames, radius, damage);
        }
    }

    private static final FieldParseTable<DataBuilder> DATA_TABLE = new FieldParseTable<DataBuilder>()
            .add("RechargeFrames", Ini.integer((b, v) -> b.rechargeFrames = v))
            .add("Radius", Ini.real((b, v) -> b.radius = v))
            .add("Damage", Ini.real((b, v) -> b.damage = v));

    public static ModuleData parseData(Ini ini) {
        var builder = new DataBuilder();
        ini.initFromIni(builder, DATA_TABLE);
        return builder.build();
    }

    private final int rechargeFrames;
    private final float radius;
    private final float damage;
    private int cooldown;

    public SpecialPowerModule(GameObject owner, Data data) {
        super(owner);
        this.rechargeFrames = data.rechargeFrames();
        this.radius = data.radius();
        this.damage = data.damage();
    }

    public boolean isReady() {
        return cooldown <= 0;
    }

    public int getCooldown() {
        return cooldown;
    }

    /**
     * Unleash the power at {@code target}, damaging enemies in radius and starting
     * the recharge. Returns false (and does nothing) if still recharging.
     */
    public boolean fire(Coord3D target) {
        if (cooldown > 0) {
            return false;
        }
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null) {
            return false;
        }
        int ownerPlayer = owner.getPlayerIndex();
        var victims = world.objectsInRange(target, radius, candidate ->
                candidate.getBody() != null
                        && !candidate.isEffectivelyDead()
                        && world.getRelationship(ownerPlayer, candidate.getPlayerIndex()) == Relationship.ENEMIES);
        for (var victim : victims) {
            victim.getBody().damage(damage);
        }
        cooldown = rechargeFrames;
        return true;
    }

    @Override
    public void update() {
        if (cooldown > 0) {
            cooldown--;
        }
    }
}
