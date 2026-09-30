package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.SightCells;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.UnitView;

/**
 * The discovery that counts its changes where they are made answers, over the whole map, every question the one that
 * compared the whole map with itself every frame answered: frame after frame of heroes walking with and without line
 * of sight, the simulation's cells taken a chunk at a time, the map opened and new worlds laid, under fogs of every
 * kind.
 */
class DiscoveryKeptTest {

    private static final int LOCAL = 1;

    /** Rooms of stone and storeys, as a crawler's floor is. */
    private static PathGrid floor(SplittableRandom random, int wide, int deep) {
        var grid = new PathGrid(wide, deep);
        for (int cy = 0; cy < deep; cy++) {
            for (int cx = 0; cx < wide; cx++) {
                grid.setBlocked(cx, cy, random.nextInt(5) == 0);
                if (random.nextInt(9) == 0) {
                    grid.setLevel(cx, cy, 1 + random.nextInt(2));
                }
            }
        }
        grid.setLevelHeight(10f);
        return grid;
    }

    private static UnitView unit(int player, String template, float x, float y) {
        return new UnitView(1, template, player, x, y, 0f, 4f, 4f, false, true, false, false, -1);
    }

    /** The next view the simulation might hand over: some chunks written anew, the rest the same arrays. */
    private static SightCells.View nextView(SplittableRandom random, SightCells.View was, PathGrid grid) {
        int across = (grid.getWidth() + SightCells.CHUNK - 1) / SightCells.CHUNK;
        int down = (grid.getHeight() + SightCells.CHUNK - 1) / SightCells.CHUNK;
        var chunks = was == null ? new byte[across * down][] : was.chunks().clone();
        int edits = 1 + random.nextInt(3);
        for (int i = 0; i < edits; i++) {
            int chunk = random.nextInt(chunks.length);
            if (random.nextInt(6) == 0) {
                chunks[chunk] = null;
                continue;
            }
            var cells = chunks[chunk] == null ? new byte[SightCells.CHUNK * SightCells.CHUNK] : chunks[chunk].clone();
            int from = random.nextInt(cells.length);
            for (int at = from; at < Math.min(cells.length, from + 400); at++) {
                cells[at] = (byte) random.nextInt(3);
            }
            chunks[chunk] = cells;
        }
        return new SightCells.View(grid.getCellSize(), grid.getWidth(), grid.getHeight(), chunks, null);
    }

    private static void assertSameAnswers(DiscoveryBefore before, Discovery now, SplittableRandom random, String when) {
        int wide = before.getWidth();
        int deep = before.getHeight();
        for (int cy = -2; cy < deep + 2; cy++) {
            for (int cx = -2; cx < wide + 2; cx++) {
                assertEquals(before.lightAt(cx, cy), now.lightAt(cx, cy), when + ": light at " + cx + "," + cy);
                assertEquals(before.stateAt(cx, cy).name(), now.stateAt(cx, cy).name(), when + ": state at " + cx + "," + cy);
                assertEquals(before.hidden(cx, cy), now.hidden(cx, cy), when + ": hidden at " + cx + "," + cy);
            }
        }
        float cell = now.getCellSize();
        for (int i = 0; i < 200; i++) {
            float x = (float) random.nextDouble(-2 * cell, (wide + 2) * cell);
            float y = (float) random.nextDouble(-2 * cell, (deep + 2) * cell);
            assertEquals(before.lightAtPoint(x, y), now.lightAtPoint(x, y), when + ": light at point " + x + "," + y);
            assertEquals(before.canSee(x, y), now.canSee(x, y), when + ": in sight at " + x + "," + y);
        }
        assertEquals(before.movedCells(), now.movedCells(), when + ": the cells whose light moved");
        if (!now.everythingChanged()) {
            assertEquals(before.changedCells(), now.changedCells(), when + ": the cells whose state changed");
        }
        assertEquals(before.exploredCells(), now.exploredCells(), when);
        assertEquals(before.visibleCells(), now.visibleCells(), when);
    }

    private static void walk(long seed, Fog fog) {
        var random = new SplittableRandom(seed);
        var grid = floor(random, 70, 52);
        var before = new DiscoveryBefore(grid, fog);
        var now = new Discovery(grid, fog);
        float cell = grid.getCellSize();
        SightCells.View view = null;
        var heroes = new ArrayList<float[]>(List.of(new float[] {200f, 200f}, new float[] {500f, 300f}));
        int mode = 0;
        for (int frame = 0; frame < 260; frame++) {
            if (frame % 40 == 0) {
                mode = random.nextInt(10) < 6 ? 0 : random.nextInt(10) < 6 ? 1 : 2;
            }
            if (random.nextInt(120) == 0) {
                grid = random.nextBoolean() ? grid : floor(random, 40 + random.nextInt(40), 30 + random.nextInt(30));
                before.reset(grid);
                now.reset(grid);
                view = null;
            }
            switch (mode) {
                case 0 -> {
                    var units = new ArrayList<UnitView>();
                    for (var hero : heroes) {
                        hero[0] = Math.clamp(hero[0] + (float) random.nextDouble(-9, 9), 0f, grid.getWidth() * cell);
                        hero[1] = Math.clamp(hero[1] + (float) random.nextDouble(-9, 9), 0f, grid.getHeight() * cell);
                        units.add(unit(LOCAL, random.nextInt(8) == 0 ? "Arrow" : "Hero", hero[0], hero[1]));
                    }
                    units.add(unit(LOCAL + 1, "Hero", 300f, 250f)); // someone else's eyes open nothing
                    float radius = random.nextInt(25) == 0 ? 0f : (float) random.nextDouble(20, 90);
                    String eyes = random.nextInt(4) == 0 ? null : "Hero";
                    before.reveal(units, LOCAL, radius, eyes);
                    now.reveal(units, LOCAL, radius, eyes);
                }
                case 1 -> {
                    view = random.nextInt(30) == 0 ? null : nextView(random, view, grid);
                    before.fromSight(view);
                    now.fromSight(view);
                }
                default -> {
                    before.openEverything();
                    now.openEverything();
                }
            }
            float seconds = random.nextInt(10) == 0 ? 0f : (float) random.nextDouble(0.005, 0.1);
            before.soften(seconds);
            now.soften(seconds);
            assertSameAnswers(before, now, random, "seed " + seed + ", frame " + frame + ", mode " + mode);
        }
    }

    @Test
    void aCrawlersFogWithLineOfSightAndATorchsEdge() {
        walk(11, new Fog(true, 0f, 0.3f, 1f, 2, 7f, 256, 0x121821));
    }

    @Test
    void anOpenFogWithAHardEdgeAndTheUnseenGroundFaintlyThere() {
        walk(23, new Fog(false, 0.2f, 0.34f, 1f, 0, 7f, 256, 0x000000));
    }

    @Test
    void theDefaultFog() {
        walk(37, Fog.DEFAULT);
        walk(41, Fog.DEFAULT);
    }
}
