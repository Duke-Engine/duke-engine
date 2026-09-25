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
 *
 * <p><b>A hold shared by a side</b> ({@code SharedBy}): every thing of the side whose hold names the same network —
 * the reference's tunnel network, {@code TunnelContain} — holds one list of passengers with one capacity. What gets in
 * at any of them may get out at any of them, beside the one it leaves by; the passengers live through the loss of any
 * but the last, and die with that.
 *
 * <p><b>Passengers that fire</b> ({@code PassengersFire}, and {@link #setPassengersFire} while the game runs — an
 * upgrade's bunker): each uses its own weapon, target and reload from where its carrier stands, as though it stood
 * there, the reference's Humvee and Battle Bus. <b>Riders</b> ({@code RiderBone}): passengers standing on top of the
 * carrier at that bone of its model, turning with it and firing on their own, dying with it, and clicked as the carrier
 * — an Overlord's gattling cannon.
 */
@ModuleGroup(ModuleGroups.MOVEMENT)
public final class ContainModule extends uz.dukeengine.core.module.UpdateModule
        implements uz.dukeengine.core.module.DieModule {

    /**
     * INI config: {@code Slots} (passenger capacity); {@code SharedBy}: the network whose one hold, and one capacity,
     * every one of the side's things naming it shares — {@code null} for a hold of its own; {@code PassengersFire}:
     * whether they fire from inside; {@code RiderBone}: the bone of its model they ride on top at, or null.
     */
    public record Data(int slots, String sharedBy, boolean passengersFire, String riderBone) implements ModuleData {

        /** A hold of its own. */
        public Data(int slots) {
            this(slots, null);
        }

        /** Passengers inside, and idle. */
        public Data(int slots, String sharedBy) {
            this(slots, sharedBy, false, null);
        }
    }

    private static final Coord3D UNLOAD_OFFSET = new Coord3D(0f, -5f, 0f);

    private final int slots;
    private final String sharedBy;
    private final String riderBone;
    private boolean passengersFire;
    private final List<ObjectId> passengers = new ArrayList<>();

    public ContainModule(GameObject owner, Data data) {
        super(owner);
        this.slots = data.slots();
        this.sharedBy = data.sharedBy();
        this.riderBone = data.riderBone();
        this.passengersFire = data.passengersFire();
    }

    /** Let its passengers fire from inside, or hold them idle — the reference's PassengersFireUpgrade. */
    public void setPassengersFire(boolean fire) {
        this.passengersFire = fire;
    }

    public boolean passengersFire() {
        return passengersFire;
    }

    /** The bone of its model its passengers ride on top at, or null for a hold they sit inside. */
    public String riderBone() {
        return riderBone;
    }

    /** Its riders stand where they ride, turned with it; its firing passengers where it stands. */
    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (sharedBy != null || world == null || !passengersFire && riderBone == null) {
            return;
        }
        var seat = riderBone == null ? null : uz.dukeengine.core.thing.Bones.inWorld(owner, riderBone);
        for (var id : passengers) {
            var passenger = world.findObject(id);
            if (passenger == null) {
                continue;
            }
            if (riderBone == null) {
                passenger.setPosition(owner.getPosition()); // fires as though it stood where its carrier does
                continue;
            }
            passenger.setPosition(seat != null ? seat : owner.getPosition());
            passenger.setOrientation(owner.getOrientation());
            passenger.setKeepsOwnHeight(true);
        }
    }

    /** Whether {@code passenger} fires from inside what carries it: a hold whose passengers fire, or a rider. */
    public static boolean firesFromInside(GameObject passenger) {
        var hold = holdOf(passenger);
        return hold != null && (hold.passengersFire || hold.riderBone != null);
    }

    /** The hold of its own that {@code passenger} is in — a side's shared one left out — or null. */
    public static ContainModule holdOf(GameObject passenger) {
        var world = passenger.getWorld();
        if (world == null || !passenger.isContained()) {
            return null;
        }
        for (var carrier : world.getObjects()) {
            var hold = carrier.findModule(ContainModule.class);
            if (hold != null && hold.sharedBy == null && hold.passengers.contains(passenger.getId())) {
                return hold;
            }
        }
        return null;
    }

    /** Who is in it: its own list, or its side's for the network it shares. */
    private List<ObjectId> hold() {
        return sharedBy != null && getOwner().getWorld() instanceof uz.dukeengine.rts.RtsSimulation rts
                ? rts.sharedHold(getOwner().getPlayerIndex(), sharedBy) : passengers;
    }

    public int getSlots() {
        return slots;
    }

    public int getPassengerCount() {
        return hold().size();
    }

    public boolean isFull() {
        return hold().size() >= slots;
    }

    public boolean contains(ObjectId id) {
        return hold().contains(id);
    }

    /**
     * Who rides inside, in the order they got in — one getting out leaves the others in theirs: the reference's
     * contain list, which a bar walks to show an exit button a passenger ({@code ControlBar::populateInvDataCallback}).
     */
    public List<ObjectId> getPassengers() {
        return List.copyOf(hold());
    }

    /** Load {@code passenger}; returns false if full. The passenger goes idle. */
    public boolean load(GameObject passenger) {
        if (isFull() || passenger == getOwner()) {
            return false;
        }
        hold().add(passenger.getId());
        passenger.setContained(true);
        return true;
    }

    /** Every passenger of the player's container out beside it — an {@code Evacuate} order. */
    public static boolean evacuate(uz.dukeengine.core.thing.World world,
            uz.dukeengine.rts.message.GameMessage.Evacuate order) {
        var container = world.findObject(order.container());
        var hold = container == null || container.getPlayerIndex() != order.playerIndex() ? null
                : container.findModule(ContainModule.class);
        if (hold == null || hold.hold().isEmpty()) {
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
        if (!hold().remove(passenger.getId())) {
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
        var hold = hold();
        for (var id : hold) {
            var passenger = world.findObject(id);
            if (passenger != null) {
                passenger.setContained(false);
                passenger.setPosition(dropPoint);
            }
        }
        hold.clear();
    }

    /**
     * The last of its side's network gone, its passengers go with it — the reference's tunnel network, whose
     * passengers die when no tunnel is left to come out of; any other it shares its hold with still standing, they
     * live on in that one.
     */
    @Override
    public void onDie(uz.dukeengine.core.module.Death death) {
        var world = getOwner().getWorld();
        if (riderBone != null && world != null) {
            killAll(world, passengers); // riders die with what they ride
            return;
        }
        if (sharedBy == null || !(world instanceof uz.dukeengine.rts.RtsSimulation rts)) {
            return;
        }
        int side = getOwner().getPlayerIndex();
        for (var other : world.getObjects()) {
            var hold = other.isEffectivelyDead() || other.getPlayerIndex() != side ? null
                    : other.findModule(ContainModule.class);
            if (hold != null && sharedBy.equals(hold.sharedBy)) {
                return; // one still stands: they wait in it
            }
        }
        killAll(world, rts.sharedHold(side, sharedBy));
    }

    private static void killAll(uz.dukeengine.core.thing.World world, List<ObjectId> hold) {
        for (var id : hold) {
            var passenger = world.findObject(id);
            if (passenger != null && passenger.getBody() != null) {
                passenger.getBody().setHealth(0f);
            } else if (passenger != null) {
                passenger.markDestroyed();
            }
        }
        hold.clear();
    }
}
