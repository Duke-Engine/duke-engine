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
        boolean orcMoved = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals("Orc"))
                .anyMatch(o -> o.getPosition().x() < 495f);
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
