package uz.dukeengine.rts.module;

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
 * carries. A harvester with legs walks it; one with no {@link MoveUpdate} does the same loop standing still,
 * and a side with no depot at all banks where it stands. Both of those are what the whole module used to do,
 * so a game that had neither keeps working exactly as it did.
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
public final class HarvestUpdate extends UpdateModule {

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

    private Doing doing = Doing.FETCHING;
    /** The pile it walked to, which a unit-at-a-time load is handed from until it is done. */
    private GameObject atPile;
    /** Whether it has been sent somewhere and not yet been found standing still again. */
    private boolean sent;
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
        if (world == null || owner.isEffectivelyDead()) {
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
        }
    }

    private void fetch(GameObject owner, World world) {
        var pile = world.findClosest(owner.getPosition(), searchRange, candidate -> {
            var supply = candidate.findModule(SupplyModule.class);
            return supply != null && supply.getRemaining() > 0;
        });
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
        if (framesPerUnit > 0) {
            loadAUnitAtATime();
            return;
        }
        var pile = world.findClosest(owner.getPosition(), searchRange, candidate -> {
            var supply = candidate.findModule(SupplyModule.class);
            return supply != null && supply.getRemaining() > 0;
        });
        if (pile == null) {
            doing = Doing.FETCHING; // it emptied: go and look for another
            return;
        }
        elapsed++;
        if (elapsed < framesPerTrip) {
            return;
        }
        elapsed = 0;
        carrying = pile.findModule(SupplyModule.class).take(loadPerTrip);
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
        var depot = world.findClosest(owner.getPosition(), Float.MAX_VALUE,
                candidate -> candidate.findModule(SupplyDepot.class) != null
                        && candidate.getPlayerIndex() == owner.getPlayerIndex());
        if (depot != null && !walkTo(owner, depot)) {
            elapsed = 0; // still on its way: the wait at the depot starts when it is there
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
     */
    private boolean walkTo(GameObject owner, GameObject there) {
        var legs = owner.findModule(MoveUpdate.class);
        if (legs == null) {
            return true;
        }
        if (owner.getWorld().isBeside(owner, there)) {
            if (legs.isMoving()) {
                legs.stop();
            }
            sent = false;
            return true;
        }
        if (legs.isMoving()) {
            return false;
        }
        if (sent) {
            sent = false;
            return true; // it set off and has stopped: this is as near as it gets
        }
        legs.moveTo(owner.getWorld().standingNextTo(owner, there));
        sent = true;
        return false;
    }

    /** What it is carrying and has not banked yet, for a client that draws a full harvester differently. */
    public int getCarrying() {
        return carrying;
    }
}
