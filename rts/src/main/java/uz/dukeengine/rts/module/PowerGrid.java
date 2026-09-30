package uz.dukeengine.rts.module;

import uz.dukeengine.core.thing.World;

/**
 * A player's power balance, derived from the {@link PowerModule}s on the objects
 * they own — the Generals rule that a base which outgrows its generators stops
 * producing.
 *
 * <p>Derived state, never stored: nothing has to be updated when a generator is
 * built or destroyed, so there is no cache to go stale (or to desync).
 */
public final class PowerGrid {

    private PowerGrid() {
    }

    /**
     * Net power for a player: everything produced minus everything consumed, every power module of every thing of its
     * that stands — a thing lying dead while its death plays out gives and takes none.
     */
    public static int surplus(World world, int playerIndex) {
        int surplus = 0;
        for (var object : world.getObjectsOf(playerIndex)) {
            if (object.isEffectivelyDead()) {
                continue;
            }
            for (var module : object.getModules()) {
                if (module instanceof PowerModule power) {
                    surplus += power.getProduced() - power.getConsumed();
                }
            }
        }
        return surplus;
    }

    /** Whether the player's production covers its consumption. */
    public static boolean isPowered(World world, int playerIndex) {
        return surplus(world, playerIndex) >= 0;
    }
}
