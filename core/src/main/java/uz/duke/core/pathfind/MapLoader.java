package uz.duke.core.pathfind;

/**
 * Builds a {@link PathGrid} from a simple text map.
 *
 * <p>A stand-in for SAGE's binary terrain/{@code TerrainLogic} until real map
 * loading exists, this turns an ASCII grid into passability data so navigation
 * can be authored and tested as plain text:
 * <pre>{@code
 * ..........
 * ....##....
 * ....##....
 * ..........
 * }</pre>
 * {@code #} (or {@code X}) marks a blocked cell; any other non-space character is
 * clear. The first line is row {@code cy = 0}; columns are {@code cx}. Width is
 * the longest line; short lines are padded with clear cells.
 */
public final class MapLoader {

    private MapLoader() {
    }

    public static PathGrid fromText(String map) {
        return fromText(map, PathGrid.DEFAULT_CELL_SIZE);
    }

    public static PathGrid fromText(String map, float cellSize) {
        var lines = map.strip().split("\n");
        int height = lines.length;
        int width = 0;
        for (var line : lines) {
            width = Math.max(width, stripTrailing(line).length());
        }
        if (width == 0 || height == 0) {
            throw new IllegalArgumentException("map is empty");
        }

        var grid = new PathGrid(width, height, cellSize);
        for (int cy = 0; cy < height; cy++) {
            var line = lines[cy];
            for (int cx = 0; cx < line.length(); cx++) {
                char c = line.charAt(cx);
                if (c == '#' || c == 'X' || c == 'x') {
                    grid.setBlocked(cx, cy, true);
                }
            }
        }
        return grid;
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }
}
