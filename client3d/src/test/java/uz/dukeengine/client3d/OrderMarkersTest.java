package uz.dukeengine.client3d;

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

    /**
     * A mark is kept until its life has run out, and then let go.
     *
     * <p>How long that is comes from outside: it is part of how the mark looks,
     * and the look is the game's — see {@link OrderMark}. This class is only the
     * list of what was ordered and where.
     */
    @Test
    void aMarkIsKeptForItsLifeAndNoLonger() {
        var markers = new OrderMarkers();
        markers.add(0f, 0f, OrderMarkers.Kind.MOVE, 100f);

        markers.prune(100.3f, 0.4f);
        assertEquals(1, markers.markers().size(), "still within its life");

        markers.prune(100.4f, 0.4f);
        assertTrue(markers.markers().isEmpty(), "and let go once it is spent");
    }

    /** Several orders in quick succession are all kept, oldest first. */
    @Test
    void quickClicksAllLeaveTheirOwnMark() {
        var markers = new OrderMarkers();
        markers.add(1f, 1f, OrderMarkers.Kind.MOVE, 100f);
        markers.add(2f, 2f, OrderMarkers.Kind.MOVE, 100.1f);
        markers.add(3f, 3f, OrderMarkers.Kind.ATTACK, 100.2f);

        markers.prune(100.3f, 0.4f);

        assertEquals(3, markers.markers().size(), "three clicks, three marks");
        assertEquals(1f, markers.markers().get(0).x(), 0.001f, "in the order they were given");
    }

    @Test
    void spentMarksArePrunedAway() {
        var markers = new OrderMarkers();
        markers.add(0f, 0f, OrderMarkers.Kind.MOVE, 0f);
        markers.add(50f, 50f, OrderMarkers.Kind.ATTACK, 0.4f);

        markers.prune(0.5f, 0.4f);

        assertEquals(1, markers.markers().size(), "the older one has finished");
        assertEquals(OrderMarkers.Kind.ATTACK, markers.markers().get(0).kind());

        markers.prune(0.9f, 0.4f);
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
