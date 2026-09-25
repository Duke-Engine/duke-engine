package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The answer to a click that gives the game's word, named by the game: a supply truck sent to a depot says its supply
 * line, a steered power says nothing on the click, and a word it names nothing for is answered as a move, as always.
 */
class OrderAnswerTest {

    @Test
    void aWordAnsweredWithSupplyNoneOrNothingNamed() {
        var visuals = Visuals.create().orderAnswer("Dock", "supply").orderAnswer("Steer", null);

        assertEquals("supply", visuals.orderAnswerFor("Dock"), "ordered.supply.<first selected>");
        assertNull(visuals.orderAnswerFor("Steer"), "nothing played");
        assertEquals("move", visuals.orderAnswerFor("Enter"), "no answer named: ordered.move.<first selected>, as now");
    }
}
