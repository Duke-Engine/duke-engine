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
public final class SupplyDepot extends Module {

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
}
