package uz.dukeengine.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.content.ShippedBlock;

/**
 * A dungeon with storeys in it is still one dungeon.
 *
 * <p>Flat, connectivity was a property of the construction: the corridors form a
 * spanning tree, so every room hangs off the first and there is nothing to check.
 * Height can take that away in two ways that no amount of care in the drawing
 * prevents — a corridor too short to hold the stair it needs, and a corridor that
 * crosses a third room standing at some other storey. So the generator walks its
 * own dungeon before handing it over, and this is the test that the walk is
 * honest: it does the same walk, with the engine's rule for what a step is, over
 * two hundred seeds.
 *
 * <p>The other half is that height happened at all. A generator that quietly gave
 * up on every climb would pass the paragraph above perfectly.
 */
class StoreysTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final int SEEDS = 200;

    private static GeneratedDungeon generate(long seed) {
        return DungeonGenerator.generate(seed, SETTINGS, 1);
    }

    private static PathGrid gridOf(GeneratedDungeon dungeon) {
        var grid = MapLoader.fromText(dungeon.asciiMap());
        MapLoader.levels(grid, dungeon.levelMap());
        grid.setLevelHeight(SETTINGS.storeyHeight());
        return grid;
    }

    /**
     * The one that matters most: every room can be walked to, climbing included.
     *
     * <p>The older reachability test flood-fills the carved floor and knows
     * nothing about height, so it would pass a dungeon cut in half by a storey.
     * This one asks the engine's own {@code canStep} at every move, which is the
     * same rule a unit's locomotor obeys.
     */
    @Test
    void everyRoomCanStillBeWalkedTo() {
        for (long seed = 0; seed <= SEEDS; seed++) {
            var dungeon = generate(seed);
            var grid = gridOf(dungeon);
            var reached = walk(grid, dungeon);

            for (int room = 0; room < dungeon.rooms().size(); room++) {
                var at = dungeon.rooms().get(room);
                assertTrue(reached[at.centerCellY() * grid.getWidth() + at.centerCellX()],
                        "seed " + seed + ": room " + room + " stands on storey "
                                + dungeon.roomStoreys().get(room) + " with no way up to it");
            }
            for (var monster : dungeon.monsters()) {
                assertTrue(reached[cellIndex(grid, monster.at().x(), monster.at().y())],
                        "seed " + seed + ": a monster is on a storey the hero cannot climb to");
            }
            assertTrue(reached[cellIndex(grid, dungeon.boss().at().x(), dungeon.boss().at().y())],
                    "seed " + seed + ": the boss is unreachable, so the floor cannot be finished");
        }
    }

    /** And the floors are really at different heights, not all quietly flattened. */
    @Test
    void dungeonsActuallyHaveStoreysInThem() {
        int withHeight = 0;
        int highestSeen = 0;
        for (long seed = 0; seed <= SEEDS; seed++) {
            var storeys = generate(seed).roomStoreys();
            int highest = storeys.stream().mapToInt(Integer::intValue).max().orElse(0);
            int lowest = storeys.stream().mapToInt(Integer::intValue).min().orElse(0);
            highestSeen = Math.max(highestSeen, highest);
            if (highest > lowest) {
                withHeight++;
            }
        }

        assertTrue(withHeight > SEEDS * 3 / 4,
                "only " + withHeight + " of " + (SEEDS + 1) + " dungeons had a second storey");
        assertEquals(SETTINGS.maxStorey(), highestSeen,
                "no dungeon ever reached the top storey the settings allow");
    }

    /**
     * The hero comes in at the bottom and the boss waits above him.
     *
     * <p>Not always at the very top: the climb into his room is cut down to what
     * the corridor can hold a staircase for, and on a floor whose rooms happen to
     * sit close together that is one storey rather than two. What must hold every
     * time is the direction — the way down is never up.
     */
    @Test
    void theHeroComesInAtTheBottomAndTheBossWaitsAbove() {
        int aboveTheEntrance = 0;
        for (long seed = 0; seed <= SEEDS; seed++) {
            var dungeon = generate(seed);
            var storeys = dungeon.roomStoreys();

            assertEquals(SETTINGS.entranceStorey(), storeys.get(0),
                    "seed " + seed + ": the hero did not start on the entrance storey");
            assertTrue(storeys.get(dungeon.bossRoom()) >= storeys.get(0),
                    "seed " + seed + ": the boss ended up below the way in");
            if (storeys.get(dungeon.bossRoom()) > storeys.get(0)) {
                aboveTheEntrance++;
            }
        }

        assertTrue(aboveTheEntrance > SEEDS * 3 / 4,
                "the boss should nearly always be worth climbing to; he was above the way in "
                        + aboveTheEntrance + " times out of " + (SEEDS + 1));
    }

    /**
     * A stair is as wide as the corridor it sits in.
     *
     * <p>A stair narrower than its corridor is a bottleneck, and a bottleneck is
     * where the largest creature wedges — the same reason corridors are two cells
     * wide rather than one. Every run of stair cells should therefore be at least
     * a corridor wide and a stair long.
     */
    @Test
    void noStairIsNarrowerThanItsCorridor() {
        int smallest = Integer.MAX_VALUE;
        for (long seed = 0; seed <= SEEDS; seed++) {
            var dungeon = generate(seed);
            for (var run : stairRuns(dungeon.levelMap())) {
                smallest = Math.min(smallest, run);
                assertTrue(run >= SETTINGS.corridorWidth(),
                        "seed " + seed + ": a stair of " + run + " cells is narrower than the "
                                + SETTINGS.corridorWidth() + "-cell corridor it sits in");
            }
        }
        assertTrue(smallest < Integer.MAX_VALUE, "no dungeon had a stair in it at all");
    }

    /** A seed still names exactly one dungeon, height and all. */
    @Test
    void aSeedNamesOneDungeon() {
        for (long seed = 0; seed < 20; seed++) {
            var first = generate(seed);
            var again = generate(seed);

            assertEquals(first.levelMap(), again.levelMap(), "seed " + seed);
            assertEquals(first.roomStoreys(), again.roomStoreys(), "seed " + seed);
        }
    }

    /** Turn height off and the generator draws the dungeon it drew before it. */
    @Test
    void aDungeonWithNoStoreysIsTheOldFlatOne() {
        var flat = DungeonSettings.parse(ShippedBlock.dataWith("Endless", "MaxStorey", 0));

        for (long seed = 0; seed < 20; seed++) {
            var dungeon = DungeonGenerator.generate(seed, flat, 1);

            assertTrue(dungeon.roomStoreys().stream().allMatch(storey -> storey == 0),
                    "seed " + seed + ": a room stood above the ground floor");
            assertTrue(dungeon.levelMap().indexOf('/') < 0,
                    "seed " + seed + ": a flat dungeon has no stairs in it");
            var grid = gridOf(dungeon);
            for (int cy = 0; cy < grid.getHeight(); cy++) {
                for (int cx = 0; cx < grid.getWidth(); cx++) {
                    assertEquals(0f, grid.groundHeight(cx, cy), 0f, "cell " + cx + "," + cy);
                }
            }
        }
    }

    // ---- determinism, with height in it ----

    /**
     * A run reduced to what the simulation ended up as, over several floors.
     *
     * <p>The same shape {@code DungeonThemeTest} uses. What is being asked here is
     * different: not whether the look changes the game, but whether a game with
     * height in it is still the same game twice.
     */
    private static String playedOut(DungeonSettings settings) {
        var game = uz.dukeengine.dungeon.Dungeon.newSession(20250910L, settings).game();
        game.runHeadless(1);
        var signature = new StringBuilder();
        for (int step = 0; step < 8; step++) {
            game.runHeadless(120);
            signature.append(game.getLogic().getObjectCount()).append(':')
                    .append(game.getLogic().checksum()).append('|');
        }
        return signature.toString();
    }

    /** A seed with storeys in it plays out the same way twice. */
    @Test
    void aRunThroughARaisedDungeonIsTheSameRunTwice() {
        var first = playedOut(SETTINGS);
        var again = playedOut(SETTINGS);

        assertTrue(first.length() > 10, "something has to have happened");
        assertEquals(first, again, "the same seed played out differently the second time");
    }

    /**
     * And height really does reach the simulation.
     *
     * <p>The counterpart to every "nothing changed" test above: if the storeys
     * were only paint, flattening them would leave the run identical, and every
     * guarantee in this file would be guarding nothing.
     */
    @Test
    void takingTheHeightAwayChangesTheRun() {
        var raised = playedOut(SETTINGS);
        var flattened = playedOut(DungeonSettings.parse(ShippedBlock.dataWith("Endless", "MaxStorey", 0)));

        assertTrue(!raised.equals(flattened),
                "a dungeon with storeys played out exactly like a flat one, so the "
                        + "height never reached the simulation at all");
    }

    // ---- helpers ----

    private static int cellIndex(PathGrid grid, float worldX, float worldY) {
        int cx = (int) Math.floor(worldX / PathGrid.DEFAULT_CELL_SIZE);
        int cy = (int) Math.floor(worldY / PathGrid.DEFAULT_CELL_SIZE);
        return cy * grid.getWidth() + cx;
    }

    /** Every cell the hero can get to, by the engine's rule for a step. */
    private static boolean[] walk(PathGrid grid, GeneratedDungeon dungeon) {
        var reached = new boolean[grid.getWidth() * grid.getHeight()];
        int startX = (int) Math.floor(dungeon.hero().x() / PathGrid.DEFAULT_CELL_SIZE);
        int startY = (int) Math.floor(dungeon.hero().y() / PathGrid.DEFAULT_CELL_SIZE);
        var queue = new ArrayDeque<int[]>();
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
        return reached;
    }

    /** How many cells each connected run of stair cells holds. */
    private static List<Integer> stairRuns(String levelMap) {
        var lines = levelMap.strip().split("\n");
        var seen = new boolean[lines.length][lines[0].length()];
        var runs = new ArrayList<Integer>();
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int y = 0; y < lines.length; y++) {
            for (int x = 0; x < lines[y].length(); x++) {
                if (lines[y].charAt(x) != '/' || seen[y][x]) {
                    continue;
                }
                int size = 0;
                var queue = new ArrayDeque<int[]>();
                seen[y][x] = true;
                queue.add(new int[] {x, y});
                while (!queue.isEmpty()) {
                    var at = queue.poll();
                    size++;
                    for (var step : steps) {
                        int nx = at[0] + step[0];
                        int ny = at[1] + step[1];
                        if (ny < 0 || ny >= lines.length || nx < 0 || nx >= lines[ny].length()
                                || seen[ny][nx] || lines[ny].charAt(nx) != '/') {
                            continue;
                        }
                        seen[ny][nx] = true;
                        queue.add(new int[] {nx, ny});
                    }
                }
                runs.add(size);
            }
        }
        return runs;
    }
}
