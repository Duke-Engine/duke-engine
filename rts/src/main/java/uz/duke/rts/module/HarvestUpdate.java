package uz.duke.rts.module;

import uz.duke.core.module.ModuleData;
import uz.duke.core.module.ModuleGroup;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.module.UpdateModule;
import uz.duke.core.thing.GameObject;
import uz.duke.rts.player.RtsPlayer;

/**
 * Gathers resources from the nearest {@link SupplyModule} pile and turns them
 * into money for the owner, ported in spirit from SAGE's harvester
 * {@code SupplyTruckAIUpdate}.
 *
 * <p>A trip takes {@code framesPerTrip} logic frames; on completion the harvester
 * draws up to {@code loadPerTrip} from the nearest non-empty supply pile and
 * deposits whatever it got into the owning player's treasury. With no pile in
 * reach it idles. This models the gather→deposit loop without the full
 * drive-there-and-back movement (a natural later refinement using {@link MoveUpdate}).
 */
@ModuleGroup(RtsModuleGroups.ECONOMY)
public final class HarvestUpdate extends UpdateModule {

    /** INI config: {@code LoadPerTrip}, {@code FramesPerTrip}. */
    public record Data(int loadPerTrip, int framesPerTrip) implements ModuleData {
    }

    private final int loadPerTrip;
    private final int framesPerTrip;
    private int elapsed;

    public HarvestUpdate(GameObject owner, Data data) {
        super(owner);
        this.loadPerTrip = data.loadPerTrip();
        this.framesPerTrip = Math.max(1, data.framesPerTrip());
    }

    @Override
    public void update() {
        var owner = getOwner();
        var world = owner.getWorld();
        if (world == null) {
            return;
        }
        var pile = world.findClosest(owner.getPosition(), Float.MAX_VALUE, candidate -> {
            var supply = candidate.findModule(SupplyModule.class);
            return supply != null && supply.getRemaining() > 0;
        });
        if (pile == null) {
            return; // nothing left to harvest — idle
        }

        elapsed++;
        if (elapsed < framesPerTrip) {
            return;
        }
        elapsed = 0;

        int taken = pile.findModule(SupplyModule.class).take(loadPerTrip);
        var player = RtsPlayer.of(world, owner.getPlayerIndex());
        if (taken > 0 && player != null) {
            player.deposit(taken);
        }
    }
}
