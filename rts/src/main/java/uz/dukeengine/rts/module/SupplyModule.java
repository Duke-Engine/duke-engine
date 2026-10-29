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
public final class SupplyModule extends Module implements uz.dukeengine.core.save.Saveable {

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

    /**
     * Close its dock, or open it again: see {@link Dock}. Closed, the one in finishes, those waiting by it keep their
     * places, and no harvester new to it is taken until it opens. One that names no dock has none to close.
     */
    public void setDockClosed(boolean closed) {
        if (docking == null) {
            java.util.logging.Logger.getLogger(SupplyModule.class.getName()).warning(
                    () -> getOwner().getTemplate().name() + " names no Dock to close");
            return;
        }
        docking.setClosed(closed);
    }

    /** Whether its dock is closed. */
    public boolean isDockClosed() {
        return docking != null && docking.isClosed();
    }

    public int getRemaining() {
        return remaining;
    }

    @Override
    public void save(uz.dukeengine.core.save.SaveOut out) {
        out.writeInt(remaining);
        Docking.save(docking, out);
    }

    @Override
    public void load(uz.dukeengine.core.save.SaveIn in) {
        remaining = in.readInt();
        Docking.load(docking, in, SupplyModule.class.getName());
    }

    /** Remove up to {@code requested}; returns the amount actually taken. */
    public int take(int requested) {
        int taken = Math.min(Math.max(requested, 0), remaining);
        remaining -= taken;
        return taken;
    }
}
