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

    /**
     * Read a height layer over a grid that already has its walls: which floor
     * each cell stands on, and which cells link one floor to the next.
     *
     * <pre>{@code
     * ..........
     * ..111/....
     * ..111.....
     * ..........
     * }</pre>
     *
     * <ul>
     *   <li>{@code 0}–{@code 9} — floor at that level. {@code .} is level 0, so a
     *       map written before height existed reads as the flat map it is.</li>
     *   <li>{@code /} — a ramp: a cell that links its floor to the one above.
     *       It stands at the <em>lowest</em> level of the cells orthogonally
     *       around it, which is the foot of the stair — so the cell above it is
     *       reached by climbing rather than by stepping.</li>
     *   <li>{@code #}, {@code X}, {@code x}, space — stone, and stone has no
     *       floor. Left alone.</li>
     * </ul>
     *
     * <p>Anything else throws. A level map is a picture of a building and a
     * character nobody recognises in the middle of one is a mistake worth
     * hearing about — quietly reading it as level 0 would be a room that is
     * mysteriously on the ground floor.
     *
     * <p>Separate from {@link #fromText} rather than folded into it because the
     * two layers answer different questions and a map may want only the first.
     * The same text can serve as both.
     */
    public static void levels(PathGrid grid, String map) {
        var lines = map.strip().split("\n");
        for (int cy = 0; cy < lines.length; cy++) {
            var line = lines[cy];
            for (int cx = 0; cx < line.length(); cx++) {
                char c = line.charAt(cx);
                if (c >= '0' && c <= '9') {
                    grid.setLevel(cx, cy, c - '0');
                } else if (c == '.') {
                    grid.setLevel(cx, cy, 0);
                } else if (c != '#' && c != 'X' && c != 'x' && c != ' ' && c != '/') {
                    throw new IllegalArgumentException(
                            "unknown character '" + c + "' in the level map at " + cx + "," + cy);
                }
            }
        }
        // Ramps last: a ramp stands at the foot of what it climbs, and it can
        // only know that once the floors around it have been read.
        for (int cy = 0; cy < lines.length; cy++) {
            var line = lines[cy];
            for (int cx = 0; cx < line.length(); cx++) {
                if (line.charAt(cx) == '/') {
                    grid.setRamp(cx, cy, true);
                    grid.setLevel(cx, cy, lowestNeighbour(grid, cx, cy));
                }
            }
        }
    }

    private static int lowestNeighbour(PathGrid grid, int cx, int cy) {
        int lowest = Integer.MAX_VALUE;
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (var step : around) {
            int x = cx + step[0];
            int y = cy + step[1];
            if (grid.inBounds(x, y) && !grid.isTerrainBlocked(x, y)) {
                lowest = Math.min(lowest, grid.level(x, y));
            }
        }
        return lowest == Integer.MAX_VALUE ? 0 : lowest;
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }
}
