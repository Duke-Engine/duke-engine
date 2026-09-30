package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.view.UnitView;

/**
 * The dark worked out only for a window round the camera is, once it has settled, the dark of the whole map: every
 * cell, in the window and out of it, as bright as the discovery kept whole draws it; the ground coming into the window
 * drawn at once as it has settled, among the cells that moved; and a still world costs nothing, however wide.
 */
class DiscoveryWindowTest {

    private static final int LOCAL = 0;
    private static final Fog CRAWLER = new Fog(true, 0f, 0.3f, 1f, 2, 7f, 256, 0x121821, 2);

    private static PathGrid rooms(int wide, int deep, long seed) {
        var random = new SplittableRandom(seed);
        var grid = new PathGrid(wide, deep);
        for (int cy = 0; cy < deep; cy++) {
            for (int cx = 0; cx < wide; cx++) {
                grid.setBlocked(cx, cy, random.nextInt(6) == 0);
            }
        }
        return grid;
    }

    private static List<UnitView> hero(float x, float y) {
        return List.of(new UnitView(1, "Hero", LOCAL, x, y, 0f, 4f, 4f, false, true, false, false, -1));
    }

    /** Soften until nothing moves: the light at its target everywhere it is kept. */
    private static void settle(Discovery seen, float x, float y) {
        for (int i = 0; i < 400; i++) {
            seen.soften(0.1f);
            seen.follow(x, y);
            if (seen.moved().isEmpty()) {
                return;
            }
        }
        throw new AssertionError("the light never settled");
    }

    @Test
    void aWindowSettledIsTheWholeMapSettledInTheWindowAndOutOfIt() {
        var grid = rooms(160, 120, 5);
        var whole = new Discovery(grid, CRAWLER);
        var near = new Discovery(grid, CRAWLER);
        near.window(40);
        assertTrue(near.isWindowed() && !whole.isWindowed());
        var random = new SplittableRandom(8);
        float x = 400f;
        float y = 300f;
        for (int step = 0; step < 30; step++) {
            x = Math.clamp(x + (float) random.nextDouble(-60, 60), 0f, 1599f);
            y = Math.clamp(y + (float) random.nextDouble(-60, 60), 0f, 1199f);
            whole.reveal(hero(x, y), LOCAL, 70f, "Hero");
            near.reveal(hero(x, y), LOCAL, 70f, "Hero");
            settle(whole, x, y);
            settle(near, x, y);
            for (int cy = -1; cy <= grid.getHeight(); cy++) {
                for (int cx = -1; cx <= grid.getWidth(); cx++) {
                    assertEquals(whole.lightAt(cx, cy), near.lightAt(cx, cy), "step " + step + ", cell " + cx + "," + cy);
                    assertEquals(whole.stateAt(cx, cy), near.stateAt(cx, cy));
                }
            }
            for (int i = 0; i < 100; i++) {
                float px = (float) random.nextDouble(-20, 1620);
                float py = (float) random.nextDouble(-20, 1220);
                assertEquals(whole.lightAtPoint(px, py), near.lightAtPoint(px, py), "step " + step);
            }
        }
    }

    @Test
    void theGroundComingIntoTheWindowIsDrawnSettledAndIsAmongTheCellsThatMoved() {
        var grid = rooms(200, 200, 13);
        var whole = new Discovery(grid, CRAWLER);
        var near = new Discovery(grid, CRAWLER);
        near.window(32);
        for (float x = 300f; x < 1200f; x += 55f) {
            whole.reveal(hero(x, 500f), LOCAL, 80f, "Hero");
            near.reveal(hero(x, 500f), LOCAL, 80f, "Hero");
            settle(whole, x, 500f);
            settle(near, x, 500f);
        }
        int fromX = near.windowX();
        int fromY = near.windowY();
        near.soften(0.1f); // settled already: nothing moves
        assertTrue(near.moved().isEmpty());
        near.follow(1500f, 520f); // thirty-odd cells on, two down
        assertEquals(150 - 16, near.windowX());
        assertEquals(52 - 16, near.windowY());
        int came = 0;
        for (int cy = near.windowY(); cy < near.windowY() + 32; cy++) {
            for (int cx = near.windowX(); cx < near.windowX() + 32; cx++) {
                boolean wasIn = cx >= fromX && cx < fromX + 32 && cy >= fromY && cy < fromY + 32;
                if (wasIn) {
                    continue;
                }
                came++;
                assertTrue(near.movedCells().get(cy * 200 + cx), "cell " + cx + "," + cy + " came in");
                assertEquals(whole.lightAt(cx, cy), near.lightAt(cx, cy), "drawn as it has settled");
            }
        }
        assertEquals(came, near.moved().size(), "and nothing else moved");
    }

    @Test
    void aStillWideWorldCostsNothingAndAWalkCostsWhatItsWindowHolds() {
        var grid = new PathGrid(2048, 2048);
        var near = new Discovery(grid, CRAWLER);
        near.window(96);
        float x = 10_000f;
        float y = 10_000f;
        near.follow(x, y);
        int most = 0;
        for (int frame = 0; frame < 300; frame++) {
            x += 0.4f;
            near.reveal(hero(x, y), LOCAL, 80f, "Hero");
            near.soften(1f / 60f);
            near.follow(x, y);
            if (frame > 0) {
                most = Math.max(most, near.moved().size());
            }
        }
        assertTrue(most > 0 && most <= 96 * 96, "a walk moves what its window holds, at most: " + most);
        settle(near, x, y);
        near.reveal(hero(x, y), LOCAL, 80f, "Hero");
        near.soften(1f / 60f);
        near.follow(x, y);
        assertTrue(near.moved().isEmpty() && near.changed().isEmpty() && !near.everythingChanged(),
                "standing still costs nothing");
        assertFalse(near.everythingChanged());
    }
}
