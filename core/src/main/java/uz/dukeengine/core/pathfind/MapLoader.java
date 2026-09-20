package uz.dukeengine.core.pathfind;

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
        var done = new boolean[grid.getHeight() * grid.getWidth()];
        for (int cy = 0; cy < lines.length; cy++) {
            var line = lines[cy];
            for (int cx = 0; cx < line.length(); cx++) {
                if (line.charAt(cx) == '/' && grid.inBounds(cx, cy)
                        && !done[cy * grid.getWidth() + cx]) {
                    settleStair(grid, lines, done, cx, cy);
                }
            }
        }
    }

    /**
     * Put a whole run of ramp cells on the floor it climbs from.
     *
     * <p>A stair is rarely one cell: it is as wide as the corridor it sits in and
     * as long as it takes to climb, and every cell of it belongs to the same
     * step. Asking each of them separately what is next to it does not work —
     * the cell in the middle of the run has ramps on every side and no floor to
     * take a number from. So the run is found first, and then the lowest floor
     * touching <em>any</em> of it is the floor all of it stands on.
     */
    private static void settleStair(PathGrid grid, String[] lines, boolean[] done,
            int fromX, int fromY) {
        var run = new java.util.ArrayList<int[]>();
        var queue = new java.util.ArrayDeque<int[]>();
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        queue.add(new int[] {fromX, fromY});
        done[fromY * grid.getWidth() + fromX] = true;
        int lowest = Integer.MAX_VALUE;

        while (!queue.isEmpty()) {
            var at = queue.poll();
            run.add(at);
            for (var step : around) {
                int x = at[0] + step[0];
                int y = at[1] + step[1];
                if (!grid.inBounds(x, y) || charAt(lines, x, y) == 0) {
                    continue;
                }
                if (charAt(lines, x, y) == '/') {
                    if (!done[y * grid.getWidth() + x]) {
                        done[y * grid.getWidth() + x] = true;
                        queue.add(new int[] {x, y});
                    }
                } else if (!grid.isTerrainBlocked(x, y)) {
                    lowest = Math.min(lowest, grid.level(x, y));
                }
            }
        }

        int foot = lowest == Integer.MAX_VALUE ? 0 : lowest;
        for (var cell : run) {
            grid.setRamp(cell[0], cell[1], true);
            grid.setLevel(cell[0], cell[1], foot);
        }
    }

    private static char charAt(String[] lines, int cx, int cy) {
        return cy < lines.length && cx < lines[cy].length() ? lines[cy].charAt(cx) : 0;
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ' ') {
            end--;
        }
        return s.substring(0, end);
    }
}
