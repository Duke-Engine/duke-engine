package uz.dukeengine.rts.construction;

import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.rts.Buildable;
import uz.dukeengine.rts.RtsSimulation;
import uz.dukeengine.rts.message.GameMessage;

/**
 * Where a {@code Construct} order is accepted or refused, and a site called off.
 *
 * <p>Both run inside a frame, as the order is applied — on every machine at once — so the money that goes, the
 * builder sent, and the answer when it is no are the same on all of them. Nothing here is ever done from the
 * window's thread.
 */
public final class Construction {

    private Construction() {
    }

    /**
     * Accept a {@code Construct} order, or refuse it at no cost.
     *
     * <p>Refused if the builder is not the player's, cannot walk, or is gone; if nothing of that name exists;
     * if the side may not make it yet ({@link RtsSimulation#canBuild}); if the place does not fit
     * ({@link Placement}); or if the player cannot pay. Accepted, the money goes
     * now, any errand the builder was already on is given up with its money back, and it sets off.
     *
     * @return whether it was accepted
     */
    public static boolean order(RtsSimulation world, GameMessage.Construct order, PlacementRules rules) {
        var builder = world.findObject(order.builder());
        if (builder == null || builder.isEffectivelyDead() || builder.getPlayerIndex() != order.playerIndex()
                || builder.findModule(MoveUpdate.class) == null) {
            return false;
        }
        var template = world.findTemplate(order.template());
        if (template == null || world.getPathGrid() == null || !world.canBuild(order.playerIndex(), template)) {
            return false;
        }
        var fit = Placement.check(world, world.getPathGrid(), template, order.place(), order.facing(), rules);
        if (fit != Placement.Fit.FITS) {
            return false;
        }
        var player = world.getRtsPlayer(order.playerIndex());
        int cost = player == null ? 0 : player.priceOf(template);
        if (player == null || !player.withdraw(cost)) {
            return false;
        }
        // One errand at a time. Given up before the new one is added — and the old ones taken off, which is
        // safe here, between frames of the builder's own updates, where it would not be during them.
        for (var module : java.util.List.copyOf(builder.getModules())) {
            if (module instanceof BuildOrder earlier) {
                earlier.giveUp();
                builder.removeModule(earlier);
            }
        }
        // Put down now, where the rules say so, and the builder then goes to it; otherwise when it arrives.
        var site = rules.siteAtOrder()
                ? BuildOrder.putDown(builder, template, order.place(), order.facing(), cost, rules) : null;
        var errand = new BuildOrder(builder, template, order.place(), order.facing(), cost, rules, site);
        builder.addModule(errand);
        errand.begin();
        return true;
    }

    /**
     * Call off a site still going up, if it is the player's.
     *
     * @return whether there was a site of theirs to call off
     */
    public static boolean cancel(RtsSimulation world, GameMessage.CancelConstruction order) {
        var site = world.findObject(order.site());
        if (site == null || site.getPlayerIndex() != order.playerIndex()) {
            return false;
        }
        var building = site.findModule(ConstructionSite.class);
        return building != null && building.cancel();
    }
}
