package uz.duke.core.network;

/**
 * Two peers computed different worlds for the same frame: the simulations have
 * diverged and are no longer the same game.
 *
 * <p>The frame matters more than the numbers. Divergence is not gradual — one
 * frame the worlds agree, the next they do not — so the reported frame is where
 * to look, and the commands applied on it are the first suspects.
 *
 * <p>Nothing here says what to do about it. Stopping, kicking the odd peer out,
 * or resyncing from a snapshot are decisions about a particular game, not about
 * the engine.
 */
public record Desync(
        int frame,
        int localPlayer,
        long localChecksum,
        int otherPlayer,
        long otherChecksum) {

    @Override
    public String toString() {
        return "desync at frame " + frame
                + ": player " + localPlayer + " has " + localChecksum
                + ", player " + otherPlayer + " has " + otherChecksum;
    }
}
