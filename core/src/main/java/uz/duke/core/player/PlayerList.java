package uz.duke.core.player;

import java.util.ArrayList;
import java.util.List;
import uz.duke.core.SubsystemInterface;

/**
 * The roster of all players in a game, ported from SAGE's {@code PlayerList}.
 *
 * <p>Index 0 is conventionally the neutral player (civilian/unowned objects).
 * Players are looked up by the {@code playerIndex} stored on each object, and
 * relationship queries between two indices flow through here so combat code has
 * a single place to ask "are these two enemies?".
 */
public final class PlayerList extends SubsystemInterface {

    public static final int NEUTRAL_INDEX = 0;

    private final List<Player> players = new ArrayList<>();

    @Override
    public void init() {
        reset();
    }

    @Override
    public void reset() {
        players.clear();
        // A game always has at least the neutral player at index 0.
        players.add(new Player(NEUTRAL_INDEX, "Neutral"));
    }

    @Override
    public void update() {
    }

    /** Add a player; its index must be the next free slot. */
    public Player addPlayer(String name) {
        var player = new Player(players.size(), name);
        players.add(player);
        return player;
    }

    public Player getPlayer(int index) {
        if (index < 0 || index >= players.size()) {
            return null;
        }
        return players.get(index);
    }

    public Player getNeutralPlayer() {
        return players.get(NEUTRAL_INDEX);
    }

    public int getPlayerCount() {
        return players.size();
    }

    /** The stance player {@code a} holds toward player {@code b}, by index. */
    public Relationship getRelationship(int a, int b) {
        var pa = getPlayer(a);
        var pb = getPlayer(b);
        if (pa == null || pb == null) {
            return Relationship.NEUTRAL;
        }
        return pa.getRelationshipTo(pb);
    }
}
