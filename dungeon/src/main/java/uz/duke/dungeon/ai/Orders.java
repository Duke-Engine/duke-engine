package uz.duke.dungeon.ai;

/**
 * What a player has told the game that outlives the frame it arrived on.
 *
 * <p>Two things, and a command is a moment while both of these are states, so
 * something has to remember them between the two and this is the smallest thing
 * that can: whether his creatures have been told to stand still and start
 * nothing, and which single creature he has picked out to look at.
 *
 * <p>The second is not an order at all -- nothing in the world changes because of
 * it -- and it lives here anyway, because it has exactly the shape of one: it
 * arrives as a command, it lasts until the next, and the thing that needs it
 * cannot be addressed from the thing that sets it.
 *
 * <p>Held beside the brain rather than inside it because the two ends cannot see
 * each other: the command arrives at the session's command handler, which has a
 * player number, and the brain is a module on an object, which has no way of
 * being addressed from there. The engine's script module does not hand its script
 * back out, and reaching through it if it did would be worse — a game has no
 * business rummaging in the engine's modules for its own state.
 *
 * <p>Built once per session and handed to both ends the way the power book and
 * the loot bag already are. Deterministic for the same reason they are: it is
 * only ever written while a command is being applied, on the simulation thread,
 * on a frame boundary.
 */
public final class Orders {

    /** By player, so a second hero in the same game keeps his own answer. */
    private final java.util.Set<Integer> holding = new java.util.HashSet<>();

    /**
     * Tell one player's creatures to stand still and start nothing, or let them
     * go again.
     *
     * <p>Set rather than toggled, because the two buttons that change it are two
     * buttons: Stop means stop and Guard means guard, and a player who presses
     * Stop twice meant it twice. A toggle also cannot be shown -- a button that
     * lights when the state is on has to be told which state, and "the other one"
     * is not a state.
     */
    public void hold(int playerIndex, boolean stand) {
        if (stand) {
            holding.add(playerIndex);
        } else {
            holding.remove(playerIndex);
        }
    }

    public boolean isHolding(int playerIndex) {
        return holding.contains(playerIndex);
    }

    /** By player, for the same reason: one screen each, one selection each. */
    private final java.util.Map<Integer, uz.duke.core.thing.ObjectId> watching =
            new java.util.HashMap<>();

    /** Say which creature a player has picked out, or {@code null} for none. */
    public void watch(int playerIndex, uz.duke.core.thing.ObjectId unit) {
        if (unit == null) {
            watching.remove(playerIndex);
        } else {
            watching.put(playerIndex, unit);
        }
    }

    /** The creature that player has picked out, or {@code null}. */
    public uz.duke.core.thing.ObjectId watchedBy(int playerIndex) {
        return watching.get(playerIndex);
    }

    /** Forget everything — a new run is a new set of orders. */
    public void clear() {
        holding.clear();
        watching.clear();
    }
}
