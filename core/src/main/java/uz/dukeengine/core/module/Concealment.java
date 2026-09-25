package uz.dukeengine.core.module;

/**
 * A module that may keep its thing from some players: not shown to them, not selectable, hovered or on their radar,
 * and not a target for them — the reference's stealth, hidden from every player but its own side and allies until
 * someone detects it. Any module of a thing saying so hides it. Asked on the simulation thread, so the answer is a pure
 * function of the simulation's state; a thing is never hidden from its own side, and a watcher sees everything.
 */
public interface Concealment {

    /** Whether its thing is hidden from {@code player} now. */
    boolean hiddenFrom(int player);
}
