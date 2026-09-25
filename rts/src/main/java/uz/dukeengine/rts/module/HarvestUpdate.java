package uz.dukeengine.rts.module;

import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.module.ModuleData;
import uz.dukeengine.core.module.ModuleGroup;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.rts.player.RtsPlayer;

/**
 * Gathers from a {@link SupplyModule} pile and carries it to a {@link SupplyDepot}, ported in spirit from
 * SAGE's harvester {@code SupplyTruckAIUpdate}.
 *
 * <p>The loop is: walk to the nearest pile that still has something in it, stand there for
 * {@code framesPerTrip} frames while it loads, walk to the nearest depot of its own side, and bank what it
 * carries. A harvester moves by whatever moves it — walking with a {@link MoveUpdate}, flying with a {@code
 * FlyUpdate}; one that cannot move does the same loop standing still, and a side with no depot at all banks where
 * it stands. Both of those are what the whole module used to do, so a game that had neither keeps working exactly
 * as it did.
 *
 * <p><b>Told where to work</b> ({@link #workAt}) — the reference's preferred dock, a warehouse or a supply centre the
 * player clicked — it works there until told another place or it is gone, however far it is: one place, the last named.
 * <b>Loaded and paid only beside:</b> it takes a load only while it stands beside its pile, and banks it only beside the
 * depot it carries it to, as the reference's truck is loaded and paid only docked; a trip cut short keeps its load, and
 * the harvester sets off again a second later. <b>Ordered elsewhere</b> — any order its player gives it, a move, an
 * attack, a stop, a guard, a build order — it stops working and keeps what it carries, until it is next told where to
 * work, as the reference's truck goes idle.
 *
 * <p>The walking is the point. Without it there was no distance to a pile and so no reason to put one
 * anywhere, no reason to defend a depot, and no cost to mining the far side of the map — which is most of
 * what an RTS economy is about.
 *
 * <p><b>Standing at either end.</b> A real RTS truck does not bank the moment it reaches the depot: it stands
 * there for a time of its own, and only then does the money arrive; and at the pile it is handed its load a
 * box at a time. The reference game docks a truck, waits its delay, and asks the dock to act, over and over
 * until the dock says it is done — the pile hands over one box an act and is done on the act that finds the
 * truck full, the depot takes every box in its one act ({@code SupplyWarehouseDockUpdate::action},
 * {@code SupplyCenterDockUpdate::action}). A truck of four boxes with 1.0 s at the pile and 0.4 s at the
 * depot therefore stands 5.0 s at the pile and 0.4 s at the depot, and its money arrives at the end of the
 * 0.4. {@code framesAtDepot} and {@code framesPerUnit} are those two waits; left out, both are nothing, and
 * the harvester loads and banks exactly as it did before they existed.
 *
 * @param loadPerTrip   how much it carries in one go
 * @param framesPerTrip how long loading takes, once it is standing at the pile — unless it loads a unit at a
 *                      time, below
 * @param searchRange   how far it will look for a pile; 0 is anywhere on the map
 * @param framesAtDepot how long it stands at the depot before what it carries is counted, the frame it
 *                      arrives in the first of them, as at the pile. 0 counts it on arrival, as always
 * @param framesPerUnit when above 0, it is loaded a unit at a time: it waits this long, is handed one unit,
 *                      and again, until an act finds it full — one more wait than it has units, as in the
 *                      reference — or finds the pile empty, which ends the wait early and sends it home with
 *                      what it has. {@code framesPerTrip} is then not read. 0 loads the whole trip at once
 * @param unitOfLoad    how much one of those acts hands over; 0 is one. A game whose piles hold money and
 *                      whose boxes are worth 75 of it says 75 here, and 300 as the load of four
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class HarvestUpdate extends UpdateModule implements OrderListener {

    /** Where it is in the loop. */
    private enum Doing {
        /** Walking to a pile. */
        FETCHING,
        /** Standing at one, filling up. */
        LOADING,
        /** Walking a load back to a depot. */
        RETURNING
    }

    /**
     * {@code LoadPerTrip}, {@code FramesPerTrip}, {@code SearchRange}, {@code FramesAtDepot},
     * {@code FramesPerUnit}, {@code UnitOfLoad}.
     */
    public record Data(int loadPerTrip, int framesPerTrip, float searchRange, int framesAtDepot, int framesPerUnit,
            int unitOfLoad) implements ModuleData {

        /** A harvester that loads a trip at once and banks the moment it arrives, as every one did before. */
        public Data(int loadPerTrip, int framesPerTrip, float searchRange) {
            this(loadPerTrip, framesPerTrip, searchRange, 0, 0, 0);
        }
    }

    private final int loadPerTrip;
    private final int framesPerTrip;
    private final float searchRange;
    private final int framesAtDepot;
    private final int framesPerUnit;
    private final int unitOfLoad;

    /** The reference's retry after a trip that did not end beside its depot: a second, not every frame's search. */
    private static final int RETRY_FRAMES = GameConstants.LOGICFRAMES_PER_SECOND;

    private Doing doing = Doing.FETCHING;
    /** The one place it was told to work — a pile to fetch from or a depot of its side — or null for the nearest. */
    private GameObject place;
    /** Whether its player has ordered it elsewhere since it was last told where to work. */
    private boolean paused;
    /** Frames before a harvester whose trip ended short sets off for its depot again. */
    private int retryIn;
    /** The pile it walked to, which a unit-at-a-time load is handed from until it is done. */
    private GameObject atPile;
    /** Whether it has been sent somewhere and not yet been found standing still again. */
    private boolean sent;
    /** What it has come as near to as it gets in this phase; it stays arrived there until the phase is over. */
    private GameObject reached;
    private int elapsed;
    private int carrying;

    public HarvestUpdate(GameObject owner, Data data) {
        super(owner);
        this.loadPerTrip = data.loadPerTrip();
        this.framesPerTrip = Math.max(1, data.framesPerTrip());
        this.searchRange = data.searchRange() <= 0f ? Float.MAX_VALUE : data.searchRange();
        this.framesAtDepot = Math.max(0, data.framesAtDepot());
        this.framesPerUnit = Math.max(0, data.framesPerUnit());
        this.unitOfLoad = data.unitOfLoad() <= 0 ? 1 : data.unitOfLoad();
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null || owner.isEffectivelyDead() || paused) {
            return;
        }
        // Each phase that finishes hands straight on to the next in the same frame, so a harvester with
        // nowhere to walk keeps exactly the timing it had before there was any walking: FramesPerTrip is
        // the loading, not the loading plus a frame for each corner of the loop.
        for (int step = 0; step < Doing.values().length; step++) {
            var was = doing;
            switch (doing) {
                case FETCHING -> fetch(owner, world);
                case LOADING -> load(owner, world);
                case RETURNING -> carry(owner, world);
            }
            if (doing == was) {
                return;
            }
            reached = null; // a new phase is a new errand
        }
    }

    /**
     * Work at {@code place} from now on — a pile to fetch from, or a depot of its side to deliver to, the game's to
     * name — until told another or it is gone: after every delivery it goes back to it, however far it is. One place,
     * the last named: a depot replaces a pile told before it, and a pile a depot. It works again if its player had
     * ordered it elsewhere. Sent to a pile with a part of a load, it fills up there first; sent to its depot with any
     * load at all, it delivers it at once.
     */
    public void workAt(GameObject place) {
        if (place == null) {
            return;
        }
        boolean isPile = place.findModule(SupplyModule.class) != null;
        boolean isDepot = place.findModule(SupplyDepot.class) != null
                && place.getPlayerIndex() == getOwner().getPlayerIndex();
        if (!isPile && !isDepot) {
            return;
        }
        this.place = place;
        paused = false;
        atPile = null;
        elapsed = 0;
        retryIn = 0;
        reached = null;
        sent = false;
        doing = isPile ? (carrying < loadPerTrip ? Doing.FETCHING : Doing.RETURNING)
                : (carrying > 0 ? Doing.RETURNING : Doing.FETCHING);
    }

    /**
     * Its player gave it an order: it stops working where it is, keeping what it carries, until it is next told where
     * to work ({@link #workAt}) — the reference's truck, gone idle.
     */
    @Override
    public void onOrder(uz.dukeengine.rts.message.GameMessage order) {
        paused = true;
    }

    /** Whether it has stopped working for an order of its player's, until it is next told where to work. */
    public boolean isPaused() {
        return paused;
    }

    /** At work on its loop, it is not asked to step aside, nor moved off the ground it loads on. */
    @Override
    public boolean keepsBusy() {
        return !paused;
    }

    /** The place it was told to work, while it stands; else null. */
    private GameObject toldPlace() {
        if (place != null && place.isDestroyed()) {
            place = null; // gone: back to the nearest
        }
        return place;
    }

    /** The pile it fetches from: the one it was told, while it stands and has anything left; else the nearest. */
    private GameObject pileFor(GameObject owner, World world) {
        var told = toldPlace() == null ? null : place.findModule(SupplyModule.class);
        if (told != null && told.getRemaining() > 0) {
            return place;
        }
        return world.findClosest(owner.getPosition(), searchRange, candidate -> {
            var supply = candidate.findModule(SupplyModule.class);
            return supply != null && supply.getRemaining() > 0;
        });
    }

    /** The depot it delivers to: the one it was told, while it stands and is its side's; else the nearest. */
    private GameObject depotFor(GameObject owner, World world) {
        var told = toldPlace();
        if (told != null && told.findModule(SupplyDepot.class) != null
                && told.getPlayerIndex() == owner.getPlayerIndex()) {
            return told;
        }
        return world.findClosest(owner.getPosition(), Float.MAX_VALUE,
                candidate -> candidate.findModule(SupplyDepot.class) != null
                        && candidate.getPlayerIndex() == owner.getPlayerIndex());
    }

    private void fetch(GameObject owner, World world) {
        if (retryIn > 0) {
            retryIn--;
            return; // its load was cut short away from the pile: a second before it sets off again
        }
        var pile = pileFor(owner, world);
        if (pile == null) {
            return; // nothing left within reach — idle
        }
        if (walkTo(owner, pile)) {
            doing = Doing.LOADING;
            elapsed = 0;
            atPile = pile;
        }
    }

    private void load(GameObject owner, World world) {
        if (owner.getLocomotor() != null && (atPile == null || !world.isBeside(owner, atPile))) {
            atPile = null; // moved off it: nothing more is taken until it is beside a pile again
            elapsed = 0;
            retryIn = RETRY_FRAMES;
            doing = Doing.FETCHING;
            return;
        }
        if (framesPerUnit > 0) {
            loadAUnitAtATime();
            return;
        }
        var pile = atPile != null && !atPile.isDestroyed()
                && atPile.findModule(SupplyModule.class).getRemaining() > 0 ? atPile : null;
        if (pile == null) {
            doing = Doing.FETCHING; // it emptied: go and look for another
            return;
        }
        elapsed++;
        if (elapsed < framesPerTrip) {
            return;
        }
        elapsed = 0;
        carrying += pile.findModule(SupplyModule.class).take(loadPerTrip - carrying);
        doing = carrying > 0 ? Doing.RETURNING : Doing.FETCHING;
    }

    /**
     * One act of the pile's every {@code framesPerUnit}: a unit handed over, or — on the act that finds it
     * full or finds the pile empty — the end of the loading. Handed from the pile it walked to, not from
     * whichever is nearest now, so a pile that runs dry part-way ends the wait rather than being swapped for
     * another across the map.
     */
    private void loadAUnitAtATime() {
        if (++elapsed < framesPerUnit) {
            return;
        }
        elapsed = 0;
        var supply = atPile == null || atPile.isDestroyed() ? null : atPile.findModule(SupplyModule.class);
        int taken = supply == null || carrying >= loadPerTrip
                ? 0 : supply.take(Math.min(unitOfLoad, loadPerTrip - carrying));
        if (taken > 0) {
            carrying += taken;
            return;
        }
        atPile = null;
        doing = carrying > 0 ? Doing.RETURNING : Doing.FETCHING;
    }

    private void carry(GameObject owner, World world) {
        var depot = depotFor(owner, world);
        if (retryIn > 0) {
            retryIn--;
            return; // its last trip ended short: a second before it sets off again
        }
        if (depot != null && !walkTo(owner, depot)) {
            elapsed = 0; // still on its way: the wait at the depot starts when it is there
            return;
        }
        if (depot != null && owner.getLocomotor() != null && !world.isBeside(owner, depot)) {
            // Its trip ended somewhere else — sent away, or a way that ends short: it keeps what it carries.
            reached = null;
            sent = false;
            elapsed = 0;
            retryIn = RETRY_FRAMES;
            return;
        }
        if (depot != null && ++elapsed < framesAtDepot) {
            return; // standing at it while it unloads
        }
        elapsed = 0;
        var player = RtsPlayer.of(world, owner.getPlayerIndex());
        if (player != null) {
            player.deposit(carrying);
        }
        carrying = 0;
        doing = Doing.FETCHING;
    }

    /**
     * Sends it at {@code there} and says whether it has arrived.
     *
     * <p>"Arrived" is <em>touching it, or as close as it is going to get</em>. A pile and a depot are solid,
     * so a harvester sent at one walks up to its edge and stops against it — waiting for the two to actually
     * touch is waiting for something that never happens, which is what left the worker circling. A harvester
     * with no legs at all is always there, which is how this module worked before it could walk.
     *
     * <p><b>Once there, it stays there</b> for the rest of the phase. Asked again the frame after it had
     * stopped as near as it got, it used to find itself neither beside the depot nor moving nor sent, and was
     * sent again — a mover with nowhere nearer to go that does not move — so it flickered between arrived and
     * on its way every other frame, the wait at the depot was started again each time, and the load it carried
     * was never banked.
     */
    private boolean walkTo(GameObject owner, GameObject there) {
        var legs = owner.getLocomotor();
        if (legs == null || there == reached) {
            return true;
        }
        if (owner.getWorld().isBeside(owner, there)) {
            if (legs.isMoving()) {
                legs.stop();
            }
            return arrived(there);
        }
        if (legs.isMoving()) {
            return false;
        }
        if (sent) {
            return arrived(there); // it set off and has stopped: this is as near as it gets
        }
        legs.moveExactlyTo(owner.getWorld().standingNextTo(owner, there)); // docking: no place of its own there
        sent = true;
        return false;
    }

    private boolean arrived(GameObject there) {
        sent = false;
        reached = there;
        return true;
    }

    /** What it is carrying and has not banked yet, for a client that draws a full harvester differently. */
    public int getCarrying() {
        return carrying;
    }
}
