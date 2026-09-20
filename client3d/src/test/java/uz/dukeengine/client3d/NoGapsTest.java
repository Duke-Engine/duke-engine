package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import uz.dukeengine.core.pathfind.MapLoader;
import uz.dukeengine.core.pathfind.PathGrid;
import org.junit.jupiter.api.Test;

/**
 * Nothing you can see through.
 *
 * <p>Every other test here asks whether a piece is in the right place. This one
 * asks the question the player actually asks, which is whether the picture has a
 * hole in it: wherever two neighbouring cells present surfaces at different
 * heights, the step between them has to be filled by something, or the map is
 * open at the side and the world shows through as black.
 *
 * <p>Surfaces are two kinds. A floor's is its own storey. Rock's is the lid laid
 * over it, which sits a wall's height above the tallest floor beside it — so a
 * rock lid beside a raised room stands higher than the lid on the rock behind it,
 * and the step between those two is as easy to miss as it is obvious once seen.
 */
class NoGapsTest {

    private static final float CELL = PathGrid.DEFAULT_CELL_SIZE;
    private static final float STOREY = 10f;
    /** What the kits come out at when scaled to a cell — see the dungeon's own file. */
    private static final float WALL_HEIGHT = 10f;

    /**
     * A raised room in the corner of a map, reached by one stair — the shape the
     * generator makes and the shape the hole showed up in.
     */
    private static final String MAP = """
            ##########
            ###1111###
            ###1111###
            ###1111###
            ####/#####
            #00000000#
            #00000000#
            ##########
            """;

    /**
     * Three storeys and two stairs, which is what the generator can produce and
     * what makes the rock's roof a flight of terraces rather than one step.
     */
    private static final String TALLER = """
            ############
            #0000/1111##
            #0000#1111##
            #0000#11/22#
            #0000#11#22#
            #0000#11#22#
            ############
            """;

    private static PathGrid grid(String map) {
        var grid = MapLoader.fromText(map);
        MapLoader.levels(grid, map);
        grid.setLevelHeight(STOREY);
        return grid;
    }

    /** How high the surface of a cell is drawn: a floor, or the lid over rock. */
    private static float surfaceOf(PathGrid grid, int cx, int cy) {
        if (!grid.isBlocked(cx, cy)) {
            return grid.groundHeight(cx, cy);
        }
        float highest = 0f;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.inBounds(cx + dx, cy + dy) && !grid.isBlocked(cx + dx, cy + dy)) {
                    highest = Math.max(highest, grid.groundHeight(cx + dx, cy + dy));
                }
            }
        }
        return highest + WALL_HEIGHT;
    }

    /**
     * Every step between two neighbouring surfaces is covered by something
     * standing on the boundary between them.
     */
    @Test
    void thereIsNothingYouCanSeeThrough() {
        assertNoHoles(MAP);
    }

    /** And the same on a map with three storeys and a terraced roof over the rock. */
    @Test
    void norOnATallerOne() {
        assertNoHoles(TALLER);
    }

    private static void assertNoHoles(String map) {
        var grid = grid(map);
        var pieces = TileLayout.of(grid);
        var holes = new ArrayList<String>();

        for (int cy = 0; cy < grid.getHeight(); cy++) {
            for (int cx = 0; cx < grid.getWidth(); cx++) {
                for (var step : new int[][] {{1, 0}, {0, 1}}) {
                    int nx = cx + step[0];
                    int ny = cy + step[1];
                    if (!grid.inBounds(nx, ny)) {
                        continue;
                    }
                    float mine = surfaceOf(grid, cx, cy);
                    float theirs = surfaceOf(grid, nx, ny);
                    if (Math.abs(mine - theirs) < 0.001f) {
                        continue; // level with each other: nothing to fill
                    }
                    if (grid.canStep(cx, cy, nx, ny)) {
                        continue; // a step a body may walk up: the stair is the filling
                    }
                    float x = (cx + 0.5f + step[0] * 0.5f) * CELL;
                    float z = (cy + 0.5f + step[1] * 0.5f) * CELL;
                    float covered = coverAt(pieces, x, z);
                    float wanted = Math.max(mine, theirs);
                    if (covered < wanted - 0.001f) {
                        holes.add(String.format(
                                "%d,%d | %d,%d: surfaces at %.0f and %.0f, covered to %.0f",
                                cx, cy, nx, ny, mine, theirs, covered));
                    }
                }
            }
        }

        assertEquals(List.of(), holes, "the map can be seen through at " + holes.size()
                + " places:\n  " + String.join("\n  ", holes));
    }

    /**
     * How high the pieces standing on a boundary reach, counting a piece as a
     * wall's height tall — which is what they are.
     */
    private static float coverAt(List<TileLayout.Placement> pieces, float x, float z) {
        float top = 0f;
        for (var piece : pieces) {
            if (Math.abs(piece.x() - x) > 0.001f || Math.abs(piece.z() - z) > 0.001f) {
                continue;
            }
            top = Math.max(top, switch (piece.piece()) {
                case WALL -> piece.ground() + WALL_HEIGHT;
                case LEDGE -> piece.ground() + WALL_HEIGHT + WALL_HEIGHT;
                default -> 0f;
            });
        }
        return top;
    }
}
