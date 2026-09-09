package uz.duke.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * Generation is a pure function of a seed, and every dungeon it draws is one
 * connected space — no room, and so no skeleton, is ever walled off.
 */
class DungeonGeneratorTest {

    /** The settings the game actually ships with — what these properties are about. */
    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static GeneratedDungeon generate(long seed) {
        return DungeonGenerator.generate(seed, SETTINGS);
    }

    @Test
    void sameSeedDrawsTheSameDungeon() {
        var a = generate(42L);
        var b = generate(42L);

        assertEquals(a.asciiMap(), b.asciiMap(), "the map must be identical for one seed");
        assertEquals(a.hero(), b.hero());
        assertEquals(a.skeletons(), b.skeletons());
        assertEquals(a.rooms(), b.rooms());
    }

    @Test
    void differentSeedsDrawDifferentDungeons() {
        var maps = new HashSet<String>();
        for (long seed = 1; seed <= 30; seed++) {
            maps.add(generate(seed).asciiMap());
        }
        assertTrue(maps.size() > 5,
                "distinct seeds should give distinct dungeons, got " + maps.size() + " unique");
    }

    @Test
    void roomCountStaysInRange() {
        for (long seed = 0; seed <= 200; seed++) {
            int rooms = generate(seed).rooms().size();
            assertTrue(rooms >= SETTINGS.minRooms() && rooms <= SETTINGS.maxRooms(),
                    "seed " + seed + " produced " + rooms + " rooms");
        }
    }

    /**
     * The one that matters most: over many seeds, walk the floor from the hero and
     * make sure every room and every skeleton is reachable. An island room would
     * be a run the player cannot win, so there must never be one.
     */
    @Test
    void everyRoomAndSkeletonIsReachableFromTheHero() {
        for (long seed = 0; seed <= 200; seed++) {
            var dungeon = generate(seed);
            var grid = parse(dungeon.asciiMap());
            var reached = floodFillFromHero(grid, dungeon);

            for (var room : dungeon.rooms()) {
                assertTrue(reached.contains(cell(room.centerCellX(), room.centerCellY())),
                        "seed " + seed + ": a room centre is unreachable from the hero");
            }
            for (var skeleton : dungeon.skeletons()) {
                int cx = (int) Math.floor(skeleton.x() / PathGrid.DEFAULT_CELL_SIZE);
                int cy = (int) Math.floor(skeleton.y() / PathGrid.DEFAULT_CELL_SIZE);
                assertTrue(reached.contains(cell(cx, cy)),
                        "seed " + seed + ": a skeleton is walled off from the hero");
            }
        }
    }

    /**
     * Corridors stay short. Long empty corridors are the part of a generated
     * dungeon a player merely walks down, so joining each room to its nearest
     * placed neighbour (rather than the previous one) has to actually pay off —
     * and keep paying off. An L-shaped corridor's length is the Manhattan distance
     * between the two room centres it joins.
     */
    @Test
    void corridorsAreShort() {
        long total = 0;
        int count = 0;
        int longest = 0;
        for (long seed = 0; seed <= 200; seed++) {
            var dungeon = generate(seed);
            for (var link : dungeon.links()) {
                int length = corridorLength(dungeon, link);
                total += length;
                count++;
                longest = Math.max(longest, length);
            }
        }

        double average = (double) total / count;
        assertTrue(average < 20.0,
                "average corridor should be short, was " + average + " cells");
        assertTrue(longest < SETTINGS.mapWidth(),
                "no corridor should cross the whole map, longest was " + longest + " cells");
    }

    /**
     * The corridors form a spanning tree over the rooms: one fewer corridor than
     * there are rooms, and every room hanging off room 0. This is the structural
     * half of the reachability guarantee — the flood-fill above proves the carved
     * floor really is walkable, this proves the plan could not have left a room out.
     */
    @Test
    void theCorridorsSpanEveryRoom() {
        for (long seed = 0; seed <= 200; seed++) {
            var dungeon = generate(seed);
            int rooms = dungeon.rooms().size();
            assertEquals(rooms - 1, dungeon.links().size(),
                    "seed " + seed + ": a spanning tree has one link fewer than it has rooms");

            var joined = new boolean[rooms];
            joined[0] = true;
            for (var link : dungeon.links()) {
                assertTrue(joined[link.from()],
                        "seed " + seed + ": a corridor grew from a room not yet connected");
                joined[link.to()] = true;
            }
            for (int room = 0; room < rooms; room++) {
                assertTrue(joined[room], "seed " + seed + ": room " + room + " was never joined");
            }
        }
    }

    private static int corridorLength(GeneratedDungeon dungeon, GeneratedDungeon.Link link) {
        var a = dungeon.rooms().get(link.from());
        var b = dungeon.rooms().get(link.to());
        return Math.abs(a.centerCellX() - b.centerCellX())
                + Math.abs(a.centerCellY() - b.centerCellY());
    }

    /** And the engine's own pathfinder agrees: the hero has a route to each skeleton. */
    @Test
    void theEnginePathfinderReachesEachSkeleton() {
        var game = Dungeon.create(7L);
        game.runHeadless(1);
        var logic = game.getLogic();

        var hero = logic.getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals("Hero"))
                .findFirst().orElseThrow();

        var skeletons = logic.getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals("Skeleton"))
                .toList();
        assertFalse(skeletons.isEmpty(), "there should be skeletons to reach");

        for (var skeleton : skeletons) {
            var path = logic.findPath(hero.getPosition(), skeleton.getPosition());
            assertFalse(path.isEmpty(),
                    "no route from the hero to a skeleton at " + skeleton.getPosition());
        }
    }

    // ---- helpers ----

    private static char[][] parse(String asciiMap) {
        var lines = asciiMap.strip().split("\n");
        var grid = new char[lines.length][];
        for (int y = 0; y < lines.length; y++) {
            grid[y] = lines[y].toCharArray();
        }
        return grid;
    }

    private static Set<Long> floodFillFromHero(char[][] grid, GeneratedDungeon dungeon) {
        int hx = (int) Math.floor(dungeon.hero().x() / PathGrid.DEFAULT_CELL_SIZE);
        int hy = (int) Math.floor(dungeon.hero().y() / PathGrid.DEFAULT_CELL_SIZE);

        var reached = new HashSet<Long>();
        var frontier = new ArrayDeque<int[]>();
        if (isFloor(grid, hx, hy)) {
            reached.add(cell(hx, hy));
            frontier.add(new int[] {hx, hy});
        }
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!frontier.isEmpty()) {
            var at = frontier.poll();
            for (var step : steps) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (isFloor(grid, nx, ny) && reached.add(cell(nx, ny))) {
                    frontier.add(new int[] {nx, ny});
                }
            }
        }
        return reached;
    }

    private static boolean isFloor(char[][] grid, int x, int y) {
        return y >= 0 && y < grid.length && x >= 0 && x < grid[y].length && grid[y][x] != '#';
    }

    private static long cell(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
