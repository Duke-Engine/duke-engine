package uz.dukeengine.rts.module;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;

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

    /** {@code Amount}, what it starts with; {@code Dock}, how it takes its harvesters — see {@link Dock} — or none. */
    public record Data(int amount, Dock dock) implements ModuleData {

        /** A pile any number of harvesters load at at once. */
        public Data(int amount) {
            this(amount, null);
        }
    }

    private int remaining;
    private final Docking docking;

    public SupplyModule(GameObject owner, Data data) {
        super(owner);
        this.remaining = data.amount();
        this.docking = data.dock() == null ? null : new Docking(owner, data.dock());
    }

    /** Its dock's harvesters, or null where any number load at once. */
    Docking docking() {
        return docking;
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
