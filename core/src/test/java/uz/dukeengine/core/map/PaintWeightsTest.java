package uz.dukeengine.core.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The corner rule an editor ports and a client blends by: each surface's share of the painted cells round a corner.
 * The cases an editor's port is pinned against.
 */
class PaintWeightsTest {

    /** Grass, sand and a road; a character the palette does not name in the last cell. */
    private static final List<String> ROWS = List.of(
            "ggs",
            "grs",
            "ss?");
    private static final List<String> KEYS = List.of("g", "s", "r");

    private static float[] at(int cx, int cy) {
        return PaintWeights.at(ROWS, KEYS, 3, 3, cx, cy);
    }

    @Test
    void aCornerOfTheMapIsItsOneCell() {
        assertArrayEquals(new float[] {1f, 0f, 0f}, at(0, 0), "the corner of cell (0, 0) alone");
        assertArrayEquals(new float[] {0f, 1f, 0f}, at(3, 0), "of cell (2, 0)");
    }

    @Test
    void anEdgeCornerSharesItsTwoCells() {
        assertArrayEquals(new float[] {0.5f, 0.5f, 0f}, at(2, 0), "g and s along the top edge");
    }

    @Test
    void anInnerCornerSharesItsFourCellsUpToFourSurfaces() {
        assertArrayEquals(new float[] {0.75f, 0f, 0.25f}, at(1, 1), "three g and the road");
        assertArrayEquals(new float[] {0.25f, 0.5f, 0.25f}, at(2, 1), "g, s, r and s");
    }

    @Test
    void aCellThePaletteDoesNotNameIsNotCounted() {
        assertArrayEquals(new float[] {0f, 2f / 3f, 1f / 3f}, at(2, 2), "r, s and s; the ? not counted");
        assertArrayEquals(new float[] {0f, 0f, 0f}, at(3, 3), "only the ?: nothing shows");
    }
}
