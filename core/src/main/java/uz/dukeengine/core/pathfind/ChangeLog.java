package uz.dukeengine.core.pathfind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Where on a grid can be walked has changed, change by change — a cell blocked or opened, laid, raised, made a ramp or
 * a class, a circle standing in the way or gone — each told by the box of cells it changed, so whatever was worked out
 * from the grid catches up only where it must ({@link PathGrid#changesSince}).
 */
final class ChangeLog {

    /** How many changes are kept for a reader to catch up with; one further behind works out everything again. */
    private static final int MOST_CHANGES_KEPT = 4096;
    /** Cells changed in one laying past this many squares of the ground are told as one change everywhere. */
    private static final int MOST_SQUARES_TOLD = 256;
    /** The side of a square of the ground the cells a laying changed are told by. */
    private static final int SQUARE = 16;

    private final int width;
    private final int height;
    private final float cellSize;
    /** The latest changes, each {serial, minX, minY, maxX, maxY} in cells, oldest first. */
    private final ArrayDeque<int[]> changes = new ArrayDeque<>();
    /** How many changes to where can be walked the grid has seen. */
    private int serial;
    /** The last serial no longer kept: a reader behind it works out everything again. */
    private int forgottenUpTo;

    ChangeLog(int width, int height, float cellSize) {
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
    }

    /** The serial of the last change. */
    int serial() {
        return serial;
    }

    /** The boxes changed since change {@code since}, oldest first, or null where some are no longer kept. */
    List<int[]> since(int since) {
        if (since < forgottenUpTo) {
            return null;
        }
        var after = new ArrayList<int[]>();
        for (var change : changes) {
            if (change[0] > since) {
                after.add(new int[] {change[1], change[2], change[3], change[4]});
            }
        }
        return after;
    }

    void changed(int minX, int minY, int maxX, int maxY) {
        serial++;
        changes.addLast(new int[] {serial, minX, minY, maxX, maxY});
        if (changes.size() > MOST_CHANGES_KEPT) {
            while (changes.size() > MOST_CHANGES_KEPT / 2) {
                forgottenUpTo = changes.removeFirst()[0];
            }
        }
    }

    /** A cell changed: the steps into it, out of it and past its corner along with it. */
    void around(int cx, int cy) {
        changed(cx - 1, cy - 1, cx + 1, cy + 1);
    }

    void everywhere() {
        changed(-1, -1, width, height);
    }

    /** The cells {@code differs} names changed, told by the squares of the ground they fall in. */
    void cells(IntPredicate differs) {
        int squaresAcross = (width + SQUARE - 1) / SQUARE;
        var squares = new BitSet();
        for (int i = 0; i < width * height; i++) {
            if (differs.test(i)) {
                squares.set(((i / width) / SQUARE) * squaresAcross + (i % width) / SQUARE);
            }
        }
        if (squares.cardinality() > MOST_SQUARES_TOLD) {
            everywhere();
            return;
        }
        for (int square = squares.nextSetBit(0); square >= 0; square = squares.nextSetBit(square + 1)) {
            int x = (square % squaresAcross) * SQUARE;
            int y = (square / squaresAcross) * SQUARE;
            changed(x - 1, y - 1, x + SQUARE, y + SQUARE);
        }
    }

    /** The circles that came or went, each told by the cells its middle's reach covers. */
    void circles(List<SceneryFootprint> was, List<SceneryFootprint> now) {
        var before = new HashSet<>(was);
        var after = new HashSet<>(now);
        var moved = new ArrayList<SceneryFootprint>();
        was.stream().filter(circle -> !after.contains(circle)).forEach(moved::add);
        now.stream().filter(circle -> !before.contains(circle)).forEach(moved::add);
        if (moved.size() > MOST_SQUARES_TOLD) {
            everywhere();
            return;
        }
        for (var circle : moved) {
            changed((int) Math.floor((circle.x() - circle.radius()) / cellSize) - 1,
                    (int) Math.floor((circle.y() - circle.radius()) / cellSize) - 1,
                    (int) Math.floor((circle.x() + circle.radius()) / cellSize) + 1,
                    (int) Math.floor((circle.y() + circle.radius()) / cellSize) + 1);
        }
    }
}
