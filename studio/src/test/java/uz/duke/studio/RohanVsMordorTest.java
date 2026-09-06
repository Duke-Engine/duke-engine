package uz.duke.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.duke.studio.examples.RohanVsMordor;
import uz.duke.studio.io.ProjectIO;
import uz.duke.studio.model.GameFactory;
import uz.duke.studio.model.ScriptCompiler;

/**
 * The dogfooding game, played headlessly: the full Studio pipeline (project →
 * scripts → engine) must produce a battle where Mordor's AI trains waves,
 * marches through the passes, and blood is actually shed.
 */
class RohanVsMordorTest {

    @TempDir
    Path temp;

    @Test
    void projectRoundTripsThroughTheStudioFormat() throws Exception {
        var file = temp.resolve("rvm.duke");
        ProjectIO.save(RohanVsMordor.build(), file);
        var loaded = ProjectIO.load(file);

        assertEquals("Rohan vs Mordor", loaded.title);
        assertEquals(2, loaded.factions.size());
        assertEquals(9, loaded.units.size());
        assertEquals(2, loaded.scripts.size());
        // legacy armies migrated into the RTS model: one map with the mountain
        // wall + start positions, and each faction now owns a starting base
        assertEquals(1, loaded.maps.size());
        var map = loaded.maps.get(0);
        assertTrue(map.blockedCells.size() > 100, "the mountain wall is real map terrain");
        assertEquals(2, map.startPositions.size(), "two start positions from the two armies");
        assertTrue(loaded.findFaction("Rohan").startingUnits.size() > 0,
                "Rohan's army became its starting base");
        assertTrue(loaded.findFaction("Mordor").startingUnits.size() > 0);
    }

    @Test
    void theWarActuallyHappens() {
        var project = RohanVsMordor.build();
        var compiled = ScriptCompiler.compile(project.scripts);
        assertTrue(compiled.ok(), () -> compiled.errors());

        var game = GameFactory.toGame(project, compiled.scripts());
        int startingObjects = project.placements.size();
        game.runHeadless(2700); // 90 seconds of game time

        var logic = game.getLogic();
        int mordor = 2; // player index: Sauron
        assertTrue(logic.getRtsPlayer(mordor).getMoney() < 3500,
                "the WarlordAI script must spend gold training waves");

        boolean anyCasualties = logic.getObjectCount() != startingObjects;
        boolean anyWounded = logic.getObjects().stream().anyMatch(o ->
                o.getBody() != null && o.getBody().getHealth() < o.getBody().getMaxHealth());
        assertTrue(anyCasualties || anyWounded,
                "after 90s the armies must have met in battle");
    }
}
