package uz.dukeengine.rts.module;

import uz.dukeengine.rts.message.GameMessage;

/**
 * A module told the standard orders its unit is given — a move, an attack, a stop, an attack-move, a guard, a build
 * order the engine took — on the
 * simulation thread, in the order they are applied, after the engine's own handling, whether or not the unit has a
 * weapon or a locomotor: the reference's SPAWNS_ARE_THE_WEAPONS objects, which send their spawns to attack and stop
 * them, and a Nuke Cannon keeping an order pending while it packs up.
 */
public interface OrderListener {

    /** Its unit was given {@code order}. */
    void onOrder(GameMessage order);
}
