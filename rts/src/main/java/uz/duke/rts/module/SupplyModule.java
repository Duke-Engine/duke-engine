package uz.duke.rts.module;

import uz.duke.core.module.Module;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.thing.GameObject;

/**
 * A finite resource pile that harvesters draw from, ported in spirit from SAGE's
 * supply docks / supply piles.
 *
 * <p>Holds a {@link #getRemaining() remaining} amount; {@link #take} removes up
 * to a requested quantity and returns how much was actually available. When it
 * hits zero the pile is exhausted.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class SupplyModule extends Module {

    /** INI config: {@code Amount} (starting resources). */
    public record Data(int amount) implements ModuleData {
    }

    private int remaining;

    public SupplyModule(GameObject owner, Data data) {
        super(owner);
        this.remaining = data.amount();
    }

    public int getRemaining() {
        return remaining;
    }

    /** Remove up to {@code requested}; returns the amount actually taken. */
    public int take(int requested) {
        int taken = Math.min(Math.max(requested, 0), remaining);
        remaining -= taken;
        return taken;
    }
}
