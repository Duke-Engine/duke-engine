package uz.dukeengine.rts.construction;

import uz.dukeengine.core.module.UpdateModule;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.Buildable;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.ContainModule;
import uz.dukeengine.rts.module.Errand;
import uz.dukeengine.rts.module.ProductionUpdate;
import uz.dukeengine.rts.module.WeaponUpdate;
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
    public static final class Coming extends UpdateModule {

        private final SellRules rules;
        private final int soldAt;
        private float share = 99.9f;

        Coming(GameObject building, SellRules rules, int soldAt) {
            super(building);
            this.rules = rules;
            this.soldAt = soldAt;
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
