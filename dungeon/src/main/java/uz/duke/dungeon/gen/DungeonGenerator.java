package uz.duke.dungeon.gen;

import java.util.ArrayList;
import java.util.List;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.content.MonsterKind;
import uz.duke.dungeon.gen.GeneratedDungeon.Link;
import uz.duke.dungeon.gen.GeneratedDungeon.Monster;
import uz.duke.dungeon.gen.GeneratedDungeon.Placement;
import uz.duke.dungeon.gen.GeneratedDungeon.Prop;
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
        var corridors = connectRooms(cells, rng, rooms, settings.corridorWidth());
        var links = new ArrayList<Link>(corridors.size());
        for (var corridor : corridors) {
            links.add(new Link(corridor.from(), corridor.to()));
        }

        var hero = worldCenter(rooms.get(0).centerCellX(), rooms.get(0).centerCellY());
        int bossRoom = furthestRoomFromStart(rooms.size(), links);
        var storeys = Storeys.of(rng, rooms, corridors, bossRoom, settings, cells);
        var monsters = populate(rng, rooms, settings, depth, bossRoom);
        var boss = new Monster(settings.bossKindAt(depth),
                worldCenter(rooms.get(bossRoom).centerCellX(), rooms.get(bossRoom).centerCellY()));
        var props = scatter(rng, rooms, settings, storeys.map(), monsters, hero, boss.at());

        return new GeneratedDungeon(render(cells), render(storeys.map()), hero, monsters, boss,
                bossRoom, List.copyOf(rooms), List.copyOf(links), storeys.perRoom(), props);
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
    private static List<Corridor> connectRooms(char[][] cells, DeterministicRng rng,
            List<Room> rooms, int width) {
        var links = new ArrayList<Corridor>();
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
            links.add(carveCorridor(cells, rng, bestInside, bestOutside, rooms, width));
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
            if (!overlapsAny(room, rooms, settings.roomGap())
                    && withinReach(room, rooms, settings.maxRoomSpacing())) {
                rooms.add(room);
            }
        }
        return rooms;
    }

    /**
     * Whether this room is close enough to something already placed to belong to
     * the same dungeon.
     *
     * <p>Rejection sampling over the whole map scatters rooms into corners, and two
     * rooms in opposite corners are joined by a corridor across everything — the
     * part of a dungeon a player walks rather than plays. Requiring each new room
     * to sit near an existing one grows the dungeon outward from the first instead
     * of sprinkling it. The first room has nothing to be near, so it goes anywhere.
     */
    private static boolean withinReach(Room room, List<Room> placed, int maxSpacing) {
        if (placed.isEmpty()) {
            return true;
        }
        for (var other : placed) {
            int distance = Math.abs(room.centerCellX() - other.centerCellX())
                    + Math.abs(room.centerCellY() - other.centerCellY());
            if (distance <= maxSpacing) {
                return true;
            }
        }
        return false;
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

    private static Corridor carveCorridor(char[][] cells, DeterministicRng rng, int fromRoom,
            int toRoom, List<Room> rooms, int width) {
        var from = rooms.get(fromRoom);
        var to = rooms.get(toRoom);
        int x1 = from.centerCellX();
        int y1 = from.centerCellY();
        int x2 = to.centerCellX();
        int y2 = to.centerCellY();
        // An L-bend: which leg comes first is a coin-flip, so corridors vary.
        boolean horizontalFirst = rng.nextBoolean();
        if (horizontalFirst) {
            carveHorizontal(cells, y1, x1, x2, width);
            carveVertical(cells, x2, y1, y2, width);
        } else {
            carveVertical(cells, x1, y1, y2, width);
            carveHorizontal(cells, y2, x1, x2, width);
        }
        return new Corridor(fromRoom, toRoom, spine(x1, y1, x2, y2, horizontalFirst), width);
    }

    /**
     * The corridor's centre line, in the order it is walked from one room to the
     * other.
     *
     * <p>Carving does not need an order — a floor is a floor whichever end it was
     * cut from. Height does: a stair is somewhere <em>along</em> a corridor, with
     * one storey behind it and another ahead, and that is a question about a
     * journey rather than about a set of cells.
     */
    private static List<int[]> spine(int x1, int y1, int x2, int y2, boolean horizontalFirst) {
        var walk = new ArrayList<int[]>();
        if (horizontalFirst) {
            walkX(walk, y1, x1, x2);
            walkY(walk, x2, y1, y2);
        } else {
            walkY(walk, x1, y1, y2);
            walkX(walk, y2, x1, x2);
        }
        return walk;
    }

    /** {@code {x, y, alongX}} — the flag says which way the corridor's width runs. */
    private static void walkX(List<int[]> walk, int y, int from, int to) {
        int step = from <= to ? 1 : -1;
        for (int x = from; x != to + step; x += step) {
            walk.add(new int[] {x, y, 1});
        }
    }

    private static void walkY(List<int[]> walk, int x, int from, int to) {
        int step = from <= to ? 1 : -1;
        for (int y = from; y != to + step; y += step) {
            walk.add(new int[] {x, y, 0});
        }
    }

    /**
     * Carve a band {@code width} cells thick, centred on the line.
     *
     * <p>A one-cell corridor is ten world units and the largest creature is
     * sixteen across, which does not merely look tight — a mover with no room to
     * step aside swerves into stone, and stone is where it stays. The width is a
     * correctness setting, not a matter of taste.
     */
    private static void carveHorizontal(char[][] cells, int y, int xa, int xb, int width) {
        for (int offset = -(width - 1) / 2; offset <= width / 2; offset++) {
            int row = y + offset;
            if (row <= 0 || row >= cells.length - 1) {
                continue; // never breach the map's own border
            }
            for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++) {
                cells[row][x] = FLOOR;
            }
        }
    }

    private static void carveVertical(char[][] cells, int x, int ya, int yb, int width) {
        for (int offset = -(width - 1) / 2; offset <= width / 2; offset++) {
            int column = x + offset;
            if (column <= 0 || column >= cells[0].length - 1) {
                continue;
            }
            for (int y = Math.min(ya, yb); y <= Math.max(ya, yb); y++) {
                cells[y][column] = FLOOR;
            }
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
     * Scatter things through the rooms: a pillar to walk round, a statue, a barrel.
     *
     * <p>Solid, and that is the point — a room with nothing in it is a floor with
     * a fight on it, and something to put between yourself and a skeleton is the
     * difference between a room and a place. They are ordinary templates with a
     * shape and no body, so the engine bakes them into the navigation grid and
     * nothing shoots at them.
     *
     * <p>Kept away from the walls, and off the stairs. A pillar in a doorway is a
     * doorway a wide monster cannot use, and this game has spent enough of its
     * life on units wedged in corridors.
     *
     * <p>And never where something already stands. A solid thing dropped on a
     * skeleton leaves the skeleton inside an obstacle, which is not merely untidy:
     * a search that begins on blocked ground finds no path at all, so that
     * skeleton never moves again and the room it was guarding is a room the
     * player walks through unopposed.
     */
    private static List<Prop> scatter(DeterministicRng rng, List<Room> rooms,
            DungeonSettings settings, char[][] storeys, List<Monster> monsters,
            Placement hero, Placement boss) {
        var kinds = settings.propKinds();
        var props = new ArrayList<Prop>();
        if (kinds.isEmpty() || settings.maxPropsPerRoom() <= 0) {
            return List.copyOf(props);
        }
        int totalWeight = 0;
        for (var kind : kinds) {
            totalWeight += kind.weight();
        }
        if (totalWeight <= 0) {
            return List.copyOf(props);
        }

        var taken = new java.util.HashSet<Long>();
        for (var monster : monsters) {
            taken.add(cellKey(monster.at()));
        }
        taken.add(cellKey(hero));
        taken.add(cellKey(boss));

        for (var room : rooms) {
            int count = rng.nextInt(settings.minPropsPerRoom(), settings.maxPropsPerRoom());
            for (int i = 0; i < count; i++) {
                // Two cells in from the wall: one is the wall line itself, and the
                // one beside it is where a doorway opens.
                int cx = rng.nextInt(room.x() + 2, room.x() + room.w() - 3);
                int cy = rng.nextInt(room.y() + 2, room.y() + room.h() - 3);
                if (cx <= room.x() + 1 || cy <= room.y() + 1 || storeys[cy][cx] == '/'
                        || !taken.add(((long) cy << 32) | cx)) {
                    continue; // one to a cell, never on a stair, never on anybody
                }
                props.add(new Prop(drawProp(rng, kinds, totalWeight), worldCenter(cx, cy)));
            }
        }
        return List.copyOf(props);
    }

    private static long cellKey(Placement at) {
        long cx = (long) Math.floor(at.x() / PathGrid.DEFAULT_CELL_SIZE);
        long cy = (long) Math.floor(at.y() / PathGrid.DEFAULT_CELL_SIZE);
        return (cy << 32) | cx;
    }

    private static String drawProp(DeterministicRng rng,
            List<DungeonSettings.PropKind> kinds, int totalWeight) {
        int roll = rng.nextInt(totalWeight);
        for (var kind : kinds) {
            roll -= kind.weight();
            if (roll < 0) {
                return kind.template();
            }
        }
        return kinds.get(kinds.size() - 1).template();
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
