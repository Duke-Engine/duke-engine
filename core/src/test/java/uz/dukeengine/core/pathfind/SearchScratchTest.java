package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/**
 * A search keeps its arrays on its thread from one search to the next instead of making five the size of the map
 * each time, and a route is the same whatever searched on the thread before it — the lock-step promise.
 */
class SearchScratchTest {

    private static Coord3D at(float x, float y) {
        return new Coord3D(x, y, 0f);
    }

    /** A 160 by 120 floor with walls in it, the size a large dungeon floor reaches. */
    private static PathGrid walled() {
        var grid = new PathGrid(160, 120);
        for (int cy = 0; cy < 110; cy++) {
            grid.setBlocked(40, cy, true);
            grid.setBlocked(120, 119 - cy, true);
        }
        for (int cx = 60; cx < 100; cx++) {
            grid.setBlocked(cx, 60, true);
        }
        return grid;
    }

    private static List<Coord3D> routeOnAFreshThread(PathGrid grid, Coord3D from, Coord3D to, float clearance)
            throws InterruptedException {
        var route = new AtomicReference<List<Coord3D>>();
        var thread = new Thread(() -> route.set(Pathfinder.findPath(grid, from, to, clearance).getWaypoints()));
        thread.start();
        thread.join();
        return route.get();
    }

    @Test
    void aRouteIsTheSameWhateverSearchedOnTheThreadBeforeIt() throws InterruptedException {
        var big = walled();
        var small = new PathGrid(12, 9);
        small.setBlocked(6, 3, true);
        small.setBlocked(6, 4, true);
        small.setBlocked(6, 5, true);
        var from = at(15f, 15f);
        var to = at(1585f, 1185f);

        var fresh = routeOnAFreshThread(big, from, to, 4f);
        assertFalse(fresh.isEmpty());
        // The same thread searching a large map, a small one inside the large one's arrays, and the large one again.
        assertEquals(fresh, Pathfinder.findPath(big, from, to, 4f).getWaypoints());
        assertEquals(routeOnAFreshThread(small, at(5f, 45f), at(115f, 45f), 0f),
                Pathfinder.findPath(small, at(5f, 45f), at(115f, 45f)).getWaypoints());
        assertEquals(fresh, Pathfinder.findPath(big, from, to, 4f).getWaypoints());
    }

    @Test
    void aSearchAskedForInsideAnothersTrafficHasArraysOfItsOwn() throws InterruptedException {
        var big = walled();
        var small = new PathGrid(12, 9);
        var from = at(15f, 15f);
        var to = at(1585f, 1185f);
        var inner = new AtomicReference<List<Coord3D>>();
        Pathfinder.Traffic asking = (cx, cy) -> {
            inner.set(Pathfinder.findPath(small, at(5f, 5f), at(115f, 85f)).getWaypoints());
            return 0;
        };

        var outer = Pathfinder.findPathOrNearest(big, from, to, 0f, null, null, asking).getWaypoints();

        assertEquals(Pathfinder.findPathOrNearest(big, from, to, 0f, null, null, (cx, cy) -> 0).getWaypoints(),
                outer, "the search around the inner one found its own route");
        assertEquals(routeOnAFreshThread(small, at(5f, 5f), at(115f, 85f), 0f), inner.get());
    }
}
