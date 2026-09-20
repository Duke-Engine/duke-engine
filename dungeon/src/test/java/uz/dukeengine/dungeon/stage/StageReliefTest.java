package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.data.DataException;
import uz.dukeengine.core.pathfind.HeightMap;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.GeneratedDungeon;
import uz.dukeengine.dungeon.map.StaticMap;

/** A map's relief: written and read back corner for corner, and checked as the ground it makes. */
class StageReliefTest {
    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static Stage withRelief(java.util.function.IntBinaryOperator height) {
        var floor = DungeonGenerator.generate(11L, SETTINGS);
        var rows = floor.asciiMap().strip().lines().toList();
        int columns = rows.getFirst().length() + 1;
        var steps = new int[columns * (rows.size() + 1)];
        for (int y = 0; y <= rows.size(); y++) {
            for (int x = 0; x < columns; x++) {
                steps[y * columns + x] = height.applyAsInt(x, y);
            }
        }
        var relief = new HeightMap(columns, rows.size() + 1, steps);
        var hilly = new GeneratedDungeon(floor.asciiMap(), floor.levelMap(), floor.hero(), floor.monsters(), floor.boss(),
                floor.bossRoom(), floor.rooms(), floor.links(), floor.roomStoreys(), floor.props(), relief, floor.levelHeight());
        return new Stage("hills", "The Hills", "", 1, 1, 11L, hilly);
    }

    private static StaticMap read(String text) {
        return Content.records(text, "test").stream().filter(StaticMap.class::isInstance).map(StaticMap.class::cast)
                .findFirst().orElseThrow();
    }

    @Test
    void aReliefIsWrittenAndReadBackCornerForCorner() {
        var stage = withRelief((x, y) -> (x + y) % 8);
        var text = StageFile.write(stage);
        assertTrue(text.contains("  Relief = ["), text);
        var back = StageFile.stage(read(text), "test").floor().relief();
        assertEquals(stage.floor().relief().written(), back.written());
        assertTrue(StageCheck.problems(stage, SETTINGS).isEmpty(), "a gentle rise is no cliff: " + StageCheck.problems(stage, SETTINGS));
    }

    @Test
    void somethingStandingOnACliffIsFound() {
        var entrance = withRelief((x, y) -> 0).floor().hero();
        var stage = withRelief((x, y) -> x == entrance.cellX() && y == entrance.cellY() ? 40 : 0);
        assertTrue(StageCheck.problems(stage, SETTINGS).stream().anyMatch(p -> p.contains("the entrance") && p.contains("too steep")),
                StageCheck.problems(stage, SETTINGS).toString());
    }

    @Test
    void aReliefOfTheWrongSizeIsRefusedByName() {
        var text = StageFile.write(withRelief((x, y) -> 1)).replaceFirst("(  Relief = \\[\n)    \"[^\"]*\",\n", "$1");
        var error = assertThrows(DataException.class, () -> StageFile.stage(read(text), "test"));
        assertTrue(error.getMessage().contains("corners"), error.getMessage());
    }
}
