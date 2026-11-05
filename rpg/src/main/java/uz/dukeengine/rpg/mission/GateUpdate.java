package uz.dukeengine.rpg.mission;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;

/**
 * The gate of the boss's keep: it stands across the doorway, and nothing walks through it until it is opened — and
 * nothing opens it but the key, given to it. A hero walking up to it leaves it shut.
 *
 * <p>Opening swaps it for the template its block names: the gate with a shape goes, so the engine lays the navigation
 * grid again without it and the doorway is walked at once, and the same gate with no shape stands in its place,
 * turned as it was, swinging open as it appears.
 *
 * <p>Deterministic: opened by an errand an order started, on every machine on the same frame.
 */
@ModuleGroup(ModuleGroups.EFFECT)
public final class GateUpdate extends Module implements Saveable {

    /**
     * @param opens          the template that stands in its place once it is open; blank, and nothing does
     * @param withoutKeyWord what a hero sent up to it says there without the key
     * @param withKeyWord    and with the key in his bag, which is not yet the key given to it
     */
    public record Data(String opens, String withoutKeyWord, String withKeyWord) implements ModuleData {

        /** What a block leaves out. */
        static final Data DEFAULTS = new Data("", "", "");

        public Data {
            opens = opens == null ? "" : opens;
            withoutKeyWord = withoutKeyWord == null ? "" : withoutKeyWord;
            withKeyWord = withKeyWord == null ? "" : withKeyWord;
            requireSayable("WithoutKeyWord", withoutKeyWord);
            requireSayable("WithKeyWord", withKeyWord);
        }

        /**
         * What he says goes down the panel's status line as his note, which splits on ',' and '|': the rule FullWord
         * and NoUseWord are held to in {@code DungeonSettings}. The gate is an Object block, which the settings do not
         * read, so its lines are held to it here, where the block is read — and the binder says where.
         */
        private static void requireSayable(String field, String words) {
            if (words.indexOf(',') >= 0 || words.indexOf('|') >= 0) {
                throw new IllegalArgumentException(field + " may not contain ',' or '|'");
            }
        }
    }

    private final Data data;
    /** Opened already: a second opening, the same frame, would stand a second open gate in the first. */
    private boolean opened;

    public GateUpdate(GameObject owner, Data data) {
        super(owner);
        this.data = data;
    }

    /** What a hero sent up to it says when he gets there: with the key in his bag, or without it. */
    public String lineFor(boolean withTheKey) {
        return withTheKey ? data.withKeyWord() : data.withoutKeyWord();
    }

    /**
     * Whether the gate {@code gate} names still stands: it is in the world and has not been opened. Opening marks it
     * destroyed, and the engine takes it away at the start of the next frame, so from the moment it is opened it no
     * longer stands. The one answer to it, asked by the seal and by the mission alike.
     */
    public static boolean stands(World world, ObjectId gate) {
        var there = world.findObject(gate);
        return there != null && !there.isDestroyed();
    }

    /**
     * Open it: this gate goes, its shape with it, and in its place, turned as it was, stands the template
     * {@code Opens} names — the same gate swung open, in nobody's way.
     */
    public void open() {
        if (opened) {
            return;
        }
        opened = true;
        var owner = getOwner();
        var world = owner.getWorld();
        var open = world == null || data.opens().isBlank() ? null : world.findTemplate(data.opens());
        if (open != null) {
            world.spawn(open, owner.getPosition(), owner.getPlayerIndex()).setOrientation(owner.getOrientation());
        }
        owner.markDestroyed();
    }

    @Override
    public void save(SaveOut out) {
        out.writeBoolean(opened);
    }

    @Override
    public void load(SaveIn in) {
        opened = in.readBoolean();
    }
}
