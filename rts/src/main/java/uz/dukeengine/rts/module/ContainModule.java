package uz.dukeengine.rts.module;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.ModuleGroups;
import uz.dukeengine.core.thing.Classified;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Kind;
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
 * — an Overlord's gattling cannon. No order lets a rider off, neither an evacuate nor an exit: only its carrier's
 * death, or the game's own {@link #unload}, as the reference's Overlord keeps its add-on through an evacuate. A hold
 * may ride only some kinds ({@code RiderKinds}): its other passengers sit inside as any hold's do — the reference's
 * Helix, soldiers inside and its one add-on on top ({@code HelixContain}).
 *
 * <p><b>Out of a building</b>, a passenger stands on the nearest clear ground outside its footprint, or at the exit bone
 * the holder names ({@code ExitBone}), not inside it; or, where the holder names an exit path ({@code ExitStart} and
 * {@code ExitEnd}), it is put at the start and walks to free ground of its own nearest the end, then on to the
 * holder's rally point where it has one — the reference's {@code OpenContain::exitObjectViaDoor}. <b>A shared hold's
 * building sold or removed</b> leaves the network that frame; its passengers come out only where it was the last.
 * <b>A holder that loses its passengers</b> in its death may take them out of the world quietly ({@code
 * PassengersVanish}): no death, no death effect, no kill for anyone. <b>Its passengers see nothing</b> but where it
 * says they see out ({@code PassengersSeeOut}), a garrison's, and then from where it stands. <b>A holder taken out of
 * the world</b> without a death takes its passengers with it, taken away too and not killed, the same frame — the
 * reference's {@code OpenContain::onDelete}; a shared hold's stay while another of the network stands, and go with the
 * last, as the reference's tunnels cave in ({@code TunnelTracker::onTunnelDestroyed}). <b>A hold that shows its
 * passengers</b> ({@code ShowsPassengers}) has them drawn where the game stands them, turned its way, and clicked as
 * the hold — the reference's fire base, a soldier in each trench ({@code IsEnclosingContainer = No}); it moves none
 * of them itself. <b>Riders on its turret</b> ({@code RiderTurret}): their bone turned about the turret's as the
 * carrier's {@link Turret} has it turned, each facing the turret's way — the reference's heavy tank, its battle bunker
 * 15 behind the turret's pivot ({@code PassengersInTurret}, {@code Object::getSingleLogicalBonePositionOnTurret}).
 */
@ModuleGroup(ModuleGroups.MOVEMENT)
public final class ContainModule extends uz.dukeengine.core.module.UpdateModule
        implements uz.dukeengine.core.module.DieModule {

    /**
     * INI config: {@code Slots} (passenger capacity); {@code SharedBy}: the network whose one hold, and one capacity,
     * every one of the side's things naming it shares — {@code null} for a hold of its own; {@code PassengersFire}:
     * whether they fire from inside; {@code RiderBone}: the bone of its model they ride on top at, or null; {@code
     * PassengersVanish}: whether passengers it loses in its death leave the world quietly rather than dying; {@code
     * ExitBone}: the bone of its model its passengers come out at, or null for the nearest clear ground outside it;
     * {@code ExitStart} and {@code ExitEnd}: the bones of its exit path, which they walk out along instead; {@code
     * PassengersSeeOut}: whether they see out of it, from where it stands; {@code RiderKinds}: the kinds of passenger
     * that ride at its RiderBone, the others sitting inside — none named, every passenger rides; {@code
     * ShowsPassengers}: whether they are drawn where the game stands them; {@code RiderTurret}: the node of its model
     * its RiderBone turns with as its turret turns, or null for riders on its body.
     */
    public record Data(int slots, String sharedBy, boolean passengersFire, String riderBone, boolean passengersVanish,
            String exitBone, String exitStart, String exitEnd, boolean passengersSeeOut, List<Kind> riderKinds,
            boolean showsPassengers, String riderTurret) implements ModuleData {

        public Data {
            riderKinds = riderKinds == null ? List.of() : List.copyOf(riderKinds);
        }

        /** Riders on its body. */
        public Data(int slots, String sharedBy, boolean passengersFire, String riderBone, boolean passengersVanish,
                String exitBone, String exitStart, String exitEnd, boolean passengersSeeOut, List<Kind> riderKinds,
                boolean showsPassengers) {
            this(slots, sharedBy, passengersFire, riderBone, passengersVanish, exitBone, exitStart, exitEnd,
                    passengersSeeOut, riderKinds, showsPassengers, null);
        }

        /** Passengers inside that nobody sees. */
        public Data(int slots, String sharedBy, boolean passengersFire, String riderBone, boolean passengersVanish,
                String exitBone, String exitStart, String exitEnd, boolean passengersSeeOut, List<Kind> riderKinds) {
            this(slots, sharedBy, passengersFire, riderBone, passengersVanish, exitBone, exitStart, exitEnd,
                    passengersSeeOut, riderKinds, false);
        }

        /** Every passenger riding, where it names a RiderBone. */
        public Data(int slots, String sharedBy, boolean passengersFire, String riderBone, boolean passengersVanish,
                String exitBone, String exitStart, String exitEnd, boolean passengersSeeOut) {
            this(slots, sharedBy, passengersFire, riderBone, passengersVanish, exitBone, exitStart, exitEnd,
                    passengersSeeOut, List.of());
        }

        /** Passengers that see nothing from inside, as the reference's transport's do. */
        public Data(int slots, String sharedBy, boolean passengersFire, String riderBone, boolean passengersVanish,
                String exitBone, String exitStart, String exitEnd) {
            this(slots, sharedBy, passengersFire, riderBone, passengersVanish, exitBone, exitStart, exitEnd, false);
        }

        /** Passengers that come out at one bone, or beside it, as they did before a hold could name a path. */
        public Data(int slots, String sharedBy, boolean passengersFire, String riderBone, boolean passengersVanish,
                String exitBone) {
            this(slots, sharedBy, passengersFire, riderBone, passengersVanish, exitBone, null, null);
        }

        /** Passengers that die with it, and come out beside it. */
        public Data(int slots, String sharedBy, boolean passengersFire, String riderBone) {
            this(slots, sharedBy, passengersFire, riderBone, false, null);
        }

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
    /** How far clear of its holder's outline a passenger comes out. */
    private static final float CLEARANCE = 1f;
    /** The ways round a holder its passengers' exits are tried in, from its front. */
    private static final int WAYS = 16;

    private final int slots;
    private final String sharedBy;
    private final String riderBone;
    private final boolean passengersVanish;
    private final String exitBone;
    private final String exitStart;
    private final String exitEnd;
    private final boolean passengersSeeOut;
    private final List<Kind> riderKinds;
    private final boolean showsPassengers;
    private final String riderTurret;
    private boolean passengersFire;
    private final List<ObjectId> passengers = new ArrayList<>();

    public ContainModule(GameObject owner, Data data) {
        super(owner);
        this.slots = data.slots();
        this.sharedBy = data.sharedBy();
        this.riderBone = data.riderBone();
        this.passengersFire = data.passengersFire();
        this.passengersVanish = data.passengersVanish();
        this.exitBone = data.exitBone();
        this.exitStart = data.exitStart();
        this.exitEnd = data.exitEnd();
        this.passengersSeeOut = data.passengersSeeOut();
        this.riderKinds = data.riderKinds();
        this.showsPassengers = data.showsPassengers();
        this.riderTurret = data.riderTurret();
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

    /** Whether its passengers are drawn where the game stands them, and clicked as it. */
    public boolean showsPassengers() {
        return showsPassengers;
    }

    /** Whether {@code passenger} rides on top at its RiderBone, rather than sitting inside. */
    public boolean rides(GameObject passenger) {
        if (riderBone == null) {
            return false;
        }
        var kinds = Classified.of(passenger.getTemplate());
        return riderKinds.isEmpty() || riderKinds.stream().anyMatch(kinds::contains);
    }

    /** Its riders stand where they ride, turned with it; its firing passengers where it stands. */
    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (sharedBy != null || world == null || !passengersFire && riderBone == null && !passengersSeeOut) {
            return;
        }
        Coord3D seat = null;
        float facing = owner.getOrientation();
        if (riderBone != null && riderTurret == null) {
            seat = uz.dukeengine.core.thing.Bones.inWorld(owner, riderBone);
        } else if (riderBone != null && owner.getTemplate() instanceof uz.dukeengine.core.thing.Drawn drawn) {
            float turn = turretTurn(owner);
            seat = uz.dukeengine.core.thing.Bones.inWorld(owner, drawn.model(), riderBone, null, riderTurret, turn);
            var pointing = uz.dukeengine.core.thing.Bones.pointingInWorld(owner, drawn.model(), riderBone, null,
                    riderTurret, turn);
            facing = pointing != null ? pointing.turn() : owner.getOrientation() + turn;
        }
        for (var id : passengers) {
            var passenger = world.findObject(id);
            if (passenger == null) {
                continue;
            }
            if (!rides(passenger)) {
                if (!showsPassengers) {
                    passenger.setPosition(owner.getPosition()); // fires as though it stood where its carrier does
                }
                continue;
            }
            passenger.setPosition(seat != null ? seat : owner.getPosition());
            passenger.setOrientation(facing);
            passenger.setKeepsOwnHeight(true);
        }
    }

    /** How far {@code owner}'s turret stands turned, as a {@link Turret} of its says; none where it has none. */
    private static float turretTurn(GameObject owner) {
        for (var module : owner.getModules()) {
            if (module instanceof Turret turret) {
                return turret.turretTurn();
            }
        }
        return 0f;
    }

    /** Whether {@code passenger} fires from inside what carries it: a hold whose passengers fire, or a rider. */
    public static boolean firesFromInside(GameObject passenger) {
        var hold = holdOf(passenger);
        return hold != null && (hold.passengersFire || hold.rides(passenger));
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

    /** Load {@code passenger}; returns false if full, or if it is being sold. The passenger goes idle. */
    public boolean load(GameObject passenger) {
        if (isFull() || passenger == getOwner() || !standing(getOwner())) {
            return false;
        }
        hold().add(passenger.getId());
        passenger.setContained(true);
        passenger.setSeesOut(passengersSeeOut && sharedBy == null);
        if (passengersSeeOut && sharedBy == null) {
            passenger.setPosition(getOwner().getPosition()); // it looks from where it is held, wherever it got in
        }
        return true;
    }

    /** Every passenger of the player's container out beside it — an {@code Evacuate} order; its riders stay on. */
    public static boolean evacuate(uz.dukeengine.core.thing.World world,
            uz.dukeengine.rts.message.GameMessage.Evacuate order) {
        var container = world.findObject(order.container());
        var hold = container == null || container.getPlayerIndex() != order.playerIndex() ? null
                : container.findModule(ContainModule.class);
        return hold != null && hold.letOutTheInside();
    }

    /** Those inside out beside it, each on ground the one before left clear; its riders stay. Whether any came. */
    private boolean letOutTheInside() {
        var world = getOwner().getWorld();
        var hold = hold();
        boolean any = false;
        for (var id : List.copyOf(hold)) {
            var passenger = world.findObject(id);
            if (passenger != null && rides(passenger)) {
                continue;
            }
            hold.remove(id);
            if (passenger != null) {
                letOut(passenger);
                any = true;
            }
        }
        return any;
    }

    /** One of the player's passengers out of whatever carries it — an {@code ExitContainer} order; not a rider. */
    public static boolean exit(uz.dukeengine.core.thing.World world,
            uz.dukeengine.rts.message.GameMessage.ExitContainer order) {
        var passenger = world.findObject(order.passenger());
        if (passenger == null || passenger.getPlayerIndex() != order.playerIndex() || !passenger.isContained()) {
            return false;
        }
        for (var carrier : world.getObjects()) {
            var hold = carrier.findModule(ContainModule.class);
            if (hold != null && standing(carrier) && hold.contains(passenger.getId())) {
                if (hold.rides(passenger)) {
                    return false;
                }
                hold.unload(passenger);
                return true;
            }
        }
        return false;
    }

    /** Whether {@code holder} is still a holder: not dead, not gone, and not being sold. */
    private static boolean standing(GameObject holder) {
        return !holder.isEffectivelyDead() && !holder.isDestroyed()
                && !holder.hasStatus(uz.dukeengine.core.thing.ObjectStatus.SOLD);
    }

    /** Whether another of its side's things still holds the network it shares. */
    private boolean anotherHoldsTheNetwork() {
        var world = getOwner().getWorld();
        int side = getOwner().getPlayerIndex();
        for (var other : world.getObjects()) {
            var hold = other == getOwner() || other.getPlayerIndex() != side || !standing(other) ? null
                    : other.findModule(ContainModule.class);
            if (hold != null && sharedBy.equals(hold.sharedBy)) {
                return true;
            }
        }
        return false;
    }

    /**
     * It is being sold — the reference's sold tunnel leaving its network at once: a hold of its own puts everyone out
     * beside it; a shared one lets them out only where it was the last of the network, and otherwise they stay in the
     * network, to come out at another.
     */
    public void sold() {
        if (sharedBy == null || getOwner().getWorld() == null || !anotherHoldsTheNetwork()) {
            unloadAll();
        }
    }

    /** One passenger out, beside it — see {@link #exitFor} — or along its exit path. */
    public void unload(GameObject passenger) {
        if (!hold().remove(passenger.getId())) {
            return;
        }
        letOut(passenger);
    }

    private void letOut(GameObject passenger) {
        passenger.setSeesOut(false);
        if (outAlongThePath(passenger)) {
            return;
        }
        passenger.setPosition(exitFor(passenger));
        passenger.setContained(false);
    }

    /**
     * Out along its exit path, where it names one and its model has both bones: put on the ground at the start, walking
     * straight to the end — through its own walls, which no route leads out of — then to free ground of its own nearest
     * the end, or on to its rally point where it has one. Whether it went so.
     */
    private boolean outAlongThePath(GameObject passenger) {
        var owner = getOwner();
        var world = owner.getWorld();
        var start = exitStart == null || world == null ? null
                : uz.dukeengine.core.thing.Bones.inWorld(owner, exitStart);
        var end = exitEnd == null || start == null ? null : uz.dukeengine.core.thing.Bones.inWorld(owner, exitEnd);
        if (end == null) {
            return false;
        }
        passenger.setPosition(new Coord3D(start.x(), start.y(), world.groundHeight(start)));
        passenger.setContained(false);
        var legs = passenger.getLocomotor();
        if (legs != null) {
            var production = owner.findModule(ProductionUpdate.class);
            var rally = production == null ? null : production.getRallyPoint();
            legs.leave(end, rally != null ? rally : end);
        }
        return true;
    }

    /** Every passenger back into the world beside it, each on ground the one before left clear. */
    public void unloadAll() {
        var world = getOwner().getWorld();
        if (world == null) {
            return;
        }
        var hold = hold();
        for (var id : List.copyOf(hold)) {
            var passenger = world.findObject(id);
            if (passenger != null) {
                letOut(passenger);
            }
        }
        hold.clear();
    }

    /**
     * Where {@code passenger} comes out: at its holder's exit bone, where it names one; else on the nearest clear
     * ground outside the holder's footprint, tried round it a sixteenth of a turn at a time from its front — the
     * nearest clear way wins, and of two as near the earlier. Where no way round is clear, the nearest outside it.
     */
    Coord3D exitFor(GameObject passenger) {
        var owner = getOwner();
        var world = owner.getWorld();
        if (exitBone != null) {
            var bone = uz.dukeengine.core.thing.Bones.inWorld(owner, exitBone);
            if (bone != null) {
                return bone;
            }
        }
        var outline = uz.dukeengine.core.thing.Footprint.of(owner);
        if (world == null || outline.shape().isPoint()) {
            return owner.getPosition().add(UNLOAD_OFFSET);
        }
        Coord3D nearest = null;
        Coord3D nearestClear = null;
        float nearestAway = Float.MAX_VALUE;
        float nearestClearAway = Float.MAX_VALUE;
        for (int way = 0; way < WAYS; way++) {
            float turn = owner.getOrientation() + way * (float) (2 * Math.PI / WAYS);
            var at = outside(outline, passenger, (float) StrictMath.cos(turn), (float) StrictMath.sin(turn));
            float away = at.distance(owner.getPosition());
            if (away < nearestAway) {
                nearest = at;
                nearestAway = away;
            }
            if (away < nearestClearAway && world.findBlocker(passenger, at) == null && !world.isGroundBlocked(at)) {
                nearestClear = at;
                nearestClearAway = away;
            }
        }
        return nearestClear != null ? nearestClear : nearest;
    }

    /** The first point along a way out from the holder's middle where the passenger stands clear of its outline. */
    private static Coord3D outside(uz.dukeengine.core.thing.Footprint outline, GameObject passenger, float dx,
            float dy) {
        var middle = outline.center();
        float away = 0f;
        for (int step = 0; step < 4096; step++) {
            var at = new Coord3D(middle.x() + dx * away, middle.y() + dy * away, middle.z());
            float gap = uz.dukeengine.core.thing.Footprint.of(passenger, at).separation(outline);
            if (gap >= CLEARANCE) {
                return at;
            }
            away += Math.max(0.25f, CLEARANCE - gap); // out by what is missing: exact along a side, a few steps at a corner
        }
        return new Coord3D(middle.x() + dx * away, middle.y() + dy * away, middle.z());
    }

    /**
     * The last of its side's network gone, its passengers go with it — the reference's tunnel network, whose
     * passengers die when no tunnel is left to come out of; any other it shares its hold with still standing, they
     * live on in that one. Taken out of the world rather than killed, it takes its passengers away with it.
     */
    @Override
    public void onDie(uz.dukeengine.core.module.Death death) {
        var world = getOwner().getWorld();
        if (sharedBy == null && world != null && !getOwner().isEffectivelyDead()) {
            takeAway(world, passengers);
            return;
        }
        if (riderBone != null && world != null) {
            var riders = new ArrayList<ObjectId>();
            for (var id : passengers) {
                var passenger = world.findObject(id);
                if (passenger != null && rides(passenger)) {
                    riders.add(id);
                }
            }
            passengers.removeAll(riders);
            lose(world, riders); // riders die with what they ride
            return;
        }
        if (sharedBy == null || !(world instanceof uz.dukeengine.rts.RtsSimulation rts) || anotherHoldsTheNetwork()) {
            return; // one still stands: they wait in it
        }
        var hold = rts.sharedHold(getOwner().getPlayerIndex(), sharedBy);
        if (getOwner().isEffectivelyDead()) {
            lose(world, hold);
        } else {
            takeAway(world, hold); // the last taken away, not killed: the tunnels cave in on them
        }
    }

    /** Its passengers taken out of the world with it, and what they hold with them — none of them killed. */
    private static void takeAway(uz.dukeengine.core.thing.World world, List<ObjectId> hold) {
        for (var id : hold) {
            var passenger = world.findObject(id);
            if (passenger != null) {
                passenger.markDestroyed();
            }
        }
        hold.clear();
    }

    /** Its passengers lost with it: dead, or — where it says so — gone from the world without a death. */
    private void lose(uz.dukeengine.core.thing.World world, List<ObjectId> hold) {
        for (var id : hold) {
            var passenger = world.findObject(id);
            if (passenger == null) {
                continue;
            }
            if (passengersVanish) {
                passenger.vanish();
            } else if (passenger.getBody() != null) {
                passenger.getBody().setHealth(0f);
            } else {
                passenger.markDestroyed();
            }
        }
        hold.clear();
    }
}
