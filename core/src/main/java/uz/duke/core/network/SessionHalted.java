package uz.duke.core.network;

/**
 * The host has stopped the game, and this is why.
 *
 * <p>Both an instruction and a finding. The instruction matters because a peer
 * that has not noticed the divergence itself would otherwise play on alone; the
 * finding matters because when a player sends in their log, it should say the
 * same thing everyone else's does.
 *
 * <p>Only the host sends this, and it is always in a position to: everyone
 * compares against everyone, and agreement is transitive, so any disagreement
 * anywhere is a disagreement the host is part of. Two guests cannot differ from
 * each other while both matching the host.
 *
 * @param frame      the frame the worlds stopped matching on
 * @param playerIndex the peer whose world differed from the announcer's
 * @param expected   the announcer's hash of that frame
 * @param actual     that peer's hash of the same frame
 */
public record SessionHalted(int frame, int playerIndex, long expected, long actual)
        implements NetMessage {

    @Override
    public String toString() {
        return "game halted at frame " + frame + ": player " + playerIndex
                + " computed " + actual + " where the host computed " + expected;
    }
}
