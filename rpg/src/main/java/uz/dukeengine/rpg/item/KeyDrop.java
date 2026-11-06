package uz.dukeengine.rpg.item;

import java.util.function.Function;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgModuleGroups;

/**
 * What a hero leaves where he falls: the key, if he carries it — so a party never loses the way on with him, and the
 * others can take it up where it lies.
 *
 * <p>Hung on the engine's {@link DieModule} seam, as a monster's {@link LootDrop} is and for the same reason: it runs
 * once he has left the world, and what it lays lies where he fell. Everything else in his bag stays his, for when he
 * stands again — and so does a key that cannot be laid, one whose template nobody has made: it is laid first, and
 * leaves his bag only once something lies where it fell, so it is never lost between the bag and the floor.
 */
@ModuleGroup(RpgModuleGroups.ITEMS)
public final class KeyDrop extends Module implements DieModule, Saveable {

    private final LootBag bag;
    private final String chest;
    private final int floorOwner;

    /**
     * @param chest      what a thing lies in when it names nothing of its own
     * @param floorOwner whose a thing on the floor is: the dungeon's
     */
    public KeyDrop(GameObject hero, LootBag bag, String chest, int floorOwner) {
        super(hero);
        this.bag = bag;
        this.chest = chest;
        this.floorOwner = floorOwner;
    }

    @Override
    public void onDie() {
        var hero = getOwner();
        var world = hero.getWorld();
        if (world == null) {
            return;
        }
        for (int slot = 0; slot < bag.slots().size(); slot++) {
            var item = bag.at(slot);
            if (item != null && item.use() == ItemUse.UNLOCK
                    && GroundItem.lay(world, item.liesAs(chest), item, hero.getPosition(), floorOwner) != null) {
                bag.remove(slot);
            }
        }
    }

    @Override
    public void save(SaveOut out) {
        out.writeString(chest).writeInt(floorOwner);
    }

    @Override
    public void load(SaveIn in) {
        throw new IllegalStateException("a key drop is made again by KeyDrop.reviver, never by its template");
    }

    /**
     * What {@code ModuleFactory.revives} is given for a game whose heroes' bags {@code bags} finds: the bag is the
     * game's, so a save holds only what it was told besides it.
     */
    public static Saveable.Reviver<KeyDrop> reviver(Function<GameObject, LootBag> bags) {
        return (hero, in) -> new KeyDrop(hero, LootBag.of(bags, hero, KeyDrop.class), in.readString(), in.readInt());
    }
}
