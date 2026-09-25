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
    /** What it produces over its block's figure now — see {@link #setBonus}. */
    private int bonus;

    public PowerModule(GameObject owner, Data data) {
        super(owner);
        this.produced = data.produces();
        this.consumed = data.consumes();
    }

    /** What it produces now: its block's figure and its bonus. */
    public int getProduced() {
        return produced + bonus;
    }

    /**
     * Produce {@code bonus} more than its block says from now on — a reactor overcharged, 10 to 15; control rods, 5
     * to 10: the reference's EnergyBonus — counted by its side's surplus at once; 0 takes it away. Settable from any
     * module's update, its own thing's included, where swapping the module is not; it goes with the thing.
     */
    public void setBonus(int bonus) {
        this.bonus = bonus;
    }

    public int getBonus() {
        return bonus;
    }

    public int getConsumed() {
        return consumed;
    }
}
