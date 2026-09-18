package uz.duke.dungeon.loot;

import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.rts.module.RtsModuleGroups;

/**
 * A thing lying on the floor, waiting to be walked over.
 *
 * <p>There is no picking-up button and no inventory: he stands on it and it is
 * his. That is the whole interaction, and it is the right one for a game whose
 * decisions are about where to walk — going to fetch something is the decision,
 * and a second one at the end of it would be a formality.
 *
 * <p>Whose it is, is decided by having skills, the same rule
 * {@link uz.duke.dungeon.skill.Skills} uses to find a hero. A dungeon has one,
 * and saying so this way means a second hero would pick things up without
 * anything here being told about him.
 *
 * <p>Deterministic: a distance in the simulation's own units, checked on a frame
 * boundary like everything else. What it holds was settled when it dropped.
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class LootUpdate extends UpdateModule {

    private final LootBag bag;
    private final float pickupRange;
    private final int noteFrames;

    private Loot holding;

    public LootUpdate(GameObject owner, LootBag bag, float pickupRange, int noteFrames) {
        super(owner);
        this.bag = bag;
        this.pickupRange = pickupRange;
        this.noteFrames = noteFrames;
    }

    /** The empty block that puts this on the chest; the monster that dropped it fills the rest in. */
    public static ModuleData parseData(uz.duke.core.ini.Ini ini) {
        ini.initFromIni(new Object(), NO_FIELDS);
        return null;
    }

    private static final uz.duke.core.ini.FieldParseTable<Object> NO_FIELDS =
            new uz.duke.core.ini.FieldParseTable<>();

    /** Say what is in it. Called once, by whatever dropped it. */
    void holds(Loot item) {
        this.holding = item;
    }

    /** What is in it, for a test that would rather not go and stand on it. */
    public Loot getHolding() {
        return holding;
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || holding == null) {
            return;
        }
        var taker = world.objectsInRange(owner.getPosition(), pickupRange,
                candidate -> !candidate.isEffectivelyDead()
                        && candidate.findModule(SkillBook.class) != null);
        if (taker.isEmpty()) {
            return;
        }
        bag.take(holding, world.getFrame(), noteFrames);
        holding = null;
        // Gone the moment it is his: a chest that stayed would be picked up again
        // every frame he stood on it.
        owner.markDestroyed();
    }
}
