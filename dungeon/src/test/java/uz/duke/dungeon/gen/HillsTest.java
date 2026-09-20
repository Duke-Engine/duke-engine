package uz.duke.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.stage.Stage;
import uz.duke.dungeon.stage.StageCheck;

/** Hills over a generated floor: drawn from the seed, never a cliff, and the floor under them the same floor. */
class HillsTest {
    private static final DungeonSettings FLAT = DungeonSettings.parse(Content.data().replace("    Hills = 6\n", "    Hills = 0\n"));
    private static final DungeonSettings HILLY = DungeonSettings.parse(Content.data()
            .replace("    Hills = 6\n", "    Hills = 15\n").replace("    HillSize = 4\n", "    HillSize = 3\n"));

    @Test
    void theSameSeedRaisesTheSameHills() {
        assertEquals(Hills.of(7L, 20, 12, 15, 3).written(), Hills.of(7L, 20, 12, 15, 3).written());
        assertFalse(Hills.of(7L, 20, 12, 15, 3).written().equals(Hills.of(8L, 20, 12, 15, 3).written()));
    }

    @Test
    void noHillIsSteepEnoughToBeACliff() {
        for (int size : new int[] {1, 2, 3, 7}) {
            var hills = Hills.of(3L, 30, 20, Hills.MOST, size);
            for (int y = 0; y < 20; y++) {
                for (int x = 0; x < 30; x++) {
                    assertFalse(hills.isCliff(x, y), "cell " + x + ", " + y + " at size " + size);
                    assertTrue(hills.at(x, y) >= 0 && hills.at(x, y) <= Hills.MOST);
                }
            }
        }
    }

    @Test
    void aHillyFloorIsTheSameFloorWithGroundThatRisesAndFalls() {
        assertEquals(15, HILLY.hills(), "the test's own settings");
        var flat = DungeonGenerator.generate(11L, FLAT);
        var hilly = DungeonGenerator.generate(11L, HILLY);
        assertNull(flat.relief(), "a floor with no hills asked for lies flat");
        assertNotNull(hilly.relief());
        assertEquals(flat.asciiMap(), hilly.asciiMap());
        assertEquals(flat.levelMap(), hilly.levelMap());
        assertEquals(flat.monsters(), hilly.monsters());
        assertTrue(hilly.relief().written().stream().anyMatch(row -> !row.matches("[0 ]*")), "and it does rise");
        var stage = new Stage("hills", "Hills", "", 1, 1, 11L, hilly);
        assertTrue(StageCheck.problems(stage, HILLY).isEmpty(), StageCheck.problems(stage, HILLY).toString());
    }
}
