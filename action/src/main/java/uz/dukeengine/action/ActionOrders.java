package uz.dukeengine.action;

import java.util.List;
import uz.dukeengine.combat.message.GameOrder;
import uz.dukeengine.combat.module.Errand;
import uz.dukeengine.combat.module.WeaponUpdate;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.core.thing.World;

/**
 * The orders of a game played from one hero's shoulder, sent as orders of the game's own ({@link GameOrder}) so they
 * travel, apply on a frame boundary and are written into a replay as every order is. A game carries them out by handing
 * each order it hears to {@link #apply}, from its {@code DukeGame.onOrder}:
 *
 * <pre>{@code game.onOrder(order -> ActionOrders.apply(order, game.getLogic()));}</pre>
 */
public final class ActionOrders {

    /** The word of a walk along a held direction — see {@link #along}. */
    public static final String ALONG = "ALONG";
    /** The word of an attack toward a point — see {@link #toward}. */
    public static final String TOWARD = "TOWARD";
    /** No direction: stop where it stands. */
    public static final int NONE = -1;

    private ActionOrders() {
    }

    /**
     * Walk along a direction until told otherwise — a held W A S D: {@code angle} in 256ths of a turn, 0 along +x and
     * counter-clockwise seen from above, or {@link #NONE} to stop where it stands. No route: it slides along what stops
     * it ({@link Stride}). Replaces the unit's order, as a move does; an attack toward a point goes on.
     */
    public static GameOrder along(int playerIndex, List<ObjectId> units, int angle) {
        return new GameOrder(playerIndex, ALONG, units, null, null, angle < 0 ? NONE : angle & 0xFF);
    }

    /**
     * An attack toward a point, no target named — see {@link Swing}; {@code held}, again each time the weapon is ready
     * toward the latest point, until an order with it false.
     */
    public static GameOrder toward(int playerIndex, List<ObjectId> units, Coord3D point, boolean held) {
        return new GameOrder(playerIndex, TOWARD, units, point, null, held ? 1 : 0);
    }

    /**
     * Carry out {@code order} in {@code world} if it is one of these — on each of its units that is its player's, the
     * errands they were on given up — on the simulation thread, where orders of the game's own are heard.
     *
     * @return whether it was one of these
     */
    public static boolean apply(GameOrder order, World world) {
        switch (order.word()) {
            case ALONG -> {
                for (var id : order.units()) {
                    var unit = ownUnit(order, world, id);
                    if (unit == null) {
                        continue;
                    }
                    var weapon = unit.findModule(WeaponUpdate.class);
                    if (weapon != null) {
                        weapon.dropTarget(); // its own way overrides a target; a swing toward a point goes on
                    }
                    Stride.walk(unit, (int) order.number());
                }
            }
            case TOWARD -> {
                for (var id : order.units()) {
                    var unit = ownUnit(order, world, id);
                    if (unit != null) {
                        Swing.toward(unit, order.place(), order.number() != 0);
                    }
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The unit {@code id} names, its errands given up, where it is {@code order}'s player's; null otherwise. */
    private static GameObject ownUnit(GameOrder order, World world, ObjectId id) {
        var unit = world.findObject(id);
        if (unit == null || unit.getPlayerIndex() != order.playerIndex()) {
            return null;
        }
        Errand.giveUpAll(unit);
        return unit;
    }
}
