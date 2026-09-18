package uz.duke.rts.module;

import uz.duke.core.module.Module;
import uz.duke.core.module.ModuleData;
import uz.duke.core.ini.Ini;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.thing.GameObject;

/**
 * Holds up production while its owner's side is spending more capacity than it
 * provides.
 *
 * <p>Nearly every RTS has a building whose job is to raise a ceiling, and nearly
 * every one dresses it differently: Generals calls it power and stalls the base
 * when the plants cannot keep up, Warcraft calls it food and refuses to train past
 * the farms, BFME fixes the cap outright. Underneath they are one mechanism — a
 * balance of what a side supplies against what it uses — and one common rule about
 * what happens when it goes negative.
 *
 * <p>The balance lives in {@link PowerModule} on each object and is summed by
 * {@link PowerGrid}. This is the rule, and it is <b>opt-in</b>: a factory carries
 * it only if the game says so in its definition. That is the difference from
 * before, when the rule was written into the production line itself and applied to
 * every game whether or not it had any notion of capacity at all.
 *
 * <pre>{@code
 * Behavior = CapacityGate Tag
 * End
 * }</pre>
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class CapacityGate extends Module implements ProductionGate {

    /** No configuration: the rule is the same wherever it is attached. */
    public record Data() implements ModuleData {
    }

    private static final Data DATA = new Data();

    public static ModuleData parseData(Ini ini) {
        ini.initFromIni(DATA, new uz.duke.core.ini.FieldParseTable<Data>());
        return DATA;
    }

    public CapacityGate(GameObject owner, Data data) {
        super(owner);
    }

    @Override
    public boolean canProduce() {
        var world = getOwner().getWorld();
        return world == null || PowerGrid.isPowered(world, getOwner().getPlayerIndex());
    }
}
