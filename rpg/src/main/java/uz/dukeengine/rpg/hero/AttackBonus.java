package uz.dukeengine.rpg.hero;

import uz.dukeengine.combat.module.DamageModifier;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;

/**
 * What a hero's blow is worth now, as a share of the blow his weapon was built with: set by {@link HeroProgress} from
 * his level, his attributes and what he carries, and multiplied into every blow by the weapon, as the reference's
 * bonuses are. Added when first wanted, as {@link AttackSpeed} is.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class AttackBonus extends Module implements DamageModifier, Saveable {

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

    @Override
    public void save(SaveOut out) {
        out.writeFloat(multiplier);
    }

    @Override
    public void load(SaveIn in) {
        throw new IllegalStateException("an attack bonus is made again by AttackBonus.revive, never by its template");
    }

    /** One added while the match ran, made again at the figure it saved: what {@code ModuleFactory.revives} is given. */
    public static AttackBonus revive(GameObject owner, SaveIn in) {
        var bonus = new AttackBonus(owner);
        bonus.multiplier = in.readFloat();
        return bonus;
    }
}
