package uz.duke.core.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.data.Grid;
import uz.duke.core.data.Relief;
import uz.duke.core.thing.Layered;

/**
 * Any game's map record is ground the engine can lay: the marks on two of its components are the whole
 * contract, and everything else on the record is the game's own business.
 */
class MapTerrainTest {

    /** A game's map: its cells, its relief, and the scale it is drawn at — and whatever else it likes. */
    private record Floor(String name, @Grid(solid = "#X") List<String> cells, @Relief List<String> relief,
            float cellSize, float levelHeight, List<String> monsters) implements MapTemplate, Scaled, Layered {
    }

    /** A game whose maps say nothing about their scale, and whose rock is written another way. */
    private record Cave(String name, @Grid(solid = "O") List<String> cells) implements MapTemplate {
    }

    private static Floor floor(int corner) {
        var row = "%d ".formatted(corner).repeat(5).strip();
        return new Floor("crypt", List.of("####", "#00#", "#01#", "####"),
                List.of(row, row, row, row, row), 8f, 5f, List.of("Skeleton 1 1"));
    }

    @Test
    void theCellsAreTheGridAndWhatTheMapCallsRockIsRock() {
        var grid = MapTerrain.of(floor(0), 10f, 3f);

        assertEquals(4, grid.getWidth());
        assertEquals(4, grid.getHeight());
        assertTrue(grid.isBlocked(0, 0), "'#' is what this map calls rock");
        assertFalse(grid.isBlocked(1, 1), "a storey is floor, whatever its digit");
        assertFalse(grid.isBlocked(2, 2));
        assertEquals(1, grid.level(2, 2), "the digit is the storey it stands on");
        assertEquals(0, grid.level(1, 1));
    }

    @Test
    void theMapsOwnScaleWinsOverTheWorlds() {
        var own = MapTerrain.of(floor(0), 10f, 3f);
        assertEquals(8f, own.getCellSize(), 0.001f);
        assertEquals(5f, own.getLevelHeight(), 0.001f);

        var borrowed = MapTerrain.of(new Cave("hollow", List.of("OOO", "O.O", "OOO")), 10f, 3f);
        assertEquals(10f, borrowed.getCellSize(), 0.001f, "a map that does not say is laid at the world's scale");
        assertEquals(3f, borrowed.getLevelHeight(), 0.001f);
        assertTrue(borrowed.isBlocked(0, 0), "and rock is whatever its own mark says it is");
        assertFalse(borrowed.isBlocked(1, 1));
    }

    /** The relief is read as the heights it is: a corner raised sixteen steps is a cell higher by its own size. */
    @Test
    void theGroundRisesWithTheRelief() {
        assertEquals(0f, MapTerrain.of(floor(0), 10f, 3f).groundHeight(1, 1), 0.001f);

        var raised = MapTerrain.of(floor(16), 10f, 3f);
        assertEquals(8f, raised.groundHeight(1, 1) - raised.storeyHeight(1, 1), 0.001f,
                "sixteen steps is a whole cell of height, and a cell of this map is eight units");
        assertEquals(5f, raised.storeyHeight(2, 2), 0.001f, "the storey under it is the map's own height");
    }

    @Test
    void aRecordWithNoCellsIsNoMap() {
        record Nothing(String name) implements MapTemplate {
        }

        var thrown = assertThrows(IllegalArgumentException.class, () -> MapTerrain.of(new Nothing("empty"), 10f, 3f));
        assertTrue(thrown.getMessage().contains("@Grid"), thrown.getMessage());
    }
}
