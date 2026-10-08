package uz.dukeengine.rpg.hero;

import uz.dukeengine.combat.module.DamageModifier;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;

/**
 * What a hero's blow is worth now, as a share of the blow his weapon was built with: set by {@link HeroProgress} from
 * his level, his attributes and what he carries, and multiplied into every blow by the weapon, as the reference's
 * bonuses are. Added when first wanted, as {@link AttackSpeed} is.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class AttackBonus extends Module implements DamageModifier {

    private float multiplier = 1f;

    public AttackBonus(GameObject owner) {
        super(owner);
    }

    public void multiplier(float multiplier) {
        this.multiplier = multiplier;
    }

    @Override
    public float damageMultiplier() {
        return multiplier;
    }
}
