package uz.duke.core.player;

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
 * {@code uz.duke.rts.player.RtsPlayer} does exactly that.
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

    public Player(int index, String name) {
        this.index = index;
        this.name = name;
    }

    public final int getIndex() {
        return index;
    }

    public final String getName() {
        return name;
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
