package uz.dukeengine.core.pathfind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;

/** A route costed as the reference costs it: of two routes of one length, the one that turns fewer times. */
class RouteTurnsTest {

    private static Coord3D centre(int cx, int cy) {
        return new Coord3D((cx + 0.5f) * 10f, (cy + 0.5f) * 10f, 0f);
    }

    /** How many times a route of cells changes direction, from the start cell on. */
    private static int turns(PathGrid grid, int fromX, int fromY, java.util.List<Integer> cells) {
        int width = grid.getWidth();
        int turns = 0;
        int x = fromX;
        int y = fromY;
        int wasX = 0;
        int wasY = 0;
        for (int i = 0; i < cells.size(); i++) {
            int cell = cells.get(i);
            int dx = cell % width - x;
            int dy = cell / width - y;
            if (i > 0 && (dx != wasX || dy != wasY)) {
                turns++;
            }
            wasX = dx;
            wasY = dy;
            x = cell % width;
            y = cell / width;
        }
        return turns;
    }

    @Test
    void ofRoutesOfOneLengthTheOneThatTurnsFewerTimesIsTaken() {
        var grid = new PathGrid(20, 20);

        // Four cells across and one down: three straight steps and a diagonal, in any order the same length.
        var cells = Pathfinder.cellsOf(grid, centre(2, 2), centre(6, 3));

        assertEquals(4, cells.size(), "four steps");
        assertEquals(1, turns(grid, 2, 2, cells), "the diagonal at one end: one turn, not two");
    }

    @Test
    void aDiagonalIsTakenPastOneOpenSideOfIt() {
        var grid = new PathGrid(10, 10);
        grid.setBlocked(3, 2, true); // one side of the diagonal from (2, 2) to (3, 3) is stone; the other is open

        var cells = Pathfinder.cellsOf(grid, centre(2, 2), centre(3, 3));

        assertEquals(1, cells.size(), "straight across the corner, one diagonal step");
    }
}
