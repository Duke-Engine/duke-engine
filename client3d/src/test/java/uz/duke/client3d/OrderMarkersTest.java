package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The flash that says an order was heard: it appears where the player clicked and
 * is gone a moment later.
 *
 * <p>Time is handed in, so none of this waits on a clock to be tested.
 */
class OrderMarkersTest {

    @Test
    void anOrderLeavesAMarkWhereItWasGiven() {
        var markers = new OrderMarkers();
        markers.add(120f, 260f, OrderMarkers.Kind.MOVE, 10f);

        assertEquals(1, markers.markers().size());
        var mark = markers.markers().get(0);
        assertEquals(120f, mark.x(), 0.001f);
        assertEquals(260f, mark.y(), 0.001f);
        assertEquals(OrderMarkers.Kind.MOVE, mark.kind());
    }

    @Test
    void anAttackIsMarkedDifferentlyFromAMove() {
        var markers = new OrderMarkers();
        markers.add(10f, 10f, OrderMarkers.Kind.ATTACK, 0f);

        assertEquals(OrderMarkers.Kind.ATTACK, markers.markers().get(0).kind(),
                "so it can be drawn in its own colour");
    }

    @Test
    void aMarkFadesFromFullToNothingOverItsLife() {
        var markers = new OrderMarkers();
        markers.add(0f, 0f, OrderMarkers.Kind.MOVE, 100f);
        var mark = markers.markers().get(0);

        assertEquals(1f, OrderMarkers.remaining(mark, 100f), 0.001f, "full when it appears");
        assertTrue(OrderMarkers.remaining(mark, 100f + OrderMarkers.LIFETIME_SECONDS / 2f) < 1f,
                "dimmer halfway through");
        assertTrue(OrderMarkers.remaining(mark, 100f + OrderMarkers.LIFETIME_SECONDS / 2f) > 0f,
                "but still there");
        assertEquals(0f, OrderMarkers.remaining(mark, 100f + OrderMarkers.LIFETIME_SECONDS), 0.001f,
                "and spent at the end");
    }

    @Test
    void spentMarksArePrunedAway() {
        var markers = new OrderMarkers();
        markers.add(0f, 0f, OrderMarkers.Kind.MOVE, 0f);
        markers.add(50f, 50f, OrderMarkers.Kind.ATTACK, OrderMarkers.LIFETIME_SECONDS);

        markers.prune(OrderMarkers.LIFETIME_SECONDS + 0.1f);

        assertEquals(1, markers.markers().size(), "the older one has finished fading");
        assertEquals(OrderMarkers.Kind.ATTACK, markers.markers().get(0).kind());

        markers.prune(OrderMarkers.LIFETIME_SECONDS * 2f + 0.1f);
        assertTrue(markers.markers().isEmpty(), "and eventually none are left");
    }

    /** A new dungeon has no orders outstanding in it. */
    @Test
    void aNewWorldClearsTheMarks() {
        var markers = new OrderMarkers();
        markers.add(0f, 0f, OrderMarkers.Kind.MOVE, 0f);

        markers.clear();

        assertTrue(markers.markers().isEmpty());
    }
}
