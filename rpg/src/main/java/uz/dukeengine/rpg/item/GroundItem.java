package uz.dukeengine.rpg.item;

import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.save.SaveIn;
import uz.dukeengine.core.save.SaveOut;
import uz.dukeengine.core.save.Saveable;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rpg.RpgModuleGroups;

/**
 * A thing lying on the floor, waiting to be picked up.
 *
 * <p>Walking over it does nothing. A hero is sent to it — a click on it, like a click on anything else he is sent
 * at — and it changes hands when he gets there: see {@link ItemErrand}. So what lies about is a decision the player
 * makes, not one his route makes for him, and a full bag can walk past a thing and leave it for later.
 *
 * <p>What it holds was settled when it dropped, or when a hero put it down.
 */
@ModuleGroup(RpgModuleGroups.ITEMS)
public final class GroundItem extends Module implements Saveable {

    /** It reads no fields; the block only says the unit has one. */
    public record Data() implements ModuleData {
    }

    private Loot holding;

    public GroundItem(GameObject owner) {
        super(owner);
    }

    /** Say what is in it. Called once, by whatever dropped it. */
    void holds(Loot item) {
        this.holding = item;
    }

    /** What is in it, or {@code null} once it has been taken. */
    public Loot getHolding() {
        return holding;
    }

    /** Taken: empty, and gone from the floor. */
    public Loot take() {
        var item = holding;
        holding = null;
        getOwner().markDestroyed();
        return item;
    }

    /**
     * Lay {@code item} on the floor at {@code at}, as {@code template}'s thing of {@code playerIndex}'s — a monster's
     * leavings or a hero's — and say what it holds; {@code null} where the template is not one that can hold it.
     */
    public static GameObject lay(uz.dukeengine.core.thing.World world, String template, Loot item,
            uz.dukeengine.core.math.Coord3D at, int playerIndex) {
        var kind = template == null || template.isBlank() ? null : world.findTemplate(template);
        if (kind == null) {
            return null; // no such thing in the data files; nothing is left lying
        }
        var thing = world.spawn(kind, at, playerIndex);
        var lying = thing.findModule(GroundItem.class);
        if (lying == null) {
            thing.markDestroyed(); // the template exists but is not something to pick up
            return null;
        }
        lying.holds(item);
        return thing;
    }

    @Override
    public void save(SaveOut out) {
        out.writeBoolean(holding != null);
        if (holding != null) {
            holding.save(out);
        }
    }

    @Override
    public void load(SaveIn in) {
        holding = in.readBoolean() ? Loot.read(in) : null;
    }
}
