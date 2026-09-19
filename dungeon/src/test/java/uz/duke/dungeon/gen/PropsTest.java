package uz.duke.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import uz.duke.core.pathfind.MapLoader;
import uz.duke.core.pathfind.PathGrid;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.content.ShippedBlock;

/**
 * The things standing about in the rooms, and the two ways they can ruin a floor.
 *
 * <p>They are solid — that is what makes them worth having, and what makes them
 * dangerous. A pillar dropped on a skeleton leaves the skeleton inside an
 * obstacle, and a search that begins on blocked ground finds no path at all: that
 * skeleton never moves again and the room it guards is a room the player walks
 * through unopposed. A pillar dropped in a doorway is a doorway the widest
 * creature cannot fit through. Both were found by the run tests rather than
 * reasoned about, which is why they are pinned here.
 */
class PropsTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int SEEDS = 120;

    private static GeneratedDungeon generate(long seed) {
        return DungeonGenerator.generate(seed, SETTINGS, 1);
    }

    private static long cellOf(GeneratedDungeon.Placement at) {
        long cx = (long) Math.floor(at.x() / PathGrid.DEFAULT_CELL_SIZE);
        long cy = (long) Math.floor(at.y() / PathGrid.DEFAULT_CELL_SIZE);
        return (cy << 32) | cx;
    }

    /** There are some, or none of this is being tested at all. */
    @Test
    void roomsHaveThingsInThem() {
        int total = 0;
        for (long seed = 0; seed <= SEEDS; seed++) {
            total += generate(seed).props().size();
        }
        assertTrue(total > SEEDS, "only " + total + " props over " + (SEEDS + 1) + " floors");
    }

    /** And never on top of anything that is alive. */
    @Test
    void nothingIsEverDroppedOnSomebody() {
        for (long seed = 0; seed <= SEEDS; seed++) {
            var dungeon = generate(seed);
            var occupied = new HashSet<Long>();
            for (var monster : dungeon.monsters()) {
                occupied.add(cellOf(monster.at()));
            }
            occupied.add(cellOf(dungeon.hero()));
            occupied.add(cellOf(dungeon.boss().at()));

            for (var prop : dungeon.props()) {
                assertTrue(occupied.add(cellOf(prop.at())),
                        "seed " + seed + ": a " + prop.kind() + " stands on top of something"
                                + " at " + prop.at());
            }
        }
    }

    /** Nor on a stair, which is the one cell a floor cannot spare. */
    @Test
    void nothingStandsOnAStair() {
        for (long seed = 0; seed <= SEEDS; seed++) {
            var dungeon = generate(seed);
            var lines = dungeon.levelMap().strip().split("\n");
            for (var prop : dungeon.props()) {
                int cx = (int) Math.floor(prop.at().x() / PathGrid.DEFAULT_CELL_SIZE);
                int cy = (int) Math.floor(prop.at().y() / PathGrid.DEFAULT_CELL_SIZE);
                assertTrue(lines[cy].charAt(cx) != '/',
                        "seed " + seed + ": a " + prop.kind() + " is standing on the stairs");
            }
        }
    }

    /**
     * With every one of them solid, the floor is still one floor.
     *
     * <p>The engine bakes anything immobile with a shape into the navigation grid,
     * so this walks the map with the props blocked out and checks that every room
     * is still reachable — a doorway with a pillar in it would show up here rather
     * than in a run nobody could finish.
     */
    @Test
    void everyRoomIsStillReachableWithThemAllInPlace() {
        for (long seed = 0; seed <= SEEDS; seed++) {
            var dungeon = generate(seed);
            var grid = MapLoader.fromText(dungeon.asciiMap());
            MapLoader.levels(grid, dungeon.levelMap());
            grid.beginObstacles();
            for (var prop : dungeon.props()) {
                grid.setObstacle((int) Math.floor(prop.at().x() / PathGrid.DEFAULT_CELL_SIZE),
                        (int) Math.floor(prop.at().y() / PathGrid.DEFAULT_CELL_SIZE));
            }
            grid.commitObstacles();

            var reached = walk(grid, dungeon);
            // Every creature, rather than every room centre: a pillar standing on
            // a room's middle cell blocks that one cell and nothing else, and a
            // test aimed at the middle would call a perfectly good room walled off.
            // What must hold is that everything the player has to reach can be
            // reached.
            for (var monster : dungeon.monsters()) {
                assertTrue(reached[cellIndex(grid, monster.at())],
                        "seed " + seed + ": a monster is walled off by furniture");
            }
            assertTrue(reached[cellIndex(grid, dungeon.boss().at())],
                    "seed " + seed + ": the boss is walled off by furniture");
            for (int room = 0; room < dungeon.rooms().size(); room++) {
                assertTrue(anyCellReached(reached, grid, dungeon.rooms().get(room)),
                        "seed " + seed + ": room " + room + " cannot be entered at all");
            }
        }
    }

    private static int cellIndex(PathGrid grid, GeneratedDungeon.Placement at) {
        int cx = (int) Math.floor(at.x() / PathGrid.DEFAULT_CELL_SIZE);
        int cy = (int) Math.floor(at.y() / PathGrid.DEFAULT_CELL_SIZE);
        return cy * grid.getWidth() + cx;
    }

    private static boolean anyCellReached(boolean[] reached, PathGrid grid,
            GeneratedDungeon.Room room) {
        for (int cy = room.y(); cy < room.y() + room.h(); cy++) {
            for (int cx = room.x(); cx < room.x() + room.w(); cx++) {
                if (reached[cy * grid.getWidth() + cx]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A seed names the same room, the same skeletons and the same furniture. */
    @Test
    void aSeedNamesTheSameThings() {
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(generate(seed).props(), generate(seed).props(), "seed " + seed);
        }
    }

    /** Ask for none and there are none — and the floor is otherwise the same one. */
    @Test
    void aFloorCanBeAskedForNoFurnitureAtAll() {
        var bare = DungeonSettings.parse(ShippedBlock.dataWith("Endless", "PropsPerRoom", "[0, 0]"));

        for (long seed = 0; seed < 20; seed++) {
            var dungeon = DungeonGenerator.generate(seed, bare, 1);

            assertEquals(0, dungeon.props().size(), "seed " + seed);
            assertEquals(generate(seed).asciiMap(), dungeon.asciiMap(),
                    "furniture should not change the shape of the floor");
        }
    }

    /** Every cell the hero can walk to, by the engine's own rule for a step. */
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
}
