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

    /** The client's own control put on a key of the player's; one already on it takes the moved one's old key. */
    @Test
    void aClientControlIsRebindableAndAClashChangesPlaces() {
        var map = KeyMap.standard();
        var up = map.keysOf(KeyMap.Control.PAN_UP).getFirst();
        var left = map.keysOf(KeyMap.Control.PAN_LEFT).getFirst();
        KeyBindings.putOn(map, KeyMap.Control.PAN_UP, left);
        assertEquals(List.of(left), map.keysOf(KeyMap.Control.PAN_UP), "up on left's key");
        assertTrue(map.keysOf(KeyMap.Control.PAN_LEFT).contains(up), "and left on up's old one");
        assertEquals(left, KeyBindings.unpacked(KeyBindings.packed(left)), "kept and read back");
        var withCtrl = new KeyMap.Key(com.jme3.input.KeyInput.KEY_K, true, false, true);
        assertEquals(withCtrl, KeyBindings.unpacked(KeyBindings.packed(withCtrl)), "its modifiers too");
    }

    /** A quick cast takes the creature nearest the pointer within six of it (E78). */
    @Test
    void aQuickCastTakesTheCreatureNearestThePointerWithinSix() {
        var near = new uz.dukeengine.core.view.UnitView(4, "Skeleton", 1, 105f, 100f, 0f, 50f, 50f, false, true,
                false, false, -1);
        var far = new uz.dukeengine.core.view.UnitView(5, "Skeleton", 1, 120f, 100f, 0f, 50f, 50f, false, true,
                false, false, -1);
        assertEquals(4, WorldPointer.nearestWithin(List.of(far, near), 100f, 100f, WorldPointer.QUICK_CAST_REACH));
        assertEquals(-1, WorldPointer.nearestWithin(List.of(far), 100f, 100f, WorldPointer.QUICK_CAST_REACH));
    }
}
