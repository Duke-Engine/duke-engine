package uz.dukeengine.rpg.mission;

import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.DieModule;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rpg.item.GroundItem;
import uz.dukeengine.rpg.item.Loot;
import uz.dukeengine.rpg.item.LootBag;
import uz.dukeengine.rpg.item.ItemUse;
import uz.dukeengine.rpg.RpgModuleGroups;

/**
 * What a guarded place asks of the party, in four steps: kill everything counted, take the key the last of them
 * leaves where it fell, give the key to the gate, and kill what the gate kept.
 *
 * <p>Kept by the game, one to a place, and the same on every machine: it counts the deaths the engine tells it of,
 * on the frame they happen, lays the key with the world's own spawn, and reads the rest off the world as it stands —
 * whether anyone carries the key, whether the gate still stands. Nothing here reads a clock or a hash.
 *
 * <p>Which things are counted, which item is the key and where it lies when nothing is counted are the game's: what
 * a summoner calls up later was never counted.
 */
public final class Mission {

    /** The four steps, in the order a floor asks them. */
    public enum Step {
        /** Kill everything the floor put outside the keep. */
        CLEAR,
        /** Take the key the last of them left. */
        TAKE,
        /** Give it to the gate. */
        GIVE,
        /** Kill the boss: the gate is open, or there was no keep. */
        KILL
    }

    private final Loot key;
    /** What a thing lies in when it names nothing of its own; the key names itself. */
    private final String chest;
    private final int floorOwner;
    /** The gate across the keep's doorway, or null where the floor stood none. */
    private final ObjectId gate;
    /** How many the floor put outside the keep, and how many of them have died. */
    private final int outside;
    /** How many of the counted may still stand when the key is laid; 0 for none. */
    private final int leeway;
    /** The counted, by id, so how many still stand is read off the world. */
    private final List<ObjectId> counted;
    private int killed;
    private boolean keyLaid;

    private Mission(Loot key, String chest, int floorOwner, ObjectId gate, List<ObjectId> counted, int leeway) {
        this.key = key;
        this.chest = chest;
        this.floorOwner = floorOwner;
        this.gate = gate;
        this.counted = counted;
        this.outside = counted.size();
        this.leeway = Math.clamp(leeway, 0, Math.max(0, outside - 1));
    }

    /**
     * Begin one: each of {@code counted} is told to report its death, and with none to count the key is laid now at
     * {@code wayIn}.
     *
     * @param key        what the last death leaves: an item used to unlock
     * @param chest      what an item lies in when it names nothing of its own
     * @param floorOwner the side what lies on the ground belongs to
     * @param gate       what the key is given to, or null where nothing is guarded
     */
    public static Mission begin(World world, Loot key, String chest, int floorOwner, GameObject gate,
            List<GameObject> counted, Coord3D wayIn) {
        return begin(world, key, chest, floorOwner, gate, counted, wayIn, 0);
    }

    /**
     * The same, the key laid at the death that leaves no more than {@code leeway} of the counted standing — one that has
     * left the world or is dead does not stand — where that one fell: a straggler in a far corner keeps no gate shut.
     * Never all of them: a leeway of as many as were counted is held to one fewer.
     */
    public static Mission begin(World world, Loot key, String chest, int floorOwner, GameObject gate,
            List<GameObject> counted, Coord3D wayIn, int leeway) {
        var mission = new Mission(key, chest, floorOwner, gate == null ? null : gate.getId(),
                counted.stream().map(GameObject::getId).toList(), leeway);
        for (var thing : counted) {
            thing.addModule(new Counted(thing, mission));
        }
        if (counted.isEmpty()) {
            mission.lay(world, wayIn);
        }
        return mission;
    }

    /** One of the floor's own outside the keep has died: the last of them leaves the key where it fell. */
    private void fell(GameObject monster) {
        killed = Math.min(killed + 1, outside);
        if (!keyLaid && (killed == outside || leeway > 0 && standing(monster) <= leeway)) {
            lay(monster.getWorld(), monster.getPosition());
        }
    }

    /** How many of the counted stand, the one dying now not among them. */
    private int standing(GameObject dying) {
        var world = dying.getWorld();
        int standing = 0;
        for (var id : counted) {
            var thing = world.findObject(id);
            if (thing != null && thing != dying && !thing.isDestroyed()
                    && (thing.getBody() == null || !thing.getBody().isDead())) {
                standing++;
            }
        }
        return standing;
    }

    /**
     * Lay the key on the floor at {@code at}. Where that cannot be done — the template it lies as is one nobody has
     * made, or one that holds nothing — the key would lie nowhere and the gate never open, so it is said, loudly and
     * naming the template, rather than left as a floor that cannot be finished.
     */
    private void lay(World world, Coord3D at) {
        var template = key.liesAs(chest);
        if (GroundItem.lay(world, template, key, at, floorOwner) == null) {
            throw new IllegalStateException("the floor's key cannot be laid: '" + template
                    + "' is not a template of a thing that lies holding an item");
        }
        keyLaid = true;
    }

    /** Where the party stands in it now: the key not laid yet, lying, carried, or given and the gate open. */
    public Step step(World world, List<LootBag> bags) {
        if (!keyLaid) {
            return Step.CLEAR;
        }
        if (gate != null && GateUpdate.stands(world, gate)) {
            return bags.stream().anyMatch(bag -> bag.holds(ItemUse.UNLOCK)) ? Step.GIVE : Step.TAKE;
        }
        return Step.KILL;
    }

    /** How many monsters the floor put outside its keep. */
    public int outside() {
        return outside;
    }

    /** How many of them have died. */
    public int killed() {
        return killed;
    }

    /** On each monster the floor put outside the keep: its death, told to the mission it belongs to. */
    @ModuleGroup(RpgModuleGroups.ITEMS)
    public static final class Counted extends Module implements DieModule {

        private final Mission mission;

        Counted(GameObject monster, Mission mission) {
            super(monster);
            this.mission = mission;
        }

        @Override
        public void onDie() {
            mission.fell(getOwner());
        }
    }
}
