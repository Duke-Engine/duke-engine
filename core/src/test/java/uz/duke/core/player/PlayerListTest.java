package uz.duke.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerListTest {

    private PlayerList players;

    @BeforeEach
    void setUp() {
        players = new PlayerList();
        players.init();
    }

    @Test
    void neutralPlayerExistsAtIndexZero() {
        assertEquals(0, players.getNeutralPlayer().getIndex());
        assertEquals(1, players.getPlayerCount());
    }

    @Test
    void addedPlayersGetSequentialIndices() {
        var usa = players.addPlayer("USA");
        var china = players.addPlayer("China");
        assertEquals(1, usa.getIndex());
        assertEquals(2, china.getIndex());
        assertEquals(3, players.getPlayerCount());
    }

    @Test
    void playerIsAlliedWithItself() {
        var usa = players.addPlayer("USA");
        assertTrue(usa.isAllyOf(usa));
        assertEquals(Relationship.ALLIES, usa.getRelationshipTo(usa));
    }

    @Test
    void defaultRelationshipIsNeutral() {
        var usa = players.addPlayer("USA");
        var china = players.addPlayer("China");
        assertEquals(Relationship.NEUTRAL, usa.getRelationshipTo(china));
        assertFalse(usa.isEnemyOf(china));
    }

    @Test
    void relationshipsAreOneWayUnlessSetBoth() {
        var usa = players.addPlayer("USA");
        var china = players.addPlayer("China");
        usa.setRelationshipTo(china, Relationship.ENEMIES);
        assertTrue(usa.isEnemyOf(china));
        assertFalse(china.isEnemyOf(usa)); // china hasn't declared back
        assertEquals(Relationship.ENEMIES, players.getRelationship(usa.getIndex(), china.getIndex()));
    }

    /** A game's own player type, the way the RTS module supplies RtsPlayer. */
    static final class ScoredPlayer extends Player {
        int score;

        ScoredPlayer(int index, String name) {
            super(index, name);
        }
    }

    @Test
    void aGameSuppliesItsOwnPlayerType() {
        var roster = new PlayerList(ScoredPlayer::new);
        roster.init();

        var added = roster.addPlayer("USA");
        assertInstanceOf(ScoredPlayer.class, added);
        // even the neutral player created by reset() is the game's type
        assertInstanceOf(ScoredPlayer.class, roster.getNeutralPlayer());
    }

    @Test
    void outOfRangeIndexReturnsNull() {
        assertNull(players.getPlayer(99));
        assertNull(players.getPlayer(-1));
    }
}
