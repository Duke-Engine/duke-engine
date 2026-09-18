package uz.duke.studio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.studio.model.GameFactory;
import uz.duke.studio.model.ScriptCompiler;
import uz.duke.studio.model.StudioProject;

/**
 * The whole custom-code path, end to end: user source in the project →
 * javax.tools compile → module registration → the script actually drives a
 * unit in a running game. This is what the Play button does.
 */
class ScriptPipelineTest {

    @Test
    void starterProjectScriptCompilesAndDrivesUnits() {
        var project = StudioProject.starter();

        var result = ScriptCompiler.compile(project.scripts);
        assertTrue(result.ok(), () -> "starter script must compile:\n" + result.errors());
        assertTrue(result.scripts().containsKey("Berserker"));

        var game = GameFactory.toGame(project, result.scripts());
        game.runHeadless(90); // 3 game-seconds

        // The Berserker script hunts enemies: the scripted Orcs must have moved
        // toward the elves (or already be fighting) — i.e. the code took effect.
        // Orcs spawn at x 535 and 545 and the elves are away to the west, so any
        // real progress shows up as a much smaller x. How much smaller is not the
        // point: it depends on the shape of the route, and a straight route
        // spends less of itself on x than a staircase of cell centres did.
        boolean orcMoved = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Orc"))
                .anyMatch(o -> o.getPosition().x() < 520f);
        boolean casualties = game.getLogic().getObjectCount() < 4;
        assertTrue(orcMoved || casualties, "scripted orcs should hunt the enemy");
    }

    @Test
    void compileErrorsAreReportedNotThrown() {
        var project = new StudioProject();
        project.scripts.add(new StudioProject.ScriptDef("Bad",
                "package game.scripts;\npublic class Bad extends Missing {}"));

        var result = ScriptCompiler.compile(project.scripts);
        assertFalse(result.ok());
        assertTrue(result.errors().contains("Bad"));
    }

    @Test
    void wrongBaseClassIsRejectedWithClearMessage() {
        var project = new StudioProject();
        project.scripts.add(new StudioProject.ScriptDef("Loner",
                "package game.scripts;\npublic class Loner {}"));

        var result = ScriptCompiler.compile(project.scripts);
        assertFalse(result.ok());
        assertTrue(result.errors().contains("UnitScript"));
    }
}
