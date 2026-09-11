package uz.duke.dungeon.ai;

/**
 * The standing orders a player has given, which outlive the frame they arrived
 * on.
 *
 * <p>One today: whether his hero has been told to hold his ground. A command is a
 * moment and this is a state, so something has to remember it between the two,
 * and this is the smallest thing that can.
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

    /** Turn holding on or off for one player, and say what it became. */
    public boolean toggleHold(int playerIndex) {
        if (!holding.add(playerIndex)) {
            holding.remove(playerIndex);
            return false;
        }
        return true;
    }

    public boolean isHolding(int playerIndex) {
        return holding.contains(playerIndex);
    }

    /** Forget everything — a new run is a new set of orders. */
    public void clear() {
        holding.clear();
    }
}
