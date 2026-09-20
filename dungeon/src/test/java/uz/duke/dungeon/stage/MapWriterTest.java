package uz.duke.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.dungeon.content.DungeonSettings;

/**
 * A map is drawn from a seed and then filled in by hand — that is what the Map tab is for — so what is worth
 * holding still is not the text of a shipped map but the two things either side of the hand: that the same seed
 * draws the same floor every time, and that what the game ships is a floor it can play.
 *
 * <p>It used to be the shipped file, word for word, against what its seed draws. That was right while a map was
 * only ever generated; with an editor it says "nobody may touch a map", which is the opposite of the point.
 */
class MapWriterTest {

    @Test
    void aSeedDrawsTheSameFloorEveryTime() {
        var settings = DungeonSettings.load();

        assertEquals(MapWriter.text(settings, MapWriter.FIRST), MapWriter.text(settings, MapWriter.FIRST));
    }

    /** And what it draws is playable: it reads back as a stage with nothing wrong with it. */
    @Test
    void whatItDrawsIsAFloorTheGameCanPlay() {
        var settings = DungeonSettings.load();
        var drawn = StageFile.read(MapWriter.text(settings, MapWriter.FIRST), "first");

        assertEquals(java.util.List.of(), StageCheck.problems(drawn, settings));
    }

    /**
     * Every map the game ships is one it can play, hand-edited or not: a map the editor has been over is offered
     * on the stage screen only if it still holds together, and a test that says so is where an edit that broke
     * one is found.
     */
    @Test
    void everyShippedMapIsOneTheGameOffers() {
        var settings = DungeonSettings.load();
        var offered = Stages.all();

        assertTrue(offered.size() >= 2, "the game ships more than one map, and offers " + offered.size());
        for (var listed : offered) {
            var problems = StageCheck.problems(StageFile.read(listed.map().text(), listed.name()), settings);
            assertEquals(java.util.List.of(), problems, listed.name() + " cannot be played");
        }
    }
}
