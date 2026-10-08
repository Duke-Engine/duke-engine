package uz.dukeengine.rpg.item;

import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgModuleGroups;

/**
 * What a monster leaves on the floor when it finally goes.
 *
 * <p>Hung on the engine's {@link DieModule} seam, which is where the gameplay
 * half of a death belongs — it runs once the corpse has left the world, so the
 * thing it drops lands somewhere the body no longer is. The other half of a
 * death, the one the renderer watches, is an event and stays an event.
 *
 * <p>Attached when the monster is spawned rather than written into its creature
 * block, beside its level and for the same reason: what a monster leaves
 * depends on the floor it was found on, and a template says what a thing is, not
 * where it was met.
 */
@ModuleGroup(RpgModuleGroups.ITEMS)
public final class LootDrop extends Module implements DieModule {

    private final LootTable table;
    private final String chestTemplate;
    private final int tier;
    private final boolean boss;

    public LootDrop(GameObject owner, LootTable table, String chestTemplate, int tier,
            boolean boss) {
        super(owner);
        this.table = table;
        this.chestTemplate = chestTemplate;
        this.tier = tier;
        this.boss = boss;
    }

    @Override
    public void onDie() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || chestTemplate == null || chestTemplate.isBlank()) {
            return;
        }
        var item = table.dropFor(world.random(), tier, boss);
        if (item == null) {
            return; // most deaths leave nothing, which is what makes the rest worth it
        }
        GroundItem.lay(world, chestTemplate, item, owner.getPosition(), owner.getPlayerIndex());
    }
}
