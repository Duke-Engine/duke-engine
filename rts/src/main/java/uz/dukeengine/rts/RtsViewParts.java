package uz.dukeengine.rts;

import java.util.List;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.thing.World;
import uz.dukeengine.core.view.RallyView;
import uz.dukeengine.core.view.Turrets;
import uz.dukeengine.core.view.ViewParts;
import uz.dukeengine.rts.construction.ConstructionSite;
import uz.dukeengine.rts.construction.Selling;
import uz.dukeengine.rts.module.ContainModule;
import uz.dukeengine.rts.module.PowerGrid;
import uz.dukeengine.rts.module.ProductionUpdate;
import uz.dukeengine.rts.module.Turret;
import uz.dukeengine.rts.player.RtsPlayer;
import uz.dukeengine.rts.thing.RtsKinds;

/**
 * The RTS's parts of the picture: a thing's kind words, what its factory makes and its hold carries, how far a site is
 * built or a sold building down, its turrets and its rally point; a side's money and spare power.
 */
final class RtsViewParts implements ViewParts {

    static final RtsViewParts INSTANCE = new RtsViewParts();

    private RtsViewParts() {
    }

    @Override
    public GameObject shownOn(GameObject contained) {
        var carrier = ContainModule.holdOf(contained);
        return carrier != null && (carrier.rides(contained) || carrier.showsPassengers()) ? carrier.getOwner() : null;
    }

    @Override
    public boolean structure(GameObject thing) {
        return thing.isKindOf(RtsKinds.STRUCTURE);
    }

    @Override
    public boolean selectable(GameObject thing) {
        return thing.isKindOf(RtsKinds.SELECTABLE);
    }

    @Override
    public int queued(GameObject thing) {
        var production = thing.findModule(ProductionUpdate.class);
        return production == null ? -1 : production.getQueueSize();
    }

    @Override
    public List<Integer> passengers(GameObject thing) {
        var hold = thing.findModule(ContainModule.class);
        return hold == null ? List.of() : hold.getPassengers().stream().map(ObjectId::value).toList();
    }

    /** A site's progress, a sold building's share of the way down (from 1 to the reference's -0.5), else whole. */
    @Override
    public float built(GameObject thing) {
        if (thing.hasStatus(ObjectStatus.UNDER_CONSTRUCTION)) {
            var site = thing.findModule(ConstructionSite.class);
            return site == null ? 1f : site.progress();
        }
        if (thing.hasStatus(ObjectStatus.SOLD)) {
            var sale = thing.findModule(Selling.Coming.class);
            return sale == null ? 1f : Math.min(1f, sale.share() / 100f);
        }
        return 1f;
    }

    /** As its game's {@code Turret} has them, the first one's. */
    @Override
    public Turrets turrets(GameObject thing) {
        for (var module : thing.getModules()) {
            if (module instanceof Turret turret) {
                return new Turrets(turret.turretTurn(), turret.turretPitch(), turret.altTurretTurn(),
                        turret.altTurretPitch());
            }
        }
        return null;
    }

    @Override
    public RallyView rally(GameObject thing) {
        var production = thing.findModule(ProductionUpdate.class);
        var line = production == null ? null : production.rallyLine();
        return line == null ? null : new RallyView(thing.getId().value(), line.rallyPoint(), line.points(),
                line.nodes());
    }

    @Override
    public int money(World world, int player) {
        var side = RtsPlayer.of(world, player);
        return side == null ? 0 : side.getMoney();
    }

    @Override
    public int powerSurplus(World world, int player) {
        return PowerGrid.surplus(world, player);
    }
}
