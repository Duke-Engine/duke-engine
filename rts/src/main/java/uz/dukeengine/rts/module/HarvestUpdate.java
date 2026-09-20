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
 * @param loadPerTrip  how much it carries in one go
 * @param framesPerTrip how long loading takes, once it is standing at the pile
 * @param searchRange  how far it will look for a pile; 0 is anywhere on the map
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

    /** {@code LoadPerTrip}, {@code FramesPerTrip}, {@code SearchRange}. */
    public record Data(int loadPerTrip, int framesPerTrip, float searchRange) implements ModuleData {
    }

    private final int loadPerTrip;
    private final int framesPerTrip;
    private final float searchRange;

    private Doing doing = Doing.FETCHING;
    /** Whether it has been sent somewhere and not yet been found standing still again. */
    private boolean sent;
    private int elapsed;
    private int carrying;

    public HarvestUpdate(GameObject owner, Data data) {
        super(owner);
        this.loadPerTrip = data.loadPerTrip();
        this.framesPerTrip = Math.max(1, data.framesPerTrip());
        this.searchRange = data.searchRange() <= 0f ? Float.MAX_VALUE : data.searchRange();
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
        }
    }

    private void load(GameObject owner, World world) {
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

    private void carry(GameObject owner, World world) {
        var depot = world.findClosest(owner.getPosition(), Float.MAX_VALUE,
                candidate -> candidate.findModule(SupplyDepot.class) != null
                        && candidate.getPlayerIndex() == owner.getPlayerIndex());
        if (depot != null && !walkTo(owner, depot)) {
            return; // still on its way
        }
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
