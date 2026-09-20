package uz.dukeengine.dungeon.gen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.GeneratedDungeon.Room;

/**
 * How high each room stands, and where the stairs between them are.
 *
 * <p>A flat dungeon is connected by construction: the corridors form a spanning
 * tree, so every room hangs off the first one and no search is needed to prove
 * it. Height can take that away. A corridor that changes storey needs room for a
 * stair; a corridor that crosses a third room on its way passes through whatever
 * storey <em>that</em> room stands on, and if the two do not meet, the corridor is
 * cut in half by a wall that nobody built.
 *
 * <p>So the guarantee is kept the way it has to be kept once it can fail: the
 * dungeon is walked, with the engine's own rule for what counts as a step, and
 * any room that cannot be reached has its climb taken away and the whole thing
 * is drawn again. That terminates because every repair flattens at least one
 * room toward the entrance, and a dungeon flat everywhere is the dungeon this
 * game had before height — connected by construction. If even that fails,
 * something is wrong that this class does not understand, and it says so rather
 * than handing back a floor with a room nobody can enter.
 */
final class Storeys {


    private static final char STONE = '#';
    private static final char RAMP = '/';

    /** The finished level map, and how high each room ended up. */
    record Result(char[][] map, List<Integer> perRoom) {
    }

    private final List<Room> rooms;
    private final List<Corridor> corridors;
    private final DungeonSettings settings;
    private final char[][] cells;
    private final int[] storey;
    private final int[] parent;

    private Storeys(List<Room> rooms, List<Corridor> corridors, DungeonSettings settings,
            char[][] cells) {
        this.rooms = rooms;
        this.corridors = corridors;
        this.settings = settings;
        this.cells = cells;
        this.storey = new int[rooms.size()];
        this.parent = new int[rooms.size()];
        Arrays.fill(parent, -1);
    }

    static Result of(DeterministicRng rng, List<Room> rooms, List<Corridor> corridors,
            int bossRoom, DungeonSettings settings, char[][] cells) {
        var storeys = new Storeys(rooms, corridors, settings, cells);
        storeys.assign(rng, bossRoom);
        return storeys.settle();
    }

    // ---- deciding ----

    /**
     * Walk the tree outward from the entrance, deciding at each corridor whether
     * the next room is a storey up, a storey down, or level.
     *
     * <p>The corridors come in the order they were carved, which is the order the
     * tree grew: the room a corridor comes <em>from</em> always has its storey
     * already. One draw per corridor, in that order, so a seed names one dungeon.
     */
    private void assign(DeterministicRng rng, int bossRoom) {
        storey[0] = settings.entranceStorey();
        for (var corridor : corridors) {
            parent[corridor.to()] = corridor.from();
            int here = storey[corridor.from()];
            int drawn = here;
            if (rng.nextInt(100) < settings.storeyChangePercent()) {
                drawn = Math.clamp(here + (rng.nextBoolean() ? 1 : -1), 0, settings.maxStorey());
            }
            // The boss's room is where the floor ends, so it is worth climbing to
            // — decided here rather than afterwards, because rooms further along
            // the tree hang off it and would be planned against the old answer.
            // A short corridor still cuts the climb down: better a boss one
            // storey up than a boss behind a wall.
            int wanted = corridor.to() == bossRoom ? settings.bossStorey() : drawn;
            storey[corridor.to()] = climbable(corridor, here, wanted);
        }
    }

    /**
     * The nearest storey to {@code wanted} that this corridor is long enough to
     * reach.
     *
     * <p>The corridor runs flat, so what it has to hold is a staircase at each
     * end: one down from the room it comes from, one up into the room it is
     * joining. Each storey of each of them costs a run of stair and a landing,
     * and there is only so much corridor. Better a room one storey up than a
     * room with half a staircase leading to it.
     */
    private int climbable(Corridor corridor, int fromRoomStorey, int wanted) {
        int flat = settings.entranceStorey();
        var mouth = stretch(corridor, false);
        var behind = stretch(corridor, true);
        // Usually one and the same stretch, and then the staircase down from the
        // room behind is cut out of the very cells this one needs.
        int theirs = mouth[0] == behind[0] ? Math.abs(fromRoomStorey - flat) : 0;
        int room = mouth[1] - mouth[0] + 1 - stairCost(theirs);

        int mine = Math.abs(wanted - flat);
        while (mine > 0 && stairCost(mine) > room) {
            mine--;
        }
        return flat + Integer.signum(wanted - flat) * mine;
    }

    /**
     * How many cells of corridor a staircase of this many storeys eats.
     *
     * <p>A run of stair per storey, and a landing between each pair of runs —
     * but not after the last one, because the corridor it comes out onto is the
     * landing. One cell either way is the difference between a dungeon with
     * height in it and one that gives up on every climb: the corridor between
     * two rooms is measured from their centres, so most of it is room.
     */
    private int stairCost(int storeys) {
        return storeys == 0 ? 0 : storeys * (settings.stairLength() + 1) - 1;
    }

    /**
     * The run of actual corridor at one end of the spine, as {@code {first, last}}.
     *
     * <p>Not the spine: that is drawn centre to centre, so half a room stands at
     * each end of it. A corridor of twelve cells between two nine-cell rooms is
     * three cells of corridor, and a staircase measured against the twelve is a
     * staircase that stops in mid-air.
     */
    private int[] stretch(Corridor corridor, boolean behind) {
        var spine = corridor.spine();
        int step = behind ? 1 : -1;
        int at = behind ? 0 : spine.size() - 1;
        while (at >= 0 && at < spine.size() && insideARoom(spine.get(at))) {
            at += step;
        }
        if (at < 0 || at >= spine.size()) {
            return new int[] {0, -1}; // no corridor at all: the rooms touch
        }
        int end = at;
        while (end + step >= 0 && end + step < spine.size()
                && !insideARoom(spine.get(end + step))) {
            end += step;
        }
        return behind ? new int[] {at, end} : new int[] {end, at};
    }

    // ---- drawing, checking, and repairing ----

    private Result settle() {
        for (int attempt = 0; attempt <= rooms.size(); attempt++) {
            var map = paint();
            var stranded = strandedRooms(map);
            if (stranded.isEmpty()) {
                return new Result(map, asList(storey));
            }
            if (!flatten(stranded.get(0))) {
                break; // nothing left to give up, and it is still cut in two
            }
        }
        // Everything on the entrance storey: the dungeon this game had before it
        // had height, which is connected because the corridors are a spanning tree.
        Arrays.fill(storey, settings.entranceStorey());
        var map = paint();
        var stranded = strandedRooms(map);
        if (!stranded.isEmpty()) {
            throw new IllegalStateException(
                    "a dungeon on one storey still cannot reach room " + stranded.get(0)
                            + " of " + rooms.size() + " — this is not a height problem");
        }
        return new Result(map, asList(storey));
    }

    /**
     * Give up the climb into a stranded room, and into every room between it and
     * the entrance.
     *
     * @return whether anything actually changed, so a caller can stop rather than
     *     redraw the same map for ever
     */
    private boolean flatten(int stranded) {
        boolean changed = false;
        for (int room = stranded; room > 0 && parent[room] >= 0; room = parent[room]) {
            if (storey[room] != storey[parent[room]]) {
                storey[room] = storey[parent[room]];
                changed = true;
            }
        }
        return changed;
    }

    /** Every room the hero could not walk to, lowest first. */
    private List<Integer> strandedRooms(char[][] map) {
        var grid = MapLoader.fromText(render(cells));
        MapLoader.levels(grid, render(map));

        var reached = new boolean[grid.getHeight() * grid.getWidth()];
        var queue = new java.util.ArrayDeque<int[]>();
        int startX = rooms.get(0).centerCellX();
        int startY = rooms.get(0).centerCellY();
        reached[startY * grid.getWidth() + startX] = true;
        queue.add(new int[] {startX, startY});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            var at = queue.poll();
            for (var step : steps) {
                int x = at[0] + step[0];
                int y = at[1] + step[1];
                if (!grid.inBounds(x, y) || reached[y * grid.getWidth() + x]
                        || !grid.canStep(at[0], at[1], x, y)) {
                    continue;
                }
                reached[y * grid.getWidth() + x] = true;
                queue.add(new int[] {x, y});
            }
        }

        var stranded = new ArrayList<Integer>();
        for (int room = 1; room < rooms.size(); room++) {
            int cx = rooms.get(room).centerCellX();
            int cy = rooms.get(room).centerCellY();
            if (!reached[cy * grid.getWidth() + cx]) {
                stranded.add(room);
            }
        }
        return stranded;
    }

    // ---- the map itself ----

    private char[][] paint() {
        var map = new char[cells.length][cells[0].length];
        for (var row : map) {
            Arrays.fill(row, STONE);
        }
        for (int room = 0; room < rooms.size(); room++) {
            fillRoom(map, rooms.get(room), digit(storey[room]));
        }
        // Every corridor is laid flat before any stair is cut, and not one
        // corridor at a time. Corridors share cells wherever they cross, and a
        // second corridor's flat stretch drawn over a first one's landing turns
        // its staircase into a step of two storeys — which is a wall, at the foot
        // of the stairs, in a corridor that looks perfectly open on the map.
        for (var corridor : corridors) {
            for (var at : corridor.spine()) {
                paintBand(map, at, corridor.width(), digit(settings.entranceStorey()));
            }
        }
        for (var corridor : corridors) {
            cutStairs(map, corridor);
        }
        return map;
    }

    private static void fillRoom(char[][] map, Room room, char storey) {
        for (int y = room.y(); y < room.y() + room.h(); y++) {
            for (int x = room.x(); x < room.x() + room.w(); x++) {
                map[y][x] = storey;
            }
        }
    }

    /**
     * Draw a corridor: flat along its length, with a staircase where it meets a
     * room standing higher than it.
     *
     * <p>Every corridor runs at the same storey — the one the way in stands on —
     * and it is the rooms that rise. That is not a look; it is what keeps the
     * dungeon in one piece. Corridors cross each other constantly, and two
     * corridors that meet at different storeys are a wall across both of them,
     * built by nobody and impossible to see in the map. With every corridor on one
     * storey a crossing is just a crossing.
     *
     * <p>So the climb happens at the doorway, in the corridor cells just outside
     * the room: walking out of a room on the second storey you come down a step,
     * cross a landing, come down another, and you are in the corridor.
     */
    private void cutStairs(char[][] map, Corridor corridor) {
        var spine = corridor.spine();
        // Each stretch of corridor between rooms climbs at whichever of its ends
        // has a room above it. A corridor that crosses a third room has more than
        // one such stretch, and each is treated the same way.
        int start = -1;
        for (int i = 0; i <= spine.size(); i++) {
            boolean corridorHere = i < spine.size() && !insideARoom(spine.get(i));
            if (corridorHere && start < 0) {
                start = i;
            } else if (!corridorHere && start >= 0) {
                layStairs(map, corridor, start, i - 1, roomAt(spine, start - 1), true);
                layStairs(map, corridor, start, i - 1, roomAt(spine, i), false);
                start = -1;
            }
        }
    }

    /**
     * Cut steps into one end of a stretch of corridor, so it meets the room there.
     *
     * <p>Walking outward from the room the storeys come down one at a time: a run
     * of stair, then a landing, then the next run — the landing is what keeps two
     * runs from reading as one long ramp, to this code and to the eye.
     *
     * @param atStart whether the room is off the {@code from} end of the stretch
     */
    private void layStairs(char[][] map, Corridor corridor, int from, int to, int room,
            boolean atStart) {
        if (room < 0) {
            return;
        }
        int flat = settings.entranceStorey();
        int climb = Math.abs(storey[room] - flat);
        int step = Integer.signum(storey[room] - flat);
        int run = settings.stairLength();
        var spine = corridor.spine();

        int cell = atStart ? from : to;
        int outward = atStart ? 1 : -1;
        for (int down = 0; down < climb; down++) {
            for (int i = 0; i < run; i++) {
                if (cell < from || cell > to) {
                    return; // the stretch ran out; the walk afterwards will say so
                }
                paintBand(map, spine.get(cell), corridor.width(), RAMP);
                cell += outward;
            }
            // A landing between one run and the next, so two runs do not read as
            // one long ramp — to this code or to the eye. After the last run
            // there is none: the corridor is the landing.
            if (down < climb - 1 && cell >= from && cell <= to) {
                paintBand(map, spine.get(cell), corridor.width(),
                        digit(storey[room] - step * (down + 1)));
                cell += outward;
            }
        }
    }

    /** Which room a spine cell stands in, or -1 for one out in a corridor. */
    private int roomAt(List<int[]> spine, int index) {
        if (index < 0 || index >= spine.size()) {
            return -1;
        }
        var at = spine.get(index);
        for (int room = 0; room < rooms.size(); room++) {
            var shape = rooms.get(room);
            if (at[0] >= shape.x() && at[0] < shape.x() + shape.w()
                    && at[1] >= shape.y() && at[1] < shape.y() + shape.h()) {
                return room;
            }
        }
        return -1;
    }

    private boolean insideARoom(int[] at) {
        return insideARoom(at[0], at[1]);
    }

    /**
     * Paint one cell of the corridor and the width either side of it.
     *
     * <p>A stair as wide as the corridor it sits in, because a stair narrower
     * than the corridor is a bottleneck and a bottleneck is where the largest
     * creature wedges — the same reason the corridor is two cells wide.
     *
     * <p>Rooms are left alone. A corridor that crosses one is passing through a
     * place that already has a storey, and a strip of its own driven through the
     * middle would be a trench across the floor.
     */
    private void paintBand(char[][] map, int[] at, int width, char storeyChar) {
        boolean alongX = at[2] == 1;
        for (int offset = -(width - 1) / 2; offset <= width / 2; offset++) {
            int x = alongX ? at[0] : at[0] + offset;
            int y = alongX ? at[1] + offset : at[1];
            if (y <= 0 || y >= map.length - 1 || x <= 0 || x >= map[0].length - 1) {
                continue; // never breach the map's own border
            }
            if (cells[y][x] == STONE || insideARoom(x, y)) {
                continue;
            }
            if (map[y][x] == RAMP && storeyChar != RAMP) {
                continue; // another corridor's stair; half a stair is a bottleneck
            }
            map[y][x] = storeyChar;
        }
    }

    private boolean insideARoom(int x, int y) {
        for (var room : rooms) {
            if (x >= room.x() && x < room.x() + room.w()
                    && y >= room.y() && y < room.y() + room.h()) {
                return true;
            }
        }
        return false;
    }

    private static char digit(int storey) {
        return (char) ('0' + Math.clamp(storey, 0, 9));
    }

    private static List<Integer> asList(int[] values) {
        var list = new ArrayList<Integer>(values.length);
        for (var value : values) {
            list.add(value);
        }
        return List.copyOf(list);
    }

    private static String render(char[][] cells) {
        var text = new StringBuilder(cells.length * (cells[0].length + 1));
        for (var row : cells) {
            text.append(row).append('\n');
        }
        return text.toString();
    }

    /** Kept here so the walk and the engine agree on what a step is. */
    static PathGrid gridOf(String asciiMap, String levelMap, float storeyHeight) {
        var grid = MapLoader.fromText(asciiMap);
        MapLoader.levels(grid, levelMap);
        grid.setLevelHeight(storeyHeight);
        return grid;
    }
}
