package uz.dukeengine.rpg.hero;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.combat.module.RateOfFireModifier;

/**
 * How much quicker his blows come than his weapon's own rate: every wait of it shortened by the engine, which
 * divides by what this says ({@link RateOfFireModifier}). Put on the hero by {@link HeroProgress} the first time
 * something he carries asks for it, and told the figure every time what he carries changes.
 */
@ModuleGroup(ModuleGroups.COMBAT)
public final class AttackSpeed extends Module implements RateOfFireModifier, Saveable {

    private float multiplier = 1f;

    public AttackSpeed(GameObject owner) {
        super(owner);
    }

    /** This many percent quicker; nothing below none. */
    public void percent(int quicker) {
        multiplier = 1f + Math.max(0, quicker) / 100f;
    }

    @Override
    public float rateOfFireMultiplier() {
        return multiplier;
    }

    @Override
    public void save(SaveOut out) {
        out.writeFloat(multiplier);
    }

    @Override
    public void load(SaveIn in) {
        throw new IllegalStateException("an attack speed is made again by AttackSpeed.revive, never by its template");
    }

    /** One added while the match ran, made again at the figure it saved: what {@code ModuleFactory.revives} is given. */
    public static AttackSpeed revive(GameObject owner, SaveIn in) {
        var quick = new AttackSpeed(owner);
        quick.multiplier = in.readFloat();
        return quick;
    }
}
