package uz.dukeengine.rts.module;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;

/**
 * Carries other objects inside this one, ported in spirit from SAGE's
 * {@code TransportContain}/garrison modules.
 *
 * <p>Loaded passengers are marked {@linkplain GameObject#isContained() contained}
 * — they stay in the simulation but go idle (no movement or firing) and cannot be
 * targeted — until unloaded back into the world beside the transport. Capacity is
 * fixed; loading past it fails.
 */
@ModuleGroup(ModuleGroups.MOVEMENT)
public final class ContainModule extends Module {

    /** INI config: {@code Slots} (passenger capacity). */
    public record Data(int slots) implements ModuleData {
    }

    private static final Coord3D UNLOAD_OFFSET = new Coord3D(0f, -5f, 0f);

    private final int slots;
    private final List<ObjectId> passengers = new ArrayList<>();

    public ContainModule(GameObject owner, Data data) {
        super(owner);
        this.slots = data.slots();
    }

    public int getSlots() {
        return slots;
    }

    public int getPassengerCount() {
        return passengers.size();
    }

    public boolean isFull() {
        return passengers.size() >= slots;
    }

    public boolean contains(ObjectId id) {
        return passengers.contains(id);
    }

    /** Load {@code passenger}; returns false if full. The passenger goes idle. */
    public boolean load(GameObject passenger) {
        if (isFull() || passenger == getOwner()) {
            return false;
        }
        passengers.add(passenger.getId());
        passenger.setContained(true);
        return true;
    }

    /** Every passenger of the player's container out beside it — an {@code Evacuate} order. */
    public static boolean evacuate(uz.dukeengine.core.thing.World world,
            uz.dukeengine.rts.message.GameMessage.Evacuate order) {
        var container = world.findObject(order.container());
        var hold = container == null || container.getPlayerIndex() != order.playerIndex() ? null
                : container.findModule(ContainModule.class);
        if (hold == null || hold.passengers.isEmpty()) {
            return false;
        }
        hold.unloadAll();
        return true;
    }

    /** One of the player's passengers out of whatever carries it — an {@code ExitContainer} order. */
    public static boolean exit(uz.dukeengine.core.thing.World world,
            uz.dukeengine.rts.message.GameMessage.ExitContainer order) {
        var passenger = world.findObject(order.passenger());
        if (passenger == null || passenger.getPlayerIndex() != order.playerIndex() || !passenger.isContained()) {
            return false;
        }
        for (var carrier : world.getObjects()) {
            var hold = carrier.findModule(ContainModule.class);
            if (hold != null && hold.contains(passenger.getId())) {
                hold.unload(passenger);
                return true;
            }
        }
        return false;
    }

    /** One passenger out, beside the transport. */
    public void unload(GameObject passenger) {
        if (!passengers.remove(passenger.getId())) {
            return;
        }
        passenger.setContained(false);
        passenger.setPosition(getOwner().getPosition().add(UNLOAD_OFFSET));
    }

    /** Eject every passenger back into the world beside the transport. */
    public void unloadAll() {
        var world = getOwner().getWorld();
        if (world == null) {
            return;
        }
        var dropPoint = getOwner().getPosition().add(UNLOAD_OFFSET);
        for (var id : passengers) {
            var passenger = world.findObject(id);
            if (passenger != null) {
                passenger.setContained(false);
                passenger.setPosition(dropPoint);
            }
        }
        passengers.clear();
    }
}
