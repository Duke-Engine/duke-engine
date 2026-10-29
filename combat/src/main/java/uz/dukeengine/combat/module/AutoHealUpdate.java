package uz.dukeengine.combat.module;

import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.module.BodyModule;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;

/**
 * Regenerates the owner's health over time, ported in spirit from SAGE's
 * {@code AutoHealBehavior}.
 *
 * <p>Each frame it heals the owner's body by a fixed rate (authored in health
 * per second, converted to a per-frame amount on the fixed logic clock).
 * {@link BodyModule#heal} clamps to the maximum, so a unit recovers toward full
 * health and stops there. A dead unit is never revived.
 */
@ModuleGroup(ModuleGroups.BODY)
public final class AutoHealUpdate extends UpdateModule implements uz.dukeengine.core.save.Saveable {

    /** INI config: {@code HealPerSecond}. */
    public record Data(float healPerSecond) implements ModuleData {
    }

    private final float healPerSecond;
    private final float healPerFrame;

    public AutoHealUpdate(GameObject owner, Data data) {
        super(owner);
        this.healPerSecond = data.healPerSecond();
        this.healPerFrame = data.healPerSecond() * GameConstants.SECONDS_PER_LOGICFRAME;
    }

    /**
     * Its rate, which is all it holds: the same as its template's for one its template made, and what makes again one a
     * game added while the match ran (an upgrade's own mending) — see {@link #revive}.
     */
    @Override
    public void save(uz.dukeengine.core.save.SaveOut out) {
        out.writeFloat(healPerSecond);
    }

    @Override
    public void load(uz.dukeengine.core.save.SaveIn in) {
        float saved = in.readFloat();
        if (saved != healPerSecond) {
            throw new uz.dukeengine.core.save.UnsavedModuleException(getClass().getName(), "a self-healer saved at "
                    + saved + " a second is loaded as its template's, at " + healPerSecond);
        }
    }

    /** One added while the match ran, made again at the rate it saved. */
    public static AutoHealUpdate revive(GameObject owner, uz.dukeengine.core.save.SaveIn in) {
        return new AutoHealUpdate(owner, new Data(in.readFloat()));
    }

    @Override
    public void update() {
        var body = getOwner().getBody();
        if (body == null || body.isDead()) {
            return; // nothing to heal, and the dead stay dead
        }
        body.heal(healPerFrame);
    }
}
