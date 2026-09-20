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

    public record Data() implements ModuleData {
    }

    public SupplyDepot(GameObject owner, Data data) {
        super(owner);
    }
}
