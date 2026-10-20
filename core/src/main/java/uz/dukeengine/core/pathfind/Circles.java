package uz.dukeengine.core.pathfind;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/** Circles on the ground in world units, by the cell of a grid each one's middle stands in. */
final class Circles {

    static final Circles NONE = new Circles(List.of(), new SceneryFootprint[0], null, 0f, 1, 1, 1f);

    final List<SceneryFootprint> list;
    /** The widest of them. */
    final float widest;
    /** The circles cell by cell, and where each cell's start, one past the last cell's end; null for none. */
    private final SceneryFootprint[] byCell;
    private final int[] start;
    private final int width;
    private final int height;
    private final float cellSize;

    private Circles(List<SceneryFootprint> list, SceneryFootprint[] byCell, int[] start, float widest, int width,
            int height, float cellSize) {
        this.list = list;
        this.byCell = byCell;
        this.start = start;
        this.widest = widest;
        this.width = width;
        this.height = height;
        this.cellSize = cellSize;
    }

    static Circles of(List<SceneryFootprint> circles, int width, int height, float cellSize) {
        if (circles.isEmpty()) {
            return NONE;
        }
        int cells = width * height;
        int[] start = new int[cells + 1];
        int[] cellOf = new int[circles.size()];
        float widest = 0f;
        for (int i = 0; i < circles.size(); i++) {
            var circle = circles.get(i);
            // One just off the map is kept at its edge, where what it reaches of the map is looked for.
            int cx = Math.clamp((long) Math.floor(circle.x() / cellSize), 0, width - 1);
            int cy = Math.clamp((long) Math.floor(circle.y() / cellSize), 0, height - 1);
            cellOf[i] = cy * width + cx;
            start[cellOf[i] + 1]++;
            widest = Math.max(widest, circle.radius());
        }
        for (int c = 0; c < cells; c++) {
            start[c + 1] += start[c];
        }
        var byCell = new SceneryFootprint[circles.size()];
        int[] next = Arrays.copyOf(start, cells);
        for (int i = 0; i < circles.size(); i++) {
            byCell[next[cellOf[i]]++] = circles.get(i);
        }
        return new Circles(List.copyOf(circles), byCell, start, widest, width, height, cellSize);
    }

    /** Whether a body of {@code clearance} radius standing at ({@code x}, {@code y}) is clear of all of them. */
    boolean clear(float x, float y, float clearance) {
        return !any(x, y, clearance, circle -> {
            float dx = x - circle.x();
            float dy = y - circle.y();
            float room = circle.radius() + clearance;
            return dx * dx + dy * dy < room * room;
        });
    }

    /** Whether any whose middle stands within {@code reach} plus the widest of them is one {@code test} takes. */
    boolean any(float x, float y, float reach, Predicate<SceneryFootprint> test) {
        if (start == null) {
            return false;
        }
        float far = reach + widest;
        int minX = Math.max(0, (int) Math.floor((x - far) / cellSize));
        int maxX = Math.min(width - 1, (int) Math.floor((x + far) / cellSize));
        int minY = Math.max(0, (int) Math.floor((y - far) / cellSize));
        int maxY = Math.min(height - 1, (int) Math.floor((y + far) / cellSize));
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cell = cy * width + minX, last = cy * width + maxX; cell <= last; cell++) {
                for (int i = start[cell]; i < start[cell + 1]; i++) {
                    if (test.test(byCell[i])) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
