package uz.dukeengine.core.partition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import uz.dukeengine.core.math.Coord3D;

/**
 * The lookers that show one player anything, sorted into squares of the ground by the ground their reach covers: a
 * point is asked of the few whose reach covers its square, not of every looker in the world — and the things a player
 * may be shown are looked for in those squares alone ({@link PartitionManager#standingWhereSeen}).
 *
 * <p>A point is seen when a looker whose square box covers the point's square has it within its reach, measured in
 * three dimensions — the question the snapshot always asked, answered the same.
 */
public final class Lookers {

    /** The side of a square, world units: two of the partition's buckets. */
    private static final float SQUARE = 2 * SpatialIndex.BUCKET;
    /** A looker whose box covers more squares than this is asked of every point rather than sorted into squares. */
    private static final long MOST_SQUARES = 4096;
    /** Beyond this, in world units, squares no longer line up with the partition's buckets. */
    private static final float FARTHEST = 1e9f;

    private record Looker(Coord3D at, float reach, int fromX, int toX, int fromY, int toY) {
        boolean sees(Coord3D point) {
            return at.distance(point) <= reach;
        }

        boolean covers(int x, int y) {
            return x >= fromX && x <= toX && y >= fromY && y <= toY;
        }
    }

    /** A square of the ground and the lookers whose box covers it. */
    private record Square(int x, int y, List<Looker> lookers) {
    }

    private final HashMap<Long, Square> squares = new HashMap<>();
    /** Lookers too wide or too far out to sort into squares, each asked of every point its box covers. */
    private final List<Looker> wide = new ArrayList<>();

    /** A looker at {@code at} that sees {@code reach} round it. */
    public void add(Coord3D at, float reach) {
        int fromX = square(at.x() - reach);
        int toX = square(at.x() + reach);
        int fromY = square(at.y() - reach);
        int toY = square(at.y() + reach);
        var looker = new Looker(at, reach, fromX, toX, fromY, toY);
        if (((long) toX - fromX + 1) * ((long) toY - fromY + 1) > MOST_SQUARES || !(Math.abs(at.x()) + reach < FARTHEST)
                || !(Math.abs(at.y()) + reach < FARTHEST)) {
            wide.add(looker);
            return;
        }
        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                int sx = x;
                int sy = y;
                squares.computeIfAbsent(key(x, y), k -> new Square(sx, sy, new ArrayList<>())).lookers().add(looker);
            }
        }
    }

    /** Whether some looker sees {@code point}. */
    public boolean see(Coord3D point) {
        int x = square(point.x());
        int y = square(point.y());
        var square = squares.get(key(x, y));
        if (square != null) {
            for (var looker : square.lookers()) {
                if (looker.sees(point)) {
                    return true;
                }
            }
        }
        for (var looker : wide) {
            if (looker.covers(x, y) && looker.sees(point)) {
                return true;
            }
        }
        return false;
    }

    /** Whether some looker could not be sorted into squares, and the things it sees may stand anywhere. */
    boolean anyWide() {
        return !wide.isEmpty();
    }

    /** How many squares the lookers' reach covers. */
    int squareCount() {
        return squares.size();
    }

    /** The partition's buckets the squares the lookers' reach covers are made of: each square, two buckets a side. */
    List<Long> buckets() {
        var buckets = new ArrayList<Long>(squares.size() * 4);
        for (var square : squares.values()) {
            long x = 2L * square.x();
            long y = 2L * square.y();
            buckets.add(SpatialIndex.key(x, y));
            buckets.add(SpatialIndex.key(x + 1, y));
            buckets.add(SpatialIndex.key(x, y + 1));
            buckets.add(SpatialIndex.key(x + 1, y + 1));
        }
        return buckets;
    }

    private static int square(float at) {
        return (int) Math.floor(at / SQUARE);
    }

    private static long key(int x, int y) {
        return SpatialIndex.key(x, y);
    }
}
