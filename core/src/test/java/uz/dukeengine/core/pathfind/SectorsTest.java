package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/**
 * A grid's sectors put together the cells its zones put together, keep doing so as the ground changes, and find the
 * nearest cell of a zone its zones find — and a search's arrays held by the cell or by the cells it touched answer
 * every search alike.
 */
class SectorsTest {

    /** A grid of stone, walls with gaps, storeys joined by ramps, and a class of ground. */
    private static PathGrid ground(SplittableRandom random, int width, int height) {
        var grid = new PathGrid(width, height);
        for (int i = 0; i < width * height / 7; i++) {
            grid.setBlocked(random.nextInt(width), random.nextInt(height), true);
        }
        for (int wall = 0; wall < 6; wall++) {
            int y = random.nextInt(height);
            for (int x = 0; x < width; x++) {
                if (random.nextInt(9) != 0) {
                    grid.setBlocked(x, y, true);
                }
            }
        }
        for (int i = 0; i < 40; i++) {
            int x = random.nextInt(width - 6);
            int y = random.nextInt(height - 6);
            for (int dy = 0; dy < 5; dy++) {
                for (int dx = 0; dx < 5; dx++) {
                    grid.setLevel(x + dx, y + dy, 1);
                }
            }
            if (random.nextBoolean()) {
                grid.setRamp(x, y + 2, true);
            }
        }
        for (int i = 0; i < 60; i++) {
            grid.setGroundClass(random.nextInt(width), random.nextInt(height), "MUD");
        }
        return grid;
    }

    /** Whether the two put together the same cells, one zone of each for one of the other. */
    private static void assertSameZones(Zones whole, Sectors sectors, PathGrid grid, String when) {
        var mine = new HashMap<Integer, Integer>();
        var theirs = new HashMap<Integer, Integer>();
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                int a = whole.zoneOf(x, y);
                int b = sectors.zoneOf(x, y);
                assertEquals(a < 0, b < 0, when + ": in a zone at " + x + "," + y);
                if (a < 0) {
                    continue;
                }
                assertEquals(b, (int) mine.computeIfAbsent(a, k -> b), when + ": one zone at " + x + "," + y);
                assertEquals(a, (int) theirs.computeIfAbsent(b, k -> a), when + ": the other's at " + x + "," + y);
            }
        }
        assertEquals(whole.count(), sectors.zoneCount(), when + ": as many zones");
    }

    @Test
    void theSectorsPutTogetherTheCellsTheZonesDoAndKeepUpAsTheGroundChanges() {
        var random = new SplittableRandom(1030);
        var grid = ground(random, 83, 61);
        var sectors = Sectors.of(grid, 8);
        var mud = grid.passage(grid.surfacesOf(java.util.List.of("MUD")));
        var wading = Sectors.of(mud, 8);
        assertSameZones(Zones.of(grid), sectors, grid, "laid");
        assertSameZones(Zones.of(mud), wading, mud, "laid, wading");
        for (int round = 0; round < 60; round++) {
            for (int i = 0; i < 1 + random.nextInt(12); i++) {
                int x = random.nextInt(grid.getWidth());
                int y = random.nextInt(grid.getHeight());
                switch (random.nextInt(5)) {
                    case 0 -> grid.setBlocked(x, y, !grid.isTerrainBlocked(x, y));
                    case 1 -> grid.setLevel(x, y, random.nextInt(2));
                    case 2 -> grid.setRamp(x, y, random.nextBoolean());
                    case 3 -> grid.setGroundClass(x, y, random.nextBoolean() ? "MUD" : null);
                    default -> {
                        grid.beginObstacles();
                        for (int n = 0; n < 30; n++) {
                            grid.setObstacle(random.nextInt(grid.getWidth()), random.nextInt(grid.getHeight()));
                        }
                        grid.commitObstacles();
                    }
                }
            }
            sectors.catchUp(grid);
            wading.catchUp(grid.passage(grid.surfacesOf(java.util.List.of("MUD"))));
            assertSameZones(Zones.of(grid), sectors, grid, "round " + round);
            assertSameZones(Zones.of(mud), wading, mud, "round " + round + ", wading");
        }
    }

    @Test
    void theNearestCellOfAZoneIsTheOneTheZonesFind() {
        var random = new SplittableRandom(77);
        var grid = ground(random, 97, 70);
        var whole = Zones.of(grid);
        var sectors = Sectors.of(grid, 16);
        for (int i = 0; i < 400; i++) {
            int x = random.nextInt(grid.getWidth());
            int y = random.nextInt(grid.getHeight());
            int zone = whole.zoneOf(x, y);
            if (zone < 0) {
                continue;
            }
            int goalX = random.nextInt(-20, grid.getWidth() + 20);
            int goalY = random.nextInt(-20, grid.getHeight() + 20);
            assertEquals(whole.nearestIn(zone, goalX, goalY, x, y),
                    sectors.nearestIn(sectors.zoneOf(x, y), goalX, goalY, x, y), "from " + x + "," + y);
        }
    }

    @Test
    void aSearchHoldsItsArraysByTheCellOrByTheCellsItTouchedAndAnswersAlike() {
        var random = new SplittableRandom(5);
        var grid = ground(random, 90, 90);
        var zones = Zones.of(grid);
        for (int i = 0; i < 150; i++) {
            var from = new Coord3D(random.nextFloat() * 900f, random.nextFloat() * 900f, 0f);
            var to = new Coord3D(random.nextFloat() * 900f, random.nextFloat() * 900f, 0f);
            float clearance = random.nextInt(3) * 4f;
            var dense = Pathfinder.findPathOrNearest(grid, from, to, clearance, zones, null);
            int was = Pathfinder.denseUpTo(0);
            try {
                var sparse = Pathfinder.findPathOrNearest(grid, from, to, clearance, zones, null);
                assertEquals(dense.getWaypoints(), sparse.getWaypoints(), "route " + i);
                assertEquals(dense.reachesGoal(), sparse.reachesGoal(), "route " + i);
            } finally {
                Pathfinder.denseUpTo(was);
            }
        }
    }

    @Test
    void aRouteAcrossAWideWorldExaminesTheGroundAlongItsWayAndGetsThere() {
        var random = new SplittableRandom(900);
        int side = 600;
        var grid = new PathGrid(side, side);
        for (int wall = 1; wall < 6; wall++) {
            int y = wall * 100;
            int gap = random.nextInt(side - 10);
            for (int x = 0; x < side; x++) {
                if (x < gap || x > gap + 4) {
                    grid.setBlocked(x, y, true);
                }
            }
        }
        for (int i = 0; i < side * side / 20; i++) {
            grid.setBlocked(random.nextInt(side), random.nextInt(side), true);
        }
        var from = new Coord3D(15f, 15f, 0f);
        var to = new Coord3D(side * 10f - 15f, side * 10f - 15f, 0f);
        grid.setBlocked(1, 1, false);
        grid.setBlocked(side - 2, side - 2, false);

        var zones = Zones.of(grid);
        var wholeTally = new Pathfinder.Tally();
        var whole = Pathfinder.findPathOrNearest(grid, from, to, 0f, zones, wholeTally, null);
        var sectors = Sectors.of(grid, 32);
        var sectoredTally = new Pathfinder.Tally();
        var sectored = Pathfinder.findPathOrNearest(grid, from, to, 0f, Zones.over(sectors), sectoredTally, null,
                sectors);

        assertTrue(whole.reachesGoal() && sectored.reachesGoal(), "both get there");
        assertTrue(sectoredTally.cells() < wholeTally.cells(),
                "examined " + sectoredTally.cells() + " cells, the whole grid's search " + wholeTally.cells());
        assertTrue(length(sectored, from) <= length(whole, from) * 1.25f,
                "no much longer: " + length(sectored, from) + " against " + length(whole, from));
        for (int i = 1; i < sectored.getWaypoints().size(); i++) {
            var a = sectored.getWaypoints().get(i - 1);
            var b = sectored.getWaypoints().get(i);
            assertTrue(Pathfinder.isClearLine(grid, a, b, 0f) || a.distance(b) <= 1.5f * grid.getCellSize(),
                    "every leg a clear line, or one step from a cell to the next");
        }
    }

    private static float length(Path path, Coord3D from) {
        float total = 0f;
        var at = from;
        for (var point : path.getWaypoints()) {
            total += at.distance(point);
            at = point;
        }
        return total;
    }
}
