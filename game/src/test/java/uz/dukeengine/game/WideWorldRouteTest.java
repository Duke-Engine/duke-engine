package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.map.Sectored;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.pathfind.PathGrid;

/**
 * A map that says it is routed by sectors has its routes found through the sectors their way crosses: a unit sent
 * from one corner of a wide world to the other gets there, and the searching for it examines the ground along its
 * way — while a map that says nothing is searched whole, as every map was.
 */
class WideWorldRouteTest {

    private record Wide(int routeSectors) implements Sectored {
        @Override
        public String name() {
            return "wide";
        }
    }

    /** 400 cells a side, walls across it every 80 cells with one gap each, the gaps far apart. */
    private static PathGrid walled() {
        var grid = new PathGrid(400, 400);
        for (int wall = 1; wall < 5; wall++) {
            int gap = wall % 2 == 0 ? 10 : 385;
            for (int x = 0; x < 400; x++) {
                if (x < gap || x > gap + 3) {
                    grid.setBlocked(x, wall * 80, true);
                }
            }
        }
        return grid;
    }

    private static DukeGame match(Object map) {
        var game = DukeGame.create("wide").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        game.localPlayer(game.addPlayer("Me", Color.CYAN));
        game.applyMapTerrain(walled(), map);
        return game;
    }

    @Test
    void aSectoredMapRoutesAcrossTheWorldByItsSectorsAndTheSearchStaysNearTheWay() {
        var game = match(new Wide(32));
        game.runHeadless(1);
        var logic = game.getLogic();
        assertEquals(32, logic.getRouteSectors());
        var soldier = logic.spawn(logic.findTemplate("Rifleman"), new Coord3D(20f, 20f, 0f), 1);
        var to = new Coord3D(3980f, 3980f, 0f);

        var path = logic.findPath(soldier, to);
        game.runHeadless(1);
        assertNotNull(path);
        assertTrue(path.reachesGoal(), "through every gap, from corner to corner");
        int sectored = logic.getCellsExaminedLastFrame();

        var whole = match(new Object());
        whole.runHeadless(1);
        var wholeLogic = whole.getLogic();
        assertEquals(0, wholeLogic.getRouteSectors(), "a map that says nothing is searched whole");
        var other = wholeLogic.spawn(wholeLogic.findTemplate("Rifleman"), new Coord3D(20f, 20f, 0f), 1);
        assertTrue(wholeLogic.findPath(other, to).reachesGoal());
        whole.runHeadless(1);
        int searchedWhole = wholeLogic.getCellsExaminedLastFrame();
        assertTrue(sectored * 4 < searchedWhole,
                "by sectors " + sectored + " cells examined, the whole grid " + searchedWhole);
    }

    @Test
    void aSectoredMapKnowsWhatReachesWhatWithoutLookingAtEveryCell() {
        var game = match(new Wide(32));
        game.runHeadless(1);
        var zones = game.getLogic().zones();
        assertTrue(zones.connected(1, 1, 398, 398), "joined through the gaps");
        var grid = game.getLogic().getPathGrid();
        for (int x = 0; x < 400; x++) {
            grid.setBlocked(x, 200, true); // the wall at 160 cells, 240's gap closed off by a wall of stone between
        }
        assertTrue(!game.getLogic().zones().connected(1, 1, 398, 398), "a wall laid across: cut in two at once");
    }
}
