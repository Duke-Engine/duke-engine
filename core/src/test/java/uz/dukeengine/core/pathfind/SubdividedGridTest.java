package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/**
 * A grid walked finer than the map's own cells: each of its cells what the map's cell under it is, the ground standing
 * exactly where the map's cells stood it, and a step between them what the step between the map's cells was.
 */
class SubdividedGridTest {

    private static final int[][] ORTHOGONAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Three by two of 10: a stone, a class of ground, an upper storey a ramp climbs to, and a relief with a cliff. */
    private static PathGrid map() {
        var grid = new PathGrid(3, 2);
        grid.setLevelHeight(20f);
        grid.setBlocked(0, 1, true);
        grid.setGroundClass(1, 1, "RUBBLE");
        grid.setLevel(2, 0, 1);
        grid.setRamp(1, 0, true);
        grid.setRelief(HeightMap.parse(List.of("0 0 4 4", "0 2 4 8", "0 4 8 24")));
        return grid;
    }

    @Test
    void eachOfItsCellsIsWhatTheMapsCellUnderItIs() {
        var map = map();
        var fine = map.subdivided(2);

        assertEquals(6, fine.getWidth());
        assertEquals(4, fine.getHeight());
        assertEquals(5f, fine.getCellSize());
        assertEquals(2, fine.cellsPerMapCell());
        assertEquals(10f, fine.mapCellSize(), "the size every rule is counted in");
        for (int y = 0; y < fine.getHeight(); y++) {
            for (int x = 0; x < fine.getWidth(); x++) {
                int cx = x / 2;
                int cy = y / 2;
                String at = x + "," + y;
                assertEquals(map.isTerrainBlocked(cx, cy), fine.isTerrainBlocked(x, y), at);
                assertEquals(map.isBlocked(cx, cy), fine.isBlocked(x, y), at);
                assertEquals(map.level(cx, cy), fine.level(x, y), at);
                assertEquals(map.isRamp(cx, cy), fine.isRamp(x, y), at);
                assertEquals(map.groundClassAt(cx, cy), fine.groundClassAt(x, y), at);
                assertEquals(map.isCliff(cx, cy), fine.isCliff(x, y), at);
            }
        }
        assertTrue(fine.isCliff(5, 3), "the relief's cliff, a map's cell of them");
    }

    @Test
    void theGroundStandsExactlyWhereTheMapsCellsStoodIt() {
        var map = map();
        var fine = map.subdivided(2);

        for (float y = 0.3f; y < 20f; y += 1.25f) {
            for (float x = 0.3f; x < 30f; x += 1.25f) {
                var at = new Coord3D(x, y, 0f);
                assertEquals(map.groundHeight(at), fine.groundHeight(at), "on the ramp and the relief: " + at);
                assertEquals(map.reliefHeight(at), fine.reliefHeight(at), at.toString());
            }
        }
        assertEquals(map.groundHeight(new Coord3D(12.5f, 2.5f, 0f)), fine.groundHeight(2, 0), "a cell's own middle");

        fine.setLevelHeight(30f);
        assertEquals(30f, map.getLevelHeight(), "the storeys are the map's, which stands the ground");
        assertEquals(map.groundHeight(new Coord3D(25f, 5f, 0f)), fine.groundHeight(new Coord3D(25f, 5f, 0f)));
    }

    @Test
    void aStepIsWhatTheStepBetweenTheMapsCellsIs() {
        var map = map();
        var fine = map.subdivided(2);

        for (int y = 0; y < fine.getHeight(); y++) {
            for (int x = 0; x < fine.getWidth(); x++) {
                for (var step : ORTHOGONAL) {
                    int nx = x + step[0];
                    int ny = y + step[1];
                    boolean expected = map.canStep(Math.floorDiv(x, 2), Math.floorDiv(y, 2), Math.floorDiv(nx, 2),
                            Math.floorDiv(ny, 2));
                    assertEquals(expected, fine.canStep(x, y, nx, ny), x + "," + y + " to " + nx + "," + ny);
                }
            }
        }
        assertTrue(fine.canStep(3, 0, 4, 0), "up the ramp onto the storey");
        assertFalse(fine.canStep(3, 1, 4, 0), "never up it on the diagonal");
    }

    @Test
    void itsDecksAreTheMapsAndOneIsTheGridItself() {
        var map = map();
        int floor = map.addDeck(new Coord3D(0f, 0f, 30f), new Coord3D(0f, 10f, 30f), new Coord3D(20f, 10f, 30f),
                new Coord3D(20f, 0f, 30f));
        map.setDeckOpen(floor, false);
        var fine = map.subdivided(2);

        assertEquals(1, fine.decks().size());
        assertFalse(fine.deck(floor).isOpen(), "shut as it was");
        assertSame(map, map.subdivided(1));
    }
}
