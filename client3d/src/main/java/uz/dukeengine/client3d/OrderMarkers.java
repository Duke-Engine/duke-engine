package uz.dukeengine.client3d;

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

    /** What the order was, which is all the difference between the colours. */
    enum Kind {
        /** Go there. Arrowheads on the floor, in the colour of going somewhere. */
        MOVE,
        /** Kill that one. A ring round the creature — see {@link AttackFlash}. */
        ATTACK,
        /**
         * Go there, and kill what you meet. The arrowheads of a walk in the colour
         * of an attack, because that is exactly what the order is: the player is
         * pointing at a piece of floor, so he is answered where he pointed, and
         * what he has asked for on the way there is a fight.
         */
        ATTACK_MOVE
    }

    /**
     * One order, where it was given and — when it was given to somebody — who to.
     *
     * <p>{@code unitId} is {@code NOBODY} for an order pointed at a piece of floor.
     * For one pointed at a creature it is that creature, because the answer has to
     * <em>follow</em> it: a ring left standing on the flagstone a skeleton was
     * on when the click landed marks a place nothing is any more, and the player
     * reads that as the order having gone somewhere else.
     *
     * <p>The id rather than the creature, because this side has a snapshot rather
     * than a world, and what is on the snapshot this frame is the only thing worth
     * drawing. {@code bornAt} is a render-time reading, not a simulation frame.
     */
    record Marker(float x, float y, int unitId, Kind kind, float bornAt) {
    }

    /** No creature: this order was given to a piece of floor. */
    static final int NOBODY = -1;

    private final List<Marker> markers = new ArrayList<>();

    void add(float worldX, float worldY, Kind kind, float now) {
        add(worldX, worldY, NOBODY, kind, now);
    }

    /** The same, for an order given to a creature the mark should follow. */
    void add(float worldX, float worldY, int unitId, Kind kind, float now) {
        markers.add(new Marker(worldX, worldY, unitId, kind, now));
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
