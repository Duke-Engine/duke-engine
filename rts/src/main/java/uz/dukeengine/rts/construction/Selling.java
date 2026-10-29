package uz.dukeengine.rts.construction;

import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.Buildable;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.ContainModule;
import uz.dukeengine.combat.module.Errand;
import uz.dukeengine.rts.module.ProductionUpdate;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.rts.player.RtsPlayer;
import uz.dukeengine.rts.thing.RtsKinds;

/**
 * A building sold, as the reference's {@code BuildAssistant::sellObject} sells one: at once its queue is called off
 * and paid back in full, its passengers put out, whatever it was doing stopped, and it may no longer be selected
 * ({@link ObjectStatus#SOLD}); then, after its scaffolding stands a while, it comes down, and when it is down the side
 * gets its refund and it leaves the world — not destroyed by anyone. It can still be shot while it comes down, and a
 * building killed before it is down gives nothing back.
 */
public final class Selling {

    /** How far below the ground a sold building's share goes before it is gone: the reference's -50%. */
    static final float GONE_AT = -50f;

    private Selling() {
    }

    /**
     * Sell a building, if it is the player's, a structure, standing, whole and not sold already.
     *
     * @return whether it was sold
     */
    public static boolean order(RtsSimulation world, GameMessage.Sell order) {
        var building = world.findObject(order.building());
        if (building == null || building.getPlayerIndex() != order.playerIndex() || building.isEffectivelyDead()
                || !building.isKindOf(RtsKinds.STRUCTURE) || building.hasStatus(ObjectStatus.SOLD)
                || building.hasStatus(ObjectStatus.UNDER_CONSTRUCTION)) {
            return false;
        }
        var line = building.findModule(ProductionUpdate.class);
        if (line != null) {
            line.cancelAll();
        }
        var hold = building.findModule(ContainModule.class);
        if (hold != null) {
            hold.sold(); // out of it — or, a shared hold's, left in the network where another still holds it
        }
        var weapon = building.findModule(WeaponUpdate.class);
        if (weapon != null) {
            weapon.holdFire();
        }
        Errand.giveUpAll(building);
        building.setStatus(ObjectStatus.SOLD);
        building.addModule(new Coming(building, world.getSellRules(), world.getFrame()));
        // Coming down as it went up: the reference's BuildAssistant::sellObject shows the scaffold while it sinks.
        building.setCondition(world.getPlacementRules().words().partlyBuilt());
        building.setCondition(world.getPlacementRules().words().beingBuilt());
        return true;
    }

    /** What a sold building is worth: its template's refund where it names one, else its cost times the share. */
    static int refundOf(GameObject building, SellRules rules) {
        var template = building.getTemplate();
        int named = Buildable.refundOf(template);
        return named > 0 ? named : Math.round(Buildable.costOf(template) * rules.sellShare());
    }

    /** The building coming down, a frame at a time. */
    public static final class Coming extends UpdateModule implements uz.dukeengine.core.save.Saveable {

        private final SellRules rules;
        private final int soldAt;
        private float share = 99.9f;

        Coming(GameObject building, SellRules rules, int soldAt) {
            super(building);
            this.rules = rules;
            this.soldAt = soldAt;
        }

        /**
         * When it was sold and how much of it still stands. Its rules are the world's, which a game sets and a save does
         * not write: an order given others is refused.
         */
        @Override
        public void save(uz.dukeengine.core.save.SaveOut out) {
            if (!(getOwner().getWorld() instanceof RtsSimulation world) || !rules.equals(world.getSellRules())) {
                throw new uz.dukeengine.core.save.UnsavedModuleException(Coming.class.getName(), "thing "
                        + getOwner().getId().value() + " (" + getOwner().getTemplate().name() + ") comes down by sell"
                        + " rules that are not its world's: those are the game's, which a save does not write");
            }
            out.writeInt(soldAt).writeFloat(share);
        }

        /** Nothing: it is made again by {@link #revive}, from all it wrote. */
        @Override
        public void load(uz.dukeengine.core.save.SaveIn in) {
            throw new IllegalStateException("a sold building's coming down is made again by Selling.Coming.revive");
        }

        /** The coming down made again from its save, on {@code building}: what {@code ModuleFactory.revives} is given. */
        public static Coming revive(GameObject building, uz.dukeengine.core.save.SaveIn in) {
            if (!(in.world() instanceof RtsSimulation world)) {
                throw new uz.dukeengine.core.save.UnsavedModuleException(Coming.class.getName(),
                        "a sold building is loaded into a world that is no RtsSimulation, with no sell rules");
            }
            var coming = new Coming(building, world.getSellRules(), in.readInt());
            coming.share = in.readFloat();
            return coming;
        }

        /** How much of it stands, in percent: just under 100 when sold, down past 0 to -50 as it comes down. */
        public float share() {
            return share;
        }

        @Override
        public void update() {
            var building = getOwner();
            var world = building.getWorld();
            if (world == null || building.isEffectivelyDead() || building.isDestroyed()
                    || world.getFrame() - soldAt < rules.scaffoldFrames()) {
                return;
            }
            share -= rules.percentPerFrame();
            if (share > GONE_AT) {
                return;
            }
            int refund = refundOf(building, rules);
            var side = RtsPlayer.of(world, building.getPlayerIndex());
            if (side != null) {
                side.refund(refund);
            }
            if (world instanceof RtsSimulation rts) {
                rts.sold(building, refund);
            }
            building.markDestroyed();
        }
    }
}
