package uz.duke.dungeon;

import java.util.ArrayList;
import java.util.List;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.dungeon.GeneratedDungeon.Placement;
import uz.duke.dungeon.GeneratedDungeon.Room;

/**
 * Draws a dungeon from a seed: a handful of rooms joined by corridors, with the
 * hero in one and the skeletons scattered through the rest.
 *
 * <p>The one property that matters more than any other is that <b>every room can
 * be reached</b> — a skeleton walled off in an island room is a run the player
 * cannot finish. This is guaranteed by construction rather than hoped for: each
 * room after the first is joined by a corridor to the one placed before it, so
 * the rooms form a spanning tree and the whole dungeon is one connected space.
 * No search, no retry, no "is it connected?" check that could pass by luck.
 *
 * <p>Everything is drawn with a {@link DeterministicRng} in a fixed order, so a
 * seed names exactly one dungeon. Room placement is the only part that can fail
 * to progress (a room may land on another and be rejected); it is bounded by an
 * attempt count and simply stops with however many rooms fit, never below the
 * floor of {@value #MIN_ROOMS}.
 */
public final class DungeonGenerator {

    /** Cells across and down. Room-sized rectangles need room to spread out. */
    static final int WIDTH = 64;
    static final int HEIGHT = 44;

    static final int MIN_ROOMS = 5;
    static final int MAX_ROOMS = 8;
    private static final int MIN_ROOM_SIZE = 5;
    private static final int MAX_ROOM_SIZE = 10;
    private static final int ROOM_GAP = 1; // stone kept between rooms so walls read as walls
    private static final int PLACEMENT_ATTEMPTS = 400;

    private static final int MIN_SKELETONS_PER_ROOM = 1;
    private static final int MAX_SKELETONS_PER_ROOM = 3;

    private static final char STONE = '#';
    private static final char FLOOR = '.';

    private DungeonGenerator() {
    }

    public static GeneratedDungeon generate(long seed) {
        var rng = new DeterministicRng(seed);

        var cells = new char[HEIGHT][WIDTH];
        for (var row : cells) {
            java.util.Arrays.fill(row, STONE);
        }

        var rooms = placeRooms(rng);
        for (var room : rooms) {
            carveRoom(cells, room);
        }
        // Join each room to the previous one — a spanning chain, so all connected.
        for (int i = 1; i < rooms.size(); i++) {
            carveCorridor(cells, rng, rooms.get(i - 1), rooms.get(i));
        }

        var hero = worldCenter(rooms.get(0).centerCellX(), rooms.get(0).centerCellY());
        var skeletons = placeSkeletons(rng, rooms);

        return new GeneratedDungeon(render(cells), hero, skeletons, List.copyOf(rooms));
    }

    private static List<Room> placeRooms(DeterministicRng rng) {
        int target = rng.nextInt(MIN_ROOMS, MAX_ROOMS);
        var rooms = new ArrayList<Room>();
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS && rooms.size() < target; attempt++) {
            int w = rng.nextInt(MIN_ROOM_SIZE, MAX_ROOM_SIZE);
            int h = rng.nextInt(MIN_ROOM_SIZE, MAX_ROOM_SIZE);
            // Keep a one-cell stone border so a room never touches the map edge.
            int x = rng.nextInt(1, WIDTH - w - 2);
            int y = rng.nextInt(1, HEIGHT - h - 2);
            var room = new Room(x, y, w, h);
            if (!overlapsAny(room, rooms)) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    private static boolean overlapsAny(Room room, List<Room> placed) {
        for (var other : placed) {
            // Expand one room by the gap and test plain rectangle intersection.
            boolean apart = room.x() - ROOM_GAP >= other.x() + other.w()
                    || other.x() - ROOM_GAP >= room.x() + room.w()
                    || room.y() - ROOM_GAP >= other.y() + other.h()
                    || other.y() - ROOM_GAP >= room.y() + room.h();
            if (!apart) {
                return true;
            }
        }
        return false;
    }

    private static void carveRoom(char[][] cells, Room room) {
        for (int y = room.y(); y < room.y() + room.h(); y++) {
            for (int x = room.x(); x < room.x() + room.w(); x++) {
                cells[y][x] = FLOOR;
            }
        }
    }

    private static void carveCorridor(char[][] cells, DeterministicRng rng, Room from, Room to) {
        int x1 = from.centerCellX();
        int y1 = from.centerCellY();
        int x2 = to.centerCellX();
        int y2 = to.centerCellY();
        // An L-bend: which leg comes first is a coin-flip, so corridors vary.
        if (rng.nextBoolean()) {
            carveHorizontal(cells, y1, x1, x2);
            carveVertical(cells, x2, y1, y2);
        } else {
            carveVertical(cells, x1, y1, y2);
            carveHorizontal(cells, y2, x1, x2);
        }
    }

    private static void carveHorizontal(char[][] cells, int y, int xa, int xb) {
        for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++) {
            cells[y][x] = FLOOR;
        }
    }

    private static void carveVertical(char[][] cells, int x, int ya, int yb) {
        for (int y = Math.min(ya, yb); y <= Math.max(ya, yb); y++) {
            cells[y][x] = FLOOR;
        }
    }

    private static List<Placement> placeSkeletons(DeterministicRng rng, List<Room> rooms) {
        var skeletons = new ArrayList<Placement>();
        for (int i = 1; i < rooms.size(); i++) {
            var room = rooms.get(i);
            int count = rng.nextInt(MIN_SKELETONS_PER_ROOM, MAX_SKELETONS_PER_ROOM);
            var used = new ArrayList<int[]>();
            for (int n = 0; n < count; n++) {
                // Interior cells only — always floor, never on the room's wall line.
                int cx = rng.nextInt(room.x() + 1, room.x() + room.w() - 2);
                int cy = rng.nextInt(room.y() + 1, room.y() + room.h() - 2);
                if (occupied(used, cx, cy)) {
                    continue; // one skeleton per cell, so they never spawn overlapping
                }
                used.add(new int[] {cx, cy});
                skeletons.add(worldCenter(cx, cy));
            }
        }
        return skeletons;
    }

    private static boolean occupied(List<int[]> used, int cx, int cy) {
        for (var cell : used) {
            if (cell[0] == cx && cell[1] == cy) {
                return true;
            }
        }
        return false;
    }

    private static Placement worldCenter(int cx, int cy) {
        float cell = PathGrid.DEFAULT_CELL_SIZE;
        return new Placement((cx + 0.5f) * cell, (cy + 0.5f) * cell);
    }

    private static String render(char[][] cells) {
        var text = new StringBuilder(HEIGHT * (WIDTH + 1));
        for (var row : cells) {
            text.append(row).append('\n');
        }
        return text.toString();
    }
}
