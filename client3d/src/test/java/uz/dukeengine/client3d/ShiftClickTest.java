package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A shift-click on one of the player's own things selects, whatever the game's word: a transport is boarded by a click
 * and added by a shift-click, as the reference's selection translator prefers selecting while Shift is held.
 */
class ShiftClickTest {

    @Test
    void aWordNamedForHisOwnTransportAClickOrdersAndAShiftClickChooses() {
        assertTrue(DukeRtsApp.leftClickOrders(Mouse.LEFT_COMMANDS, true, true, false, true),
                "a click posts the game's order");
        assertFalse(DukeRtsApp.leftClickOrders(Mouse.LEFT_COMMANDS, true, true, true, true),
                "a shift-click posts nothing, and adds the transport to the selection");
        assertFalse(DukeRtsApp.leftClickOrders(Mouse.LEFT_COMMANDS, true, false, false, true),
                "no word: a click chooses, as always");
        assertTrue(DukeRtsApp.leftClickOrders(Mouse.LEFT_COMMANDS, false, false, true, true),
                "off his own things a click commands, shift or not");
    }
}
