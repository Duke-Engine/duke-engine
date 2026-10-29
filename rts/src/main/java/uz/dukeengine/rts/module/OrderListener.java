package uz.dukeengine.rts.module;

import uz.dukeengine.core.message.Command;

/**
 * A module told the standard orders its unit is given — a move, an attack, a stop, an attack-move, a guard, a build
 * order the engine took — on the
 * simulation thread, in the order they are applied, after the engine's own handling, whether or not the unit has a
 * weapon or a locomotor: the reference's SPAWNS_ARE_THE_WEAPONS objects, which send their spawns to attack and stop
 * them, and a Nuke Cannon keeping an order pending while it packs up.
 */
public interface OrderListener {

    /**
     * Its unit was given {@code order}: one every side gives ({@code CombatOrder} — a move, an attack, a stop) or an
     * RTS's own ({@code GameMessage}).
     */
    void onOrder(Command order);

    /**
     * Whether its unit will not take {@code order} at all — the reference's {@code isAllowedToRespondToAiCommands}, a
     * gunship that answers only its own update ({@code ForbidPlayerCommands}): left out of the order as if not named,
     * neither moved nor told. None by default.
     */
    default boolean refuses(Command order) {
        return false;
    }

    /** Whether any of {@code unit}'s modules {@linkplain #refuses refuses} {@code order}. */
    static boolean refused(uz.dukeengine.core.thing.GameObject unit, Command order) {
        return unit.anyModule(module -> module instanceof OrderListener listener && listener.refuses(order));
    }
}
