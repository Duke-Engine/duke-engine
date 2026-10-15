package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A game's own row on Settings kept and read back, its keys rebound, Space a key it may bind (E79). */
class RebindTest {

    @TempDir
    Path folder;

    @Test
    void theGamesRowIsKeptAndReadBackAfterARestart() {
        var told = new int[] {-1};
        var shell = Shell.create().settingsRow("game.controls", "Boshqaruv",
                List.of("Zamonaviy (WASD)", "Klassik (sichqoncha)"), 0, to -> told[0] = to);
        var row = shell.settingsRows().getFirst();
        assertEquals("game.controls", row.key());

        var settings = new GameSettings(folder.resolve("settings.properties"));
        settings.set(row.key(), 1);
        row.chosen().accept(1);
        settings.keep();
        assertEquals(1, told[0], "the game told at once");
        assertEquals(1, new GameSettings(folder.resolve("settings.properties")).number("game.controls", 0),
                "and it is there after a restart");
    }

    @Test
    void theGamesCPutOnKOpensWhatCDidAndAClashChangesPlaces() {
        var hotkeys = Hotkeys.create().on('C', game -> { }).on('B', game -> { }).on(' ', game -> { });
        int k = Hotkeys.codeOf('K');
        hotkeys.rebind('C', k);
        assertEquals('C', hotkeys.actionOn(k), "K opens what C did");
        assertFalse(hotkeys.claims(Hotkeys.codeOf('C')), "and C is free");

        hotkeys.rebind('B', k);
        assertEquals('B', hotkeys.actionOn(k), "B on K");
        assertEquals('C', hotkeys.actionOn(Hotkeys.codeOf('B')), "C took B's old key: they changed places");

        assertTrue(hotkeys.claims(com.jme3.input.KeyInput.KEY_SPACE), "Space, a key a game binds");
    }
}
