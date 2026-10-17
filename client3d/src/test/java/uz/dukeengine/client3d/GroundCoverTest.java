package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Cover grown from a seed and a cell: the same each time, thick where asked, none on a bare cell or a bare surface (E98). */
class GroundCoverTest {

    private static final String MEADOW = "ground/world/grass.png";
    private static final GroundCover COVER = new GroundCover(
            Map.of(MEADOW, List.of(new GroundCover.Kind("models/grass.glb", 3, 11f, 0xFFFFFF),
                    new GroundCover.Kind("models/flower.glb", 1, 9f, 0xFFFFFF))),
            Map.of(MEADOW, 1.2f), 42L, (cx, cy) -> cx == 5, 24f, 1f);

    private static int grown(GroundCover cover, String picture) {
        int count = 0;
        for (int cy = 0; cy < 20; cy++) {
            for (int cx = 0; cx < 20; cx++) {
                count += cover.growIn(cx, cy, picture).size();
            }
        }
        return count;
    }

    @Test
    void theSameFromASeedThickWhereAskedNoneWhereBare() {
        assertEquals(COVER.growIn(3, 7, MEADOW), COVER.growIn(3, 7, MEADOW), "the same, grown again");
        int meadow = grown(COVER, MEADOW);
        assertTrue(meadow > 400 * 1.2 * 0.85 && meadow < 400 * 1.2 * 1.15, "about 1.2 a cell: " + meadow);
        for (int cy = 0; cy < 20; cy++) {
            assertTrue(COVER.growIn(5, cy, MEADOW).isEmpty(), "a bare cell grows nothing");
        }
        assertEquals(0, grown(COVER, "ground/world/road.png"), "a surface naming nothing grows nothing");
        assertEquals(0, grown(COVER.atDensity(0f), MEADOW), "off");
        int low = grown(COVER.atDensity(0.5f), MEADOW);
        assertTrue(low < meadow * 0.65 && low > meadow * 0.35, "half: " + low);
        for (var piece : COVER.growIn(3, 7, MEADOW)) {
            assertTrue(piece.x() >= 3f && piece.x() < 4f && piece.y() >= 7f && piece.y() < 8f, "inside its cell");
        }
    }
}
