package uz.dukeengine.core.pathfind;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import uz.dukeengine.core.math.Coord3D;

/**
 * The {@link Deck}s laid over a grid's ground, the n-th on floor n, and the ground cells they stand too low over for
 * the ground's walkers — see {@link PathGrid#addDeck}.
 */
final class Decks {

    // Laid on the simulation thread, read by a client's pointer on its own: a list either can walk without the other.
    private final List<Deck> laid = new CopyOnWriteArrayList<>();
    /** How high a deck must stand over the ground for the ground under it to stay open: the reference's 10. */
    float clearance = 10f;
    /** For each ground cell, how many open decks stand too low over it for it to be walked; null with no deck. */
    private int[] closedUnder;

    void add(Deck deck) {
        laid.add(deck);
    }

    /** The floor the next deck laid stands on. */
    int nextFloor() {
        return laid.size() + 1;
    }

    /** The deck on {@code floor}, or null. */
    Deck on(int floor) {
        return floor >= 1 && floor <= laid.size() ? laid.get(floor - 1) : null;
    }

    List<Deck> all() {
        return Collections.unmodifiableList(laid);
    }

    boolean any() {
        return !laid.isEmpty();
    }

    /** Whether an open deck stands too low over ground cell {@code index} for it to be walked. */
    boolean closes(int index) {
        return closedUnder != null && closedUnder[index] > 0;
    }

    /** The ground under the open decks of {@code grid} worked out again. */
    void layClosed(PathGrid grid) {
        float cellSize = grid.getCellSize();
        int width = grid.getWidth();
        var closed = new int[width * grid.getHeight()];
        for (var deck : laid) {
            if (!deck.isOpen()) {
                continue;
            }
            for (int cy = deck.minY(); cy <= deck.maxY(); cy++) {
                for (int cx = deck.minX(); cx <= deck.maxX(); cx++) {
                    if (!deck.walkable(cx, cy)) {
                        continue;
                    }
                    float over = deck.heightAt((cx + 0.5f) * cellSize, (cy + 0.5f) * cellSize)
                            - grid.groundHeight(cx, cy);
                    if (over < clearance) {
                        closed[cy * width + cx]++;
                    }
                }
            }
        }
        closedUnder = closed;
    }

    /** See {@link PathGrid#enters}. */
    boolean enters(PathGrid grid, int floor, int onto, int fromX, int fromY, int toX, int toY) {
        if (floor == onto || floor != 0 && onto != 0 || !grid.inBounds(fromX, fromY) || !grid.inBounds(toX, toY)) {
            return false;
        }
        var deck = on(floor == 0 ? onto : floor);
        if (deck == null || !deck.isOpen() || grid.isBlocked(floor == 0 ? fromX : toX, floor == 0 ? fromY : toY)) {
            return false;
        }
        int ground = floor == 0 ? grid.index(fromX, fromY) : grid.index(toX, toY);
        int onDeck = floor == 0 ? grid.index(toX, toY) : grid.index(fromX, fromY);
        for (var entry : deck.entries()) {
            if (entry[0] == ground && entry[1] == onDeck) {
                return true;
            }
        }
        return false;
    }

    /** See {@link PathGrid#floorAt}. */
    int floorAt(PathGrid grid, Coord3D at) {
        int floor = 0;
        float nearest = Math.abs(at.z() - grid.groundHeight(at));
        for (var deck : laid) {
            if (!deck.isOpen() || !deck.walkable(grid.toCellX(at), grid.toCellY(at))) {
                continue;
            }
            float away = Math.abs(at.z() - deck.heightAt(at.x(), at.y()));
            if (away < nearest) {
                nearest = away;
                floor = deck.floor();
            }
        }
        return floor;
    }
}
