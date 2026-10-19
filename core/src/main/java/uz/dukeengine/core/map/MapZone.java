package uz.dukeengine.core.map;

import java.util.ArrayList;
import java.util.List;

/**
 * A named place on a map, in cells — where a quest sends a hero, where a trigger listens, where people stand: {@code
 * name x y width height}, a rectangle from its top-left cell, or {@code name x y radius}, a circle round a cell's
 * middle. Zero {@code radius} is a rectangle. A record of its own type, so an editor finds a map's places by what they
 * are rather than by their shape (E22).
 */
public record MapZone(String name, int x, int y, int width, int height, int radius) {

    /** As a map writes it: {@code name x y radius} or {@code name x y width height}. */
    public static MapZone of(String written) {
        var words = written.strip().split("\s+");
        if (words.length != 4 && words.length != 5) {
            throw new IllegalArgumentException("'" + written
                    + "' is a name, then x y width height (a rectangle) or x y radius (a circle), in cells");
        }
        int x = Integer.parseInt(words[1]);
        int y = Integer.parseInt(words[2]);
        return words.length == 4 ? new MapZone(words[0], x, y, 0, 0, Integer.parseInt(words[3]))
                : new MapZone(words[0], x, y, Integer.parseInt(words[3]), Integer.parseInt(words[4]), 0);
    }

    /** Whether the world point ({@code px}, {@code py}) is in it, a cell being {@code cell} units across. */
    public boolean holds(float px, float py, float cell) {
        if (radius > 0) {
            float dx = px - (x + 0.5f) * cell;
            float dy = py - (y + 0.5f) * cell;
            return dx * dx + dy * dy <= radius * cell * radius * cell;
        }
        int cx = (int) Math.floor(px / cell);
        int cy = (int) Math.floor(py / cell);
        return cx >= x && cx < x + width && cy >= y && cy < y + height;
    }

    /** Its middle, in world units, a cell being {@code cell} across. */
    public float middleX(float cell) {
        return radius > 0 ? (x + 0.5f) * cell : (x + width / 2f) * cell;
    }

    public float middleY(float cell) {
        return radius > 0 ? (y + 0.5f) * cell : (y + height / 2f) * cell;
    }

    /** Its cells, {@code [x, y]} each, in reading order: the middle's first for a circle. Never empty. */
    public List<int[]> cells() {
        var cells = new ArrayList<int[]>();
        if (radius > 0) {
            cells.add(new int[] {x, y});
            for (int cy = y - radius; cy <= y + radius; cy++) {
                for (int cx = x - radius; cx <= x + radius; cx++) {
                    if ((cx != x || cy != y) && (cx - x) * (cx - x) + (cy - y) * (cy - y) <= radius * radius) {
                        cells.add(new int[] {cx, cy});
                    }
                }
            }
        } else {
            for (int cy = y; cy < y + Math.max(1, height); cy++) {
                for (int cx = x; cx < x + Math.max(1, width); cx++) {
                    cells.add(new int[] {cx, cy});
                }
            }
        }
        return cells;
    }

    /** As a map file writes it. */
    public String written() {
        return radius > 0 ? name + " " + x + " " + y + " " + radius
                : name + " " + x + " " + y + " " + width + " " + height;
    }
}
