package uz.dukeengine.rpg.hero;

import uz.dukeengine.combat.module.BlowRoll;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;

/**
 * A hero's critical strikes, from what he wears: {@code chance} percent of his blows and his skills' damage land
 * {@code damage} percent harder, drawn from the world's own random numbers on each — so a party crits alike. Set by
 * {@link HeroProgress}; added when first wanted, as {@link AttackSpeed} is.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class CriticalStrike extends Module implements BlowRoll {

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
}
