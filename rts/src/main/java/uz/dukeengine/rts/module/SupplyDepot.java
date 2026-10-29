package uz.dukeengine.rts.module;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;

/**
 * Marks a thing as somewhere a harvester unloads.
 *
 * <p>Nothing but a mark, and that is the whole of it: a depot does not decide who may use it, how much it
 * holds or what it looks like — a game answers all of that by which of its buildings it puts this on. Without
 * one, {@link HarvestUpdate} banks its load where it stands, which is how the gather loop worked before any of
 * this and is still what a game with no depots gets.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class SupplyDepot extends Module implements uz.dukeengine.core.save.Saveable {

    /** {@code Dock}: how it takes its harvesters — see {@link Dock} — or none, any number banking at once. */
    public record Data(Dock dock) implements ModuleData {

        /** A depot any number of harvesters bank at at once. */
        public Data() {
            this(null);
        }
    }

    private final Docking docking;

    public SupplyDepot(GameObject owner, Data data) {
        super(owner);
        this.docking = data.dock() == null ? null : new Docking(owner, data.dock());
    }

    /** Its dock's harvesters, or null where any number bank at once. */
    Docking docking() {
        return docking;
    }

    /**
     * Close its dock, or open it again: see {@link Dock}. Closed, the one in finishes, those waiting by it keep their
     * places, and no harvester new to it is taken until it opens. One that names no dock has none to close.
     */
    public void setDockClosed(boolean closed) {
        if (docking == null) {
            java.util.logging.Logger.getLogger(SupplyDepot.class.getName()).warning(
                    () -> getOwner().getTemplate().name() + " names no Dock to close");
            return;
        }
        docking.setClosed(closed);
    }

    /** Whether its dock is closed. */
    public boolean isDockClosed() {
        return docking != null && docking.isClosed();
    }

    @Override
    public void save(uz.dukeengine.core.save.SaveOut out) {
        Docking.save(docking, out);
    }

    @Override
    public void load(uz.dukeengine.core.save.SaveIn in) {
        Docking.load(docking, in, SupplyDepot.class.getName());
    }
}
