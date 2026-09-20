package uz.dukeengine.rts.module;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;

/**
 * Contributes to or draws from a player's power grid, ported in spirit from
 * SAGE's {@code PowerPlantUpdate} / power consumption on structures.
 *
 * <p>Power plants {@link #getProduced() produce}; most structures
 * {@link #getConsumed() consume}. The player's net surplus determines whether
 * power-dependent structures function — when a base is under-powered, production
 * stalls and defenses go offline, exactly as in Generals.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class PowerModule extends Module {

    /** {@code Produces} / {@code Consumes} (power units). */
    public record Data(int produces, int consumes) implements ModuleData {
    }

    private final int produced;
    private final int consumed;

    public PowerModule(GameObject owner, Data data) {
        super(owner);
        this.produced = data.produces();
        this.consumed = data.consumes();
    }

    public int getProduced() {
        return produced;
    }

    public int getConsumed() {
        return consumed;
    }
}
