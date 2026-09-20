package uz.dukeengine.rts.module;

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
public final class AutoHealUpdate extends UpdateModule {

    /** INI config: {@code HealPerSecond}. */
    public record Data(float healPerSecond) implements ModuleData {
    }

    private final float healPerFrame;

    public AutoHealUpdate(GameObject owner, Data data) {
        super(owner);
        this.healPerFrame = data.healPerSecond() * GameConstants.SECONDS_PER_LOGICFRAME;
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
