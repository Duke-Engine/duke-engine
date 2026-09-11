package uz.duke.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.ini.IniException;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.DungeonGenerator;

/**
 * A stage written out and read back is the dungeon it was cut from.
 *
 * <p>This is the whole promise of the format and it is worth more than anything
 * else here. A frozen floor that comes back a cell to the left, or a storey
 * short, or with one monster missing, is a level that plays <em>almost</em> like
 * the one its author built — and almost is the worst kind, because it is the kind
 * nobody notices until a run is unwinnable.
 *
 * <p>Held against many seeds rather than one. Every part of a floor is drawn from
 * the seed, so one seed proves the format can carry one shape of dungeon; a
 * hundred proves it can carry the ones with three storeys, the ones with a boss
 * next door and the ones where two corridors cross on a stair.
 */
class StageFileTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int SEEDS = 100;

    private static Stage stageOf(long seed) {
        return new Stage("test", "Test", "cut from seed " + seed, 1, 1, seed,
                DungeonGenerator.generate(seed, SETTINGS, 1));
    }

    @Test
    void aStageComesBackTheDungeonItWasCutFrom() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var before = stageOf(seed);
            var after = StageFile.read(StageFile.write(before), "seed " + seed);
            assertEquals(before, after, "seed " + seed + " did not survive the round trip");
        }
    }

    /**
     * And the text it goes through is text a person can read.
     *
     * <p>Not a matter of taste: the reason this format is not a serialised object
     * is that a stage which behaves oddly has to be a file somebody can open and
     * see the mistake in.
     */
    @Test
    void theFileLooksLikeTheMapItHolds() {
        var text = StageFile.write(stageOf(7L));
        assertTrue(text.contains("StageTerrain"), "the map layer is named");
        assertTrue(text.contains("StageStoreys"), "and so is the height layer");
        assertTrue(text.contains("\n  ####"), "the map is drawn in the file as a map");
        assertTrue(text.contains("Monster = "), "a monster is a line saying what and where");
    }

    /** Written twice, the same stage is the same file — so a diff shows edits only. */
    @Test
    void writingIsSteady() {
        assertEquals(StageFile.write(stageOf(11L)), StageFile.write(stageOf(11L)));
    }

    /**
     * A file with no map in it is not a stage with an empty map: it is a broken
     * file, and it says so where it is read rather than somewhere later.
     */
    @Test
    void aFileWithNoMapSaysSo() {
        var text = """
                Stage broken
                  Name = Nowhere
                End
                """;
        var thrown = assertThrows(IniException.class, () -> StageFile.read(text, "broken.stage"));
        assertTrue(thrown.getMessage().contains("StageTerrain"),
                "the complaint should name what is missing: " + thrown.getMessage());
    }

    /** And a word nobody recognises stops the read, naming the line it is on. */
    @Test
    void anUnknownFieldIsNotIgnored() {
        var text = StageFile.write(stageOf(3L)).replace("  Difficulty = ", "  Difficultly = ");
        var thrown = assertThrows(IniException.class, () -> StageFile.read(text, "typo.stage"));
        assertTrue(thrown.getMessage().contains("Difficultly"),
                "the complaint should quote the word: " + thrown.getMessage());
    }

    /**
     * A semicolon begins a comment, so a description cannot hold one — and the
     * writer says so instead of quietly cutting the sentence in half.
     */
    @Test
    void aDescriptionCannotHideAComment() {
        var stage = new Stage("test", "Test", "one room; then another", 1, 1, 1L,
                DungeonGenerator.generate(1L, SETTINGS, 1));
        var thrown = assertThrows(IllegalArgumentException.class, () -> StageFile.write(stage));
        assertTrue(thrown.getMessage().contains(";"), thrown.getMessage());
    }
}
