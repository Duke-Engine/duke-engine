package uz.dukeengine.core.network;

/**
 * Whether a networked game can still advance, and if not, why.
 *
 * <p>The distinction matters because from the outside every stop looks the same:
 * a peer waiting on a slow player, a peer whose opponent unplugged, and a peer
 * whose world no longer matches anyone's are all simply not moving. Only the
 * first of those is normal, and a player deserves to be told which one they are
 * looking at rather than left watching a frozen screen.
 */
public enum SessionState {

    /** Playing. A stall here is ordinary: someone's input has not arrived yet. */
    RUNNING,

    /**
     * The worlds have diverged, so the peers are no longer playing the same game.
     * Continuing would be worse than stopping: every player would go on making
     * decisions about a world only they can see.
     */
    DESYNCED,

    /** This peer can no longer reach the game — for a guest, the host has gone. */
    DISCONNECTED;

    /** Whether the simulation may still take another frame. */
    public boolean canAdvance() {
        return this == RUNNING;
    }
}
