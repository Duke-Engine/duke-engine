package uz.duke.rts.module;

import java.util.ArrayList;
import java.util.List;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.Module;
import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.module.ModuleGroups;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;

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
