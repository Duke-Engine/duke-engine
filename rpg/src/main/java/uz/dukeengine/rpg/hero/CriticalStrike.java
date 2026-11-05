package uz.dukeengine.rpg.hero;

import uz.dukeengine.combat.module.BlowRoll;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;

/**
 * A hero's critical strikes, from what he wears: {@code chance} percent of his blows and his skills' damage land
 * {@code damage} percent harder, drawn from the world's own random numbers on each — so a party crits alike. Set by
 * {@link HeroProgress}; added when first wanted, as {@link AttackSpeed} is.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class CriticalStrike extends Module implements BlowRoll, Saveable {

    private int chance;
    private int damage;

    public CriticalStrike(GameObject owner) {
        super(owner);
    }

    /** {@code chance} percent of blows, each {@code damage} percent harder. */
    public void figures(int chance, int damage) {
        this.chance = Math.clamp(chance, 0, 100);
        this.damage = Math.max(0, damage);
    }

    public int chance() {
        return chance;
    }

    public int damage() {
        return damage;
    }

    @Override
    public float roll(World world) {
        if (chance <= 0 || world == null) {
            return 1f;
        }
        return world.random().nextFloat() * 100f < chance ? 1f + damage / 100f : 1f;
    }

    @Override
    public void save(SaveOut out) {
        out.writeInt(chance).writeInt(damage);
    }

    @Override
    public void load(SaveIn in) {
        throw new IllegalStateException("a critical strike is made again by CriticalStrike.revive, never by its"
                + " template");
    }

    /** One added while the match ran, made again with the figures it saved: what {@code ModuleFactory.revives} is given. */
    public static CriticalStrike revive(GameObject owner, SaveIn in) {
        var critical = new CriticalStrike(owner);
        critical.chance = in.readInt();
        critical.damage = in.readInt();
        return critical;
    }
}
