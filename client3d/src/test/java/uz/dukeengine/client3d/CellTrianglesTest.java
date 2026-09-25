package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.pathfind.HeightMap;

/** The drawn ground's triangles follow the diagonal its relief is split along, so the walked ground is the drawn. */
class CellTrianglesTest {

    @Test
    void eachTriangleSharesTheDiagonalTheReliefIsSplitAlong() {
        var main = TerrainScene.cellTriangles(HeightMap.Diagonal.MAIN);
        var anti = TerrainScene.cellTriangles(HeightMap.Diagonal.ANTI);

        assertArrayEquals(new int[] {0, 3, 2, 0, 2, 1}, main, "as it always was");
        for (int triangle = 0; triangle < 2; triangle++) {
            var corners = Arrays.copyOfRange(main, triangle * 3, triangle * 3 + 3);
            assertTrue(contains(corners, 0) && contains(corners, 2), "the main diagonal, corner 0 to corner 2");
            var other = Arrays.copyOfRange(anti, triangle * 3, triangle * 3 + 3);
            assertTrue(contains(other, 1) && contains(other, 3), "the other, corner 1 to corner 3");
        }
    }

    private static boolean contains(int[] corners, int corner) {
        return Arrays.stream(corners).anyMatch(one -> one == corner);
    }
}
