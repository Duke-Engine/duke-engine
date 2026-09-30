package uz.dukeengine.core.player;

import java.util.HashMap;
import java.util.Map;

/**
 * A participant in the game — a human or AI side, ported from SAGE's
 * {@code Player}.
 *
 * <p>The engine's player is deliberately thin: an identity and a diplomatic
 * stance toward every other player. That much is common to anything with sides;
 * resources, tech and scores differ per genre and belong to the game. Subclass
 * this and hand the subclass's constructor to {@link PlayerList} to add them —
 * {@code uz.dukeengine.rts.player.RtsPlayer} does exactly that.
 *
 * <p>Objects reference their owner by {@code playerIndex}, so a player's index
 * is its stable identity. Relationships are stored sparsely: any pair not
 * explicitly set is {@link Relationship#NEUTRAL}, and a player is always allied
 * with itself.
 */
public class Player {

    private final int index;
    private final String name;
    private final Map<Integer, Relationship> relationships = new HashMap<>();
    private boolean computer;

    public Player(int index, String name) {
        this.index = index;
        this.name = name;
    }

    /**
     * What the side keeps that every machine must agree on, mixed into the frame's checksum ({@code
     * GameLogic.checksum}) — its money, its sciences, for a player of a game that keeps them. A plain player keeps
     * nothing of its own and sums nothing.
     */
    public long checksum(long hash) {
        return hash;
    }

    public final int getIndex() {
        return index;
    }

    public final String getName() {
        return name;
    }

    /**
     * Whether a computer plays this side rather than a person — any game's sides are one or the other: what only a
     * computer may make, what a computer's units do on their own, a site sought where a person's may not stand.
     */
    public final boolean isComputer() {
        return computer;
    }

    public final void setComputer(boolean computer) {
        this.computer = computer;
    }

    /** This player's stance toward {@code other}. */
    public final Relationship getRelationshipTo(Player other) {
        if (other.index == this.index) {
            return Relationship.ALLIES;
        }
        return relationships.getOrDefault(other.index, Relationship.NEUTRAL);
    }

    /** Set this player's one-way stance toward {@code other}. */
    public final void setRelationshipTo(Player other, Relationship relationship) {
        if (other.index == this.index) {
            return; // a player's stance toward itself is fixed
        }
        relationships.put(other.index, relationship);
    }

    public final boolean isEnemyOf(Player other) {
        return getRelationshipTo(other) == Relationship.ENEMIES;
    }

    public final boolean isAllyOf(Player other) {
        return getRelationshipTo(other) == Relationship.ALLIES;
    }
}
