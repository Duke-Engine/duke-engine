package uz.duke.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.gen.DungeonGenerator;
import uz.duke.dungeon.gen.GeneratedDungeon;
import uz.duke.dungeon.gen.GeneratedDungeon.Monster;
import uz.duke.dungeon.gen.GeneratedDungeon.Placement;

/**
 * What the game says about a stage somebody has edited by hand.
 *
 * <p>Editing one by hand is the point of the format, and it is also how a level
 * ends up with a monster inside a wall or a room nothing can walk to. The rule
 * this holds to is that such a file <b>stops the game and says what is wrong with
 * it</b>. Not a warning in a log nobody reads, and never a run that begins and
 * turns out an hour in to have had no way through.
 */
class StageCheckTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static Stage good(long seed) {
        return new Stage("test", "Test", "", 1, 1, seed,
                DungeonGenerator.generate(seed, SETTINGS, 1));
    }

    private static List<String> problems(Stage stage) {
        return StageCheck.problems(stage, SETTINGS);
    }

    private static Stage with(Stage stage, GeneratedDungeon floor) {
        return new Stage(stage.id(), stage.name(), stage.description(), stage.difficulty(),
                stage.players(), stage.seed(), floor);
    }

    private static GeneratedDungeon floorWith(GeneratedDungeon floor, Placement hero,
            List<Monster> monsters, Monster boss, List<GeneratedDungeon.Prop> props) {
        return new GeneratedDungeon(floor.asciiMap(), floor.levelMap(), hero, monsters, boss,
                floor.bossRoom(), floor.rooms(), floor.links(), floor.roomStoreys(), props);
    }

    /**
     * What the generator draws is clean — over many seeds, and including the
     * reachability walk.
     *
     * <p>Which is as much a test of the checker as of the generator: a check that
     * complained about perfectly good floors would be a check an author learned to
     * ignore.
     */
    @Test
    void aGeneratedFloorHasNothingWrongWithIt() {
        for (long seed = 0; seed < 60; seed++) {
            assertEquals(List.of(), problems(good(seed)), "seed " + seed);
        }
    }

    /** A monster moved into the rock is named, with the cell it is in. */
    @Test
    void aMonsterInsideStoneIsFound() {
        var stage = good(5L);
        var floor = stage.floor();
        var inStone = stoneCell(floor);
        var moved = new Monster(floor.monsters().get(0).kind(), inStone);
        var broken = with(stage, floorWith(floor, floor.hero(),
                List.of(moved), floor.boss(), floor.props()));

        assertTrue(problems(broken).stream().anyMatch(p -> p.contains("inside stone")),
                "a monster in the rock should be reported: " + problems(broken));
    }

    /** Two things on one cell is the rule that has already cost this game a run. */
    @Test
    void twoThingsOnOneCellIsFound() {
        var stage = good(6L);
        var floor = stage.floor();
        var twin = new Monster(floor.monsters().get(0).kind(), floor.monsters().get(0).at());
        var broken = with(stage, floorWith(floor, floor.hero(),
                List.of(floor.monsters().get(0), twin), floor.boss(), floor.props()));

        assertTrue(problems(broken).stream().anyMatch(p -> p.contains("standing on")),
                "one cell holds one thing: " + problems(broken));
    }

    /** A stage with no way in cannot be played, and is not merely odd. */
    @Test
    void noEntranceIsFound() {
        var stage = good(7L);
        var floor = stage.floor();
        var broken = with(stage, floorWith(floor, null, floor.monsters(), floor.boss(),
                floor.props()));

        assertTrue(problems(broken).stream().anyMatch(p -> p.contains("no entrance")),
                problems(broken).toString());
    }

    /** Nor one with nothing to kill: a stage is won by killing the boss. */
    @Test
    void noBossIsFound() {
        var stage = good(8L);
        var floor = stage.floor();
        var broken = with(stage, floorWith(floor, floor.hero(), floor.monsters(), null,
                floor.props()));

        assertTrue(problems(broken).stream().anyMatch(p -> p.contains("no boss")),
                problems(broken).toString());
    }

    /** A boss the creature files have never heard of would spawn as nothing at all. */
    @Test
    void aBossNobodyHasHeardOfIsFound() {
        var stage = good(9L);
        var floor = stage.floor();
        var broken = with(stage, floorWith(floor, floor.hero(), floor.monsters(),
                new Monster("Wyrm", floor.boss().at()), floor.props()));

        assertTrue(problems(broken).stream().anyMatch(p -> p.contains("Wyrm")),
                problems(broken).toString());
    }

    /**
     * And the one the generator itself cannot get wrong: a room walled off.
     *
     * <p>Made by filling the map back in with stone except for the room the hero
     * starts in, which is the crudest possible version of the mistake an author
     * makes by dragging a wall across a doorway.
     */
    @Test
    void aRoomNobodyCanWalkToIsFound() {
        var stage = good(10L);
        var floor = stage.floor();
        var start = floor.rooms().get(0);
        var walls = floor.asciiMap().strip().split("\n");
        for (int y = 0; y < walls.length; y++) {
            var row = new StringBuilder(walls[y]);
            for (int x = 0; x < row.length(); x++) {
                boolean inStartRoom = x >= start.x() && x < start.x() + start.w()
                        && y >= start.y() && y < start.y() + start.h();
                if (!inStartRoom) {
                    row.setCharAt(x, '#');
                }
            }
            walls[y] = row.toString();
        }
        var sealed = new GeneratedDungeon(String.join("\n", walls) + "\n", floor.levelMap(),
                floor.hero(), List.of(), floor.boss(), floor.bossRoom(), floor.rooms(),
                floor.links(), floor.roomStoreys(), List.of());

        var said = problems(with(stage, sealed));
        assertTrue(said.stream().anyMatch(p -> p.contains("cannot be walked to")),
                "a sealed dungeon should report its stranded rooms: " + said);
    }

    /** Two map layers of different sizes is a file that was cut wrong, not mistyped. */
    @Test
    void mapLayersThatDisagreeAreFound() {
        var stage = good(11L);
        var floor = stage.floor();
        var shortened = floor.levelMap().strip();
        shortened = shortened.substring(0, shortened.lastIndexOf('\n')) + "\n";
        var broken = with(stage, new GeneratedDungeon(floor.asciiMap(), shortened, floor.hero(),
                floor.monsters(), floor.boss(), floor.bossRoom(), floor.rooms(), floor.links(),
                floor.roomStoreys(), floor.props()));

        assertTrue(problems(broken).stream().anyMatch(p -> p.contains("different heights")),
                problems(broken).toString());
    }

    /** Loading gathers every complaint into one refusal rather than the first one. */
    @Test
    void loadingABrokenStageRefusesAndSaysWhy(@TempDir Path folder) throws IOException {
        var stage = good(12L);
        var floor = stage.floor();
        var broken = with(stage, floorWith(floor, null, floor.monsters(), null, floor.props()));
        var path = folder.resolve("broken.stage");
        Files.writeString(path, StageFile.write(broken));

        var thrown = assertThrows(IllegalStateException.class,
                () -> Stages.load(path.toString(), SETTINGS));
        assertTrue(thrown.getMessage().contains("no entrance")
                && thrown.getMessage().contains("no boss"),
                "both faults should be listed at once: " + thrown.getMessage());
    }

    /** A path that names nothing is not a silent fall back to the endless dungeon. */
    @Test
    void aMissingStageFileSaysSo() {
        var thrown = assertThrows(IllegalStateException.class,
                () -> Stages.load("stages/nothing-here.stage", SETTINGS));
        assertTrue(thrown.getMessage().contains("no stage at"), thrown.getMessage());
    }

    /** Somewhere on the map that is solid rock. */
    private static Placement stoneCell(GeneratedDungeon floor) {
        var rows = floor.asciiMap().strip().split("\n");
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) == '#') {
                    return Placement.atCell(x, y);
                }
            }
        }
        throw new AssertionError("a dungeon with no stone in it");
    }
}
