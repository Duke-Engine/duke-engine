package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The reference's mouse, a left click commanding and a right click letting go, beside the one every game had. */
class MouseTest {

    @Test
    void withTheLeftCommandingAClickOffHisOwnThingsOrdersWhatIsSelected() {
        var mouse = Mouse.LEFT_COMMANDS;

        assertFalse(mouse.leftClickOrders(true, false), "a tank of his clicked with nothing selected: selected");
        assertTrue(mouse.leftClickOrders(false, true), "then open ground: a move, the tank still selected");
        assertTrue(mouse.leftClickOrders(false, true), "or an enemy: an attack — the order the right button gave");
        assertFalse(mouse.leftClickOrders(true, true), "another of his own: selected instead");
        assertFalse(mouse.leftClickOrders(false, false), "nothing of his selected: a click that selects, as ever");
        assertTrue(mouse.rightClickLetsGo(), "and the right button lets the selection go");
    }

    @Test
    void theDefaultIsTheMouseEveryGameHad() {
        var mouse = Mouse.RIGHT_COMMANDS;

        assertFalse(mouse.leftClickOrders(false, true), "a left click selects, whatever it lands on");
        assertFalse(mouse.leftClickOrders(true, true));
        assertFalse(mouse.rightClickLetsGo(), "and the right button orders");
    }
}
