package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * What a scheme need not say. A touch scheme answers only what it takes over; everything else stays the client's own.
 */
class ControlSchemeTest {

    @Test
    void theClientsOwnControlsHoldNoPlacementAndAimNothing() {
        var classic = ControlScheme.CLASSIC;

        assertFalse(classic.holdsPlacement(), "a place is put down as the press is let go");
        assertFalse(classic.aiming());
        assertNull(classic.aimPoint());
    }

    @Test
    void aSchemeMayHoldAPlacementAndTheOthersStayTheClients() {
        var touch = new ControlScheme() {
            @Override
            public boolean holdsPlacement() {
                return true;
            }
        };

        assertTrue(touch.holdsPlacement());
        assertFalse(touch.left(true), "the left button is still the client's selecting");
        assertTrue(touch.rightOrders(true, true));
    }
}
