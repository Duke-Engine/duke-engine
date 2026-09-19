package uz.duke.dungeon.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.stage.Stage;
import uz.duke.dungeon.stage.StageCheck;

/**
 * Dungeons of somebody else's size.
 *
 * <p>The descent's floors are small on purpose — space beyond what the rooms need
 * becomes corridor, and corridor is walked rather than played. A stage is drawn
 * once to be learnt, so it can afford to be large, and its author says how large.
 *
 * <p>What has to survive that is <b>the connectivity guarantee</b>. It comes from
 * the construction — the corridors are a spanning tree — so in principle it holds
 * at any size; in practice a bigger map means more rooms, more corridors crossing
 * each other and far more staircases cut into them, which is exactly where height
 * broke it once before. So it is checked at scale rather than argued about.
 */
class LayoutTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Asking for the settings' own numbers is the floor the settings describe. */
    @Test
    void theSettingsOwnLayoutIsTheShippedDungeon() {
        var layout = Layout.of(SETTINGS);
        assertEquals(SETTINGS.mapWidth(), layout.width());
        assertEquals(SETTINGS.mapHeight(), layout.height());
        assertEquals(SETTINGS.minRooms(), layout.minRooms());
        assertEquals(SETTINGS.maxRooms(), layout.maxRooms());

        var floor = DungeonGenerator.generate(7L, SETTINGS, 1, layout);
        assertEquals(DungeonGenerator.generate(7L, SETTINGS, 1), floor,
                "the descent's own floors must be drawn exactly as they always were");
    }

    @Test
    void aBiggerLayoutDrawsABiggerDungeon() {
        var floor = DungeonGenerator.generate(11L, SETTINGS, 1,
                Layout.sized(SETTINGS, 160, 120, 40));
        var rows = floor.asciiMap().strip().split("\n");

        assertEquals(120, rows.length, "as tall as it was asked to be");
        assertEquals(160, rows[0].length(), "and as wide");
        assertTrue(floor.rooms().size() > SETTINGS.maxRooms(),
                "a map four times the size should hold more rooms than the shipped one ever"
                        + " does, and held " + floor.rooms().size());
    }

    /**
     * And every room in it can still be walked to — over many seeds, at a size
     * nothing in this game has ever been drawn at.
     *
     * <p>The whole check the game runs on load, so this is the same question an
     * author's stage will be asked: reachability by the engine's own rule for a
     * step, nothing standing in stone, nothing on the stairs.
     */
    @Test
    void aLargeFloorIsStillOneWalkableSpace() {
        for (long seed = 0; seed < 12; seed++) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, 1,
                    Layout.sized(SETTINGS, 180, 140, 60));
            var stage = new Stage("big", "Big", "", 1, 1, seed, floor);
            assertEquals(List.of(), StageCheck.problems(stage, SETTINGS),
                    "seed " + seed + " drew a large floor that cannot be played");
        }
    }

    /** A deep floor is a large floor's other half: harder, and still walkable. */
    @Test
    void aLargeFloorIsWalkableAtDepthToo() {
        for (long seed = 0; seed < 6; seed++) {
            var floor = DungeonGenerator.generate(seed, SETTINGS, 12,
                    Layout.sized(SETTINGS, 150, 110, 40));
            var stage = new Stage("deep", "Deep", "", 12, 1, seed, floor);
            assertEquals(List.of(), StageCheck.problems(stage, SETTINGS),
                    "seed " + seed + " at depth 12");
        }
    }

    /**
     * Asking for more rooms than the map can hold stops short rather than failing.
     *
     * <p>Rooms are placed by throwing and rejecting, so there is always a number
     * that will not fit. The builder says so out loud; the generator simply hands
     * back the floor it managed, because a dungeon with fewer rooms is a dungeon
     * and a refusal is not.
     */
    @Test
    void askingForMoreRoomsThanFitStopsShort() {
        var floor = DungeonGenerator.generate(3L, SETTINGS, 1,
                Layout.sized(SETTINGS, 40, 30, 200));
        assertTrue(floor.rooms().size() >= 2, "some rooms fitted");
        assertTrue(floor.rooms().size() < 200, "and not the two hundred asked for");
        assertEquals(List.of(), StageCheck.problems(
                new Stage("tight", "Tight", "", 1, 1, 3L, floor), SETTINGS),
                "what did fit is still a playable floor");
    }

    /** The hint the builder shows grows with the map, and never suggests nothing. */
    @Test
    void theRoomHintGrowsWithTheMap() {
        assertTrue(Layout.roomsThatFit(200, 150) > Layout.roomsThatFit(50, 36));
        assertTrue(Layout.roomsThatFit(24, 24) >= 2);
    }

    /** Attempts scale with what was asked, or a large floor quietly comes out small. */
    @Test
    void aLargeFloorIsGivenEnoughAttempts() {
        assertTrue(Layout.sized(SETTINGS, 200, 150, 60).attempts()
                        > Layout.of(SETTINGS).attempts(),
                "sixty rooms need more throws than six");
    }
}
