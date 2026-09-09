package uz.duke.dungeon.gen;

import java.util.ArrayList;
import java.util.List;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.content.MonsterKind;
import uz.duke.dungeon.gen.GeneratedDungeon.Link;
import uz.duke.dungeon.gen.GeneratedDungeon.Monster;
import uz.duke.dungeon.gen.GeneratedDungeon.Placement;
import uz.duke.dungeon.gen.GeneratedDungeon.Room;

/**
 * Draws a dungeon from a seed: a handful of rooms joined by corridors, with the
 * hero in one and the skeletons scattered through the rest.
 *
 * <p>The one property that matters more than any other is that <b>every room can
 * be reached</b> — a skeleton walled off in an island room is a run the player
 * cannot finish. This is guaranteed by construction rather than hoped for: each
 * room after the first is joined by a corridor to a room placed <em>before</em>
 * it, so the rooms form a spanning tree and the whole dungeon is one connected
 * space. No search, no retry, no "is it connected?" check that could pass by luck.
 *
 * <p>Which earlier room it joins is chosen to keep the walking short: the
 * <b>nearest</b> one, not simply the previous one. Joining rooms in placement
 * order meant regularly joining two rooms that random placement had thrown to
 * opposite corners, and the result was long empty corridors that were dull to
 * walk down. Picking the nearest earlier room is the same spanning-tree argument —
 * the partner is still an already-connected room — so the guarantee is untouched
 * while the corridors get much shorter.
 *
 * <p>Everything is drawn with a {@link DeterministicRng} in a fixed order, so a
 * seed names exactly one dungeon. Room placement is the only part that can fail
 * to progress (a room may land on another and be rejected); it is bounded by an
 * attempt count and simply stops with however many rooms fit.
 *
 * <p>How many rooms, how big, how crowded: all of it comes from
 * {@link DungeonSettings}, which is read from a file. None of it is compiled in,
 * so a dungeon can be re-tuned without a rebuild.
 */
public final class DungeonGenerator {

    private static final char STONE = '#';
    private static final char FLOOR = '.';

    private DungeonGenerator() {
    }

    /**
     * The seed one link along the chain — how a finished run picks the dungeon for
     * the next one. Keeps a whole session's sequence of dungeons a function of the
     * single seed it started with.
     */
    public static long nextSeed(long seed) {
        return DeterministicRng.advance(seed);
    }

    /** The first floor. */
    public static GeneratedDungeon generate(long seed, DungeonSettings settings) {
        return generate(seed, settings, 1);
    }

    /**
     * A floor of the dungeon at {@code depth}.
     *
     * <p>Depth changes who lives here and how many of them, not the shape: the
     * rooms and corridors are drawn the same way at every depth, so the thing that
     * gets harder is the fighting rather than the walking.
     */
    public static GeneratedDungeon generate(long seed, DungeonSettings settings, int depth) {
        var rng = new DeterministicRng(seed);

        var cells = new char[settings.mapHeight()][settings.mapWidth()];
        for (var row : cells) {
            java.util.Arrays.fill(row, STONE);
        }

        var rooms = placeRooms(rng, settings);
        for (var room : rooms) {
            carveRoom(cells, room);
        }
        var links = connectRooms(cells, rng, rooms);

        var hero = worldCenter(rooms.get(0).centerCellX(), rooms.get(0).centerCellY());
        int bossRoom = furthestRoomFromStart(rooms.size(), links);
        var monsters = populate(rng, rooms, settings, depth, bossRoom);
        var boss = new Monster(DungeonSettings.BOSS,
                worldCenter(rooms.get(bossRoom).centerCellX(), rooms.get(bossRoom).centerCellY()));

        return new GeneratedDungeon(render(cells), hero, monsters, boss, bossRoom,
                List.copyOf(rooms), List.copyOf(links));
    }

    /**
     * The room furthest from where the hero starts, counted in corridors rather
     * than in metres.
     *
     * <p>Corridors are what the player actually walks, so the room at the end of
     * the longest chain of them is the one that feels like the end — a room across
     * the map with a direct corridor to the start is next door, whatever the
     * distance says. Ties fall to the lowest room index, so a seed names one room.
     */
    private static int furthestRoomFromStart(int roomCount, List<Link> links) {
        var stepsFromStart = new int[roomCount];
        java.util.Arrays.fill(stepsFromStart, -1);
        stepsFromStart[0] = 0;

        // The links form a tree grown outward from room 0, so one pass in link
        // order reaches every room with its true distance.
        for (var link : links) {
            stepsFromStart[link.to()] = stepsFromStart[link.from()] + 1;
        }

        int furthest = 0;
        for (int room = 1; room < roomCount; room++) {
            if (stepsFromStart[room] > stepsFromStart[furthest]) {
                furthest = room;
            }
        }
        return furthest;
    }

    /**
     * Join the rooms with the cheapest set of corridors that reaches all of them —
     * a minimum spanning tree, grown one room at a time (Prim's).
     *
     * <p>Connectivity is still a property of the construction, not of luck: the
     * tree starts at room 0 and every step joins a room already in it to one that
     * is not, so after {@code rooms - 1} steps every room hangs off room 0. What
     * the minimum buys is the walking. Joining rooms in placement order, or even
     * each to its nearest <em>earlier</em> room, leaves whichever room was placed
     * out on its own tethered by a corridor across the map; choosing globally means
     * no room is ever joined by a longer corridor than it has to be.
     *
     * <p>Ties fall to the lowest room index by iteration order, so the result is
     * the same everywhere.
     */
    private static List<Link> connectRooms(char[][] cells, DeterministicRng rng, List<Room> rooms) {
        var links = new ArrayList<Link>();
        var joined = new boolean[rooms.size()];
        joined[0] = true;

        for (int step = 1; step < rooms.size(); step++) {
            int bestInside = -1;
            int bestOutside = -1;
            int bestDistance = Integer.MAX_VALUE;
            for (int inside = 0; inside < rooms.size(); inside++) {
                if (!joined[inside]) {
                    continue;
                }
                for (int outside = 0; outside < rooms.size(); outside++) {
                    if (joined[outside]) {
                        continue;
                    }
                    int distance = corridorCost(rooms.get(inside), rooms.get(outside));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestInside = inside;
                        bestOutside = outside;
                    }
                }
            }
            joined[bestOutside] = true;
            carveCorridor(cells, rng, rooms.get(bestInside), rooms.get(bestOutside));
            links.add(new Link(bestInside, bestOutside));
        }
        return links;
    }

    /** Manhattan, because that is exactly how long the L-shaped corridor will be. */
    private static int corridorCost(Room a, Room b) {
        return Math.abs(a.centerCellX() - b.centerCellX())
                + Math.abs(a.centerCellY() - b.centerCellY());
    }

    private static List<Room> placeRooms(DeterministicRng rng, DungeonSettings settings) {
        int target = rng.nextInt(settings.minRooms(), settings.maxRooms());
        var rooms = new ArrayList<Room>();
        for (int attempt = 0;
                attempt < settings.placementAttempts() && rooms.size() < target;
                attempt++) {
            int w = rng.nextInt(settings.minRoomSize(), settings.maxRoomSize());
            int h = rng.nextInt(settings.minRoomSize(), settings.maxRoomSize());
            // Keep a one-cell stone border so a room never touches the map edge.
            int x = rng.nextInt(1, settings.mapWidth() - w - 2);
            int y = rng.nextInt(1, settings.mapHeight() - h - 2);
            var room = new Room(x, y, w, h);
            if (!overlapsAny(room, rooms, settings.roomGap())) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    private static boolean overlapsAny(Room room, List<Room> placed, int gap) {
        for (var other : placed) {
            // Expand one room by the gap and test plain rectangle intersection.
            boolean apart = room.x() - gap >= other.x() + other.w()
                    || other.x() - gap >= room.x() + room.w()
                    || room.y() - gap >= other.y() + other.h()
                    || other.y() - gap >= room.y() + room.h();
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

    /**
     * Fill the rooms the hero does not start in, drawing a kind for each monster
     * from what the data file makes available at this depth.
     *
     * <p>The boss's room is left to the boss. It is meant to be the end of the
     * floor, and a crowd standing around it would turn the fight that gates the
     * next depth into a brawl the player stumbles into sideways.
     */
    private static List<Monster> populate(DeterministicRng rng, List<Room> rooms,
            DungeonSettings settings, int depth, int bossRoom) {
        var available = settings.roomFillersAt(depth);
        var monsters = new ArrayList<Monster>();
        if (available.isEmpty()) {
            return monsters;
        }
        int totalWeight = 0;
        for (var kind : available) {
            totalWeight += kind.weight();
        }

        for (int i = 1; i < rooms.size(); i++) {
            if (i == bossRoom) {
                continue;
            }
            var room = rooms.get(i);
            int count = Math.round(rng.nextInt(settings.minSkeletonsPerRoom(),
                    settings.maxSkeletonsPerRoom()) * settings.monsterCountAt(depth));
            var used = new ArrayList<int[]>();
            for (int n = 0; n < count; n++) {
                // Interior cells only — always floor, never on the room's wall line.
                int cx = rng.nextInt(room.x() + 1, room.x() + room.w() - 2);
                int cy = rng.nextInt(room.y() + 1, room.y() + room.h() - 2);
                if (occupied(used, cx, cy)) {
                    continue; // one per cell, so they never spawn overlapping
                }
                used.add(new int[] {cx, cy});
                monsters.add(new Monster(draw(rng, available, totalWeight).name(),
                        worldCenter(cx, cy)));
            }
        }
        return monsters;
    }

    /**
     * Pick a kind, the commoner ones more often.
     *
     * <p>Walked in list order — which is file order — so a seed draws the same
     * creature everywhere, and re-ordering the blocks in the file is a change to
     * the dungeons it generates rather than a silent no-op.
     */
    private static MonsterKind draw(DeterministicRng rng, List<MonsterKind> available,
            int totalWeight) {
        int roll = rng.nextInt(totalWeight);
        for (var kind : available) {
            roll -= kind.weight();
            if (roll < 0) {
                return kind;
            }
        }
        return available.get(available.size() - 1);
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
        var text = new StringBuilder(cells.length * (cells[0].length + 1));
        for (var row : cells) {
            text.append(row).append('\n');
        }
        return text.toString();
    }
}
