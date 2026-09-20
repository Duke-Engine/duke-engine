package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;

/**
 * A map drawn by hand in the IDE, with none of what the generator writes: no rooms, no corridors, no seed — a
 * floor somebody painted, a way in, and something to kill.
 *
 * <p>This is the whole of the editor's promise: what the Map tab writes is a map the game plays. The generator's
 * own maps are checked elsewhere; what is checked here is that none of what it happens to write is <em>required</em>.
 */
class HandDrawnMapTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** What the Map tab writes for a new map: a floor inside a border of rock, a way in, and a boss on it. */
    private static final String DRAWN = """
            StaticMap
              Name = drawn
              DisplayName = Drawn By Hand
              Description = A floor, a way in, and something at the end of it.
              Entrance = [2, 2]
              Cells = [
                "##########",
                "#00000000#",
                "#00000000#",
                "#00000000#",
                "#00000000#",
                "##########",
              ]
              Boss = Warden 7 4
              Monsters = [
                Skeleton 5 2,
              ]
            End
            """;

    @Test
    void aFloorAWayInAndABossIsAMapTheGamePlays() {
        var stage = StageFile.read(DRAWN, "drawn");

        assertEquals(List.of(), StageCheck.problems(stage, SETTINGS), "what the editor writes should need nothing else");
        assertEquals("drawn", stage.id());
        assertEquals("Drawn By Hand", stage.name());
        assertTrue(stage.floor().rooms().isEmpty(), "a hand-drawn floor was cut into no rooms, and needs none");

        var game = Dungeon.createStage(stage, SETTINGS);
        game.runHeadless(30);

        assertNotNull(game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals(SETTINGS.run().defaultHero()))
                .findFirst().orElse(null), "the hero should be standing where the map let him in");
    }

    /** The same map laid at its own height: a storey twice as tall as the world's. */
    @Test
    void aMapMayBeLaidAtItsOwnHeight() {
        var stage = StageFile.read(DRAWN.replace("  Entrance", "  LevelHeight = 24\n  Entrance"), "drawn");

        assertEquals(List.of(), StageCheck.problems(stage, SETTINGS));
        var game = Dungeon.createStage(stage, SETTINGS);
        game.runHeadless(5);
        assertEquals(24f, game.getTerrain().getLevelHeight(), 0.001f, "the map's own storey height, not the world's");
    }
}
