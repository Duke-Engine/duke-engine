package uz.dukeengine.core.pathfind;

import java.util.ArrayList;
import java.util.List;

/**
 * The relief of a map: a whole number of height steps at every corner of every cell, ported from SAGE's
 * {@code WorldHeightMap} — one value per vertex, so a map of {@code w × h} cells has {@code (w+1) × (h+1)} of
 * them, and the ground between them is the two triangles SAGE splits each cell into.
 *
 * <p>Integers throughout. A height is a count of steps, a place inside a cell a count of 256ths of it, and the
 * height there is worked out in whole numbers: two machines agree on it to the last bit, and nothing in it can
 * drift by a rounding. Only the answer becomes a length, once, at the edge ({@link #lengthOf}).
 *
 * <p>A cell whose corners differ by {@link #CLIFF_STEPS} or more is a cliff: too steep to walk onto, as SAGE's
 * pathfinder refuses it.
 */
public final class HeightMap {

    /**
     * Height steps to a cell's width: SAGE's {@code MAP_HEIGHT_SCALE = MAP_XY_FACTOR / 16.0f}
     * ({@code MapObject.h}) — a step is 0.625 of a 10-unit cell.
     */
    public static final int STEPS_PER_CELL = 16;

    /**
     * How far apart a cell's corners may be before it is a cliff: SAGE's {@code PATHFIND_CLIFF_SLOPE_LIMIT_F = 9.8}
     * ({@code WorldHeightMap.cpp}), which at 0.625 a step is crossed at 16 steps — a rise of a whole cell across one.
     */
    public static final int CLIFF_STEPS = 16;

    /** A cell's width in the fractions a place inside it is counted in. */
    public static final int SUBCELL = 256;

    private final int columns;
    private final int rows;
    private final int[] steps;

    /** [steps] row by row: {@code columns} corners across, {@code rows} down. */
    public HeightMap(int columns, int rows, int[] steps) {
        if (columns < 2 || rows < 2 || steps.length != columns * rows) {
            throw new IllegalArgumentException("a relief of " + columns + " by " + rows + " corners holds "
                    + columns * rows + " heights, not " + steps.length);
        }
        this.columns = columns;
        this.rows = rows;
        this.steps = steps.clone();
    }

    /** Rows of whole numbers, one per corner, as a map file writes them: {@code ["0 0 1", "0 1 2"]}. */
    public static HeightMap parse(List<String> written) {
        if (written.size() < 2) {
            throw new IllegalArgumentException("a relief is at least two rows of corners, not " + written.size());
        }
        var values = new ArrayList<int[]>();
        for (var row : written) {
            var words = row.strip().split("\\s+");
            var numbers = new int[words.length];
            for (int i = 0; i < words.length; i++) {
                try {
                    numbers[i] = Integer.parseInt(words[i]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("a relief row is whole numbers: '" + row.strip() + "'", e);
                }
            }
            if (!values.isEmpty() && numbers.length != values.getFirst().length) {
                throw new IllegalArgumentException("every relief row has " + values.getFirst().length
                        + " corners; row " + (values.size() + 1) + " has " + numbers.length);
            }
            values.add(numbers);
        }
        int columns = values.getFirst().length;
        var steps = new int[columns * values.size()];
        for (int row = 0; row < values.size(); row++) {
            System.arraycopy(values.get(row), 0, steps, row * columns, columns);
        }
        return new HeightMap(columns, values.size(), steps);
    }

    /** As a map file writes it: a row of corners a line. */
    public List<String> written() {
        var written = new ArrayList<String>(rows);
        for (int row = 0; row < rows; row++) {
            var line = new StringBuilder();
            for (int column = 0; column < columns; column++) {
                if (column > 0) {
                    line.append(' ');
                }
                line.append(steps[row * columns + column]);
            }
            written.add(line.toString());
        }
        return written;
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    /** The steps at a corner; one off the relief reads as the nearest edge, as the ground there would. */
    public int at(int column, int row) {
        int x = Math.clamp(column, 0, columns - 1);
        int y = Math.clamp(row, 0, rows - 1);
        return steps[y * columns + x];
    }

    /** Whether cell ({@code cx}, {@code cy}) is too steep to walk onto: SAGE's {@code setCellCliffFlagFromHeights}. */
    public boolean isCliff(int cx, int cy) {
        int a = at(cx, cy);
        int b = at(cx + 1, cy);
        int c = at(cx, cy + 1);
        int d = at(cx + 1, cy + 1);
        return Math.max(Math.max(a, b), Math.max(c, d)) - Math.min(Math.min(a, b), Math.min(c, d)) >= CLIFF_STEPS;
    }

    /**
     * The height at a place inside cell ({@code cx}, {@code cy}), {@code fx} and {@code fy} 256ths of the way across
     * it, in 256ths of a step: SAGE's {@code getHeightMapHeight}, the cell split along its corner-to-corner diagonal
     * from ({@code cx}, {@code cy}) to ({@code cx+1}, {@code cy+1}) and each half a flat triangle.
     */
    public int fixedAt(int cx, int cy, int fx, int fy) {
        int p0 = at(cx, cy);
        int p2 = at(cx + 1, cy + 1);
        if (fy > fx) {
            int p3 = at(cx, cy + 1);
            return p3 * SUBCELL + (SUBCELL - fy) * (p0 - p3) + fx * (p2 - p3);
        }
        int p1 = at(cx + 1, cy);
        return p1 * SUBCELL + fy * (p2 - p1) + (SUBCELL - fx) * (p0 - p1);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HeightMap relief && relief.columns == columns && relief.rows == rows
                && java.util.Arrays.equals(relief.steps, steps);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * columns + rows) + java.util.Arrays.hashCode(steps);
    }

    @Override
    public String toString() {
        return "HeightMap" + written();
    }

    /**
     * A height of {@link #fixedAt} as a length, for a grid of cells [cellSize] wide: the one place the integers
     * become a world distance.
     */
    public static float lengthOf(int fixed, float cellSize) {
        return fixed * cellSize / (STEPS_PER_CELL * SUBCELL);
    }
}
