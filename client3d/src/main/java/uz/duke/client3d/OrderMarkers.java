package uz.duke.client3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The brief flashes on the ground that say an order was heard.
 *
 * <p>Without one, a click into empty floor gives nothing back until a unit
 * happens to start walking, and a click that missed looks exactly like a click
 * that landed. The marker is the acknowledgement, which is why it appears where
 * the player clicked rather than where anything ends up.
 *
 * <p>One marker per order, not per unit: ordering six units to a spot is one
 * decision and should leave one mark, not six overlapping ones.
 *
 * <p>Purely presentational. Time arrives as an argument rather than being read
 * from a clock in here, which keeps the class testable and keeps the wall clock
 * out of everything but the render loop — the simulation neither knows nor cares
 * that any of this exists.
 */
final class OrderMarkers {

    /** What the order was, which is all the difference between the two colours. */
    enum Kind {
        MOVE,
        ATTACK
    }

    /** {@code bornAt} is a render-time reading, not a simulation frame. */
    record Marker(float x, float y, Kind kind, float bornAt) {
    }

    private final List<Marker> markers = new ArrayList<>();

    void add(float worldX, float worldY, Kind kind, float now) {
        markers.add(new Marker(worldX, worldY, kind, now));
    }

    /**
     * Forget markers that have finished.
     *
     * <p>How long one lasts is handed in rather than kept here, because it is
     * part of how the mark <em>looks</em> and that belongs to the game — see
     * {@link OrderMark}. This class is the list of what was ordered and where.
     */
    void prune(float now, float lifetimeSeconds) {
        markers.removeIf(marker -> now - marker.bornAt() >= lifetimeSeconds);
    }

    /** The markers still worth drawing. */
    List<Marker> markers() {
        return List.copyOf(markers);
    }

    /** Drop everything — a new world has no orders outstanding in it. */
    void clear() {
        markers.clear();
    }
}
