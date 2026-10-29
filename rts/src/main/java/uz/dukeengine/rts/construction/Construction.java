package uz.dukeengine.rts.construction;

import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.MoveUpdate;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
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
     * Refused unless {@code rules} are the placement rules of the world {@code owner} is in: those are the game's, set on
     * the world and made again by the game on a load, so a save writes none; an order given others cannot be written.
     */
    static void expectWorldRules(GameObject owner, PlacementRules rules, Class<?> module) {
        if (!(owner.getWorld() instanceof RtsSimulation world) || !rules.equals(world.getPlacementRules())) {
            throw new uz.dukeengine.core.save.UnsavedModuleException(module.getName(), "thing " + owner.getId().value()
                    + " (" + owner.getTemplate().name() + ") holds placement rules that are not its world's: those are"
                    + " the game's, which a save does not write");
        }
    }

    /** The placement rules of the world a module is revived in, which it was saved holding. */
    static PlacementRules worldRules(uz.dukeengine.core.save.SaveIn in, Class<?> module) {
        if (!(in.world() instanceof RtsSimulation world)) {
            throw new uz.dukeengine.core.save.UnsavedModuleException(module.getName(),
                    module.getName() + " is loaded into a world that is no RtsSimulation, with no placement rules");
        }
        return world.getPlacementRules();
    }

    /**
     * Accept a {@code Construct} order, or refuse it at no cost.
     *
     * <p>Refused if the builder is not the player's, cannot walk, or is gone; if nothing of that name exists;
     * if the side may not make it yet ({@link RtsSimulation#canBuild}); if the place does not fit as a site of the
     * player's ({@link Placement}), which may ask what his side has seen; or if the player cannot pay. Accepted, the money goes
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
        var fit = Placement.check(world, world.getPathGrid(), template, order.place(), order.facing(), rules,
                order.playerIndex());
        if (fit != Placement.Fit.FITS) {
            return false;
        }
        var player = world.getRtsPlayer(order.playerIndex());
        int cost = player == null ? 0 : player.priceOf(template);
        if (player == null || !player.withdraw(cost)) {
            return false;
        }
        giveUpErrands(builder);
        // Put down now, where the rules say so, and the builder then goes to it; otherwise when it arrives.
        var site = rules.siteAtOrder()
                ? BuildOrder.putDown(builder, template, order.place(), order.facing(), cost, rules) : null;
        var errand = new BuildOrder(builder, template, order.place(), order.facing(), cost, rules, site);
        builder.addModule(errand);
        errand.begin();
        return true;
    }

    /**
     * Raise {@code template} again for the builder's side at {@code place}, facing {@code facing} degrees — the
     * reference's rebuild ({@code DozerAIUpdate::construct} with {@code isRebuild}), which asks none of what a
     * player's order is asked: not whether the side may build it, not whether the place fits, and not its price. The
     * site is put down now, as the reference's always is, at no cost, and the builder sent to it. Simulation code's
     * alone: no order on the wire asks it. Refused only if the builder is gone or cannot walk, or nothing of that name
     * exists.
     *
     * @return whether it was raised
     */
    public static boolean rebuild(RtsSimulation world, ObjectId builderId, String templateName, Coord3D place,
            float facing, PlacementRules rules) {
        var builder = world.findObject(builderId);
        var template = world.findTemplate(templateName);
        if (builder == null || builder.isEffectivelyDead() || builder.findModule(MoveUpdate.class) == null
                || template == null) {
            return false;
        }
        giveUpErrands(builder);
        var site = BuildOrder.putDown(builder, template, place, facing, 0, rules);
        var errand = new BuildOrder(builder, template, place, facing, 0, rules, site);
        builder.addModule(errand);
        errand.begin();
        return true;
    }

    /**
     * One errand at a time. Given up before the new one is added — and the old ones taken off, which is safe here,
     * between frames of the builder's own updates, where it would not be during them.
     */
    private static void giveUpErrands(GameObject builder) {
        for (var module : java.util.List.copyOf(builder.getModules())) {
            if (module instanceof BuildOrder earlier) {
                earlier.giveUp();
                builder.removeModule(earlier);
            }
        }
    }

    /**
     * Send a builder to take up a half-built site of its side, or refuse at no cost — the reference's {@code
     * ActionManager::canResumeConstructionOf}: refused if the builder is not the player's, cannot walk, or is gone; if
     * the site is not the player's, not going up, or gone; or while its builder works on it. Taken, the builder is the
     * site's from now — "even thinking about building sets us as the current builder", as the reference has it, so a
     * second is refused — gives up any errand it was on, and sets off.
     *
     * @return whether it was taken
     */
    public static boolean resume(RtsSimulation world, GameMessage.ResumeConstruction order, PlacementRules rules) {
        var builder = world.findObject(order.builder());
        var site = world.findObject(order.site());
        if (builder == null || builder.isEffectivelyDead() || builder.getPlayerIndex() != order.playerIndex()
                || builder.findModule(MoveUpdate.class) == null || site == null || site.isEffectivelyDead()
                || site.getPlayerIndex() != order.playerIndex()) {
            return false;
        }
        var building = site.findModule(ConstructionSite.class);
        if (building == null || building.isFinished() || !building.takeUp(builder)) {
            return false;
        }
        giveUpErrands(builder);
        var errand = new BuildOrder(builder, site.getTemplate(), site.getPosition(),
                (float) Math.toDegrees(site.getOrientation()), 0, rules, site);
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
