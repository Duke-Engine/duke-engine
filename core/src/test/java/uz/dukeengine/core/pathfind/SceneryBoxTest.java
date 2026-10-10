package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ModuleFactory;
import uz.dukeengine.core.thing.ThingFactory;

/** A wall of scenery closes the cells along it, turned with its facing, and a route goes round it. */
class SceneryBoxTest {

    private static SceneryFootprintTest.TestLogic logic() {
        var logic = new SceneryFootprintTest.TestLogic(new ThingFactory(ModuleFactory.withDefaults()));
        logic.init();
        logic.setPathGrid(new PathGrid(12, 12));
        return logic;
    }

    @Test
    void aThreeCellWallFacingNinetyClosesExactlyTheThreeCellsNorthOfItsOriginAndARouteGoesRound() {
        var logic = logic();
        // Its origin on the south edge of cell (5, 5), three cells long, one deep: a cell is 10.
        logic.setSceneryBoxes(List.of(new SceneryBox(55f, 60f, 30f, 10f, 90f)));
        logic.update();
        var grid = logic.getPathGrid();

        var closed = new java.util.ArrayList<String>();
        for (int cy = 0; cy < 12; cy++) {
            for (int cx = 0; cx < 12; cx++) {
                if (grid.isBlocked(cx, cy)) {
                    closed.add(cx + "," + cy);
                }
            }
        }
        assertEquals(List.of("5,3", "5,4", "5,5"), closed);
        assertFalse(grid.isTerrainBlocked(5, 4), "not the map's stone: nothing draws rock there");

        var path = logic.findPath(new Coord3D(55f, 75f, 0f), new Coord3D(55f, 25f, 0f));
        assertFalse(path.isEmpty(), "a way round it");
        for (var waypoint : path.getWaypoints()) {
            assertFalse(grid.isBlocked(grid.toCellX(waypoint), grid.toCellY(waypoint)), "round, not through");
        }
    }

    @Test
    void aBoxCoversFromItsOriginToItsFarEndAndAcrossItsDepth() {
        var east = new SceneryBox(0f, 0f, 30f, 10f, 0f);
        assertTrue(east.covers(5f, 0f));
        assertTrue(east.covers(29f, 4f));
        assertFalse(east.covers(30f, 0f), "its far end is not in it");
        assertFalse(east.covers(-1f, 0f), "nor behind its origin");
        assertFalse(east.covers(10f, 6f), "nor past its side");
    }
}
