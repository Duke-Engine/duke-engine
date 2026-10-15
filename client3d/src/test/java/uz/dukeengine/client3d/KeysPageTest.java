package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.input.KeyInput;
import org.junit.jupiter.api.Test;

/** The Settings screen's list of keys, in the game's words, and the codes a game's canvas reads keys by. */
class KeysPageTest {

    @Test
    void aLetterTheGameClaimedWithAWordIsListedWithItInTheGamesLanguage() {
        var hotkeys = Hotkeys.create().on('c', "Qahramon", game -> { }).describe('J', "Vazifalar")
                .controls(KeyMap.empty().bind(KeyMap.Control.PAUSE, KeyMap.Key.of(KeyInput.KEY_P)));

        var lines = DukeRtsApp.keyLines(hotkeys, english -> english.equals("Pause") ? "Pauza" : english);

        assertTrue(lines.contains("C   Qahramon"), lines.toString());
        assertTrue(lines.contains("J   Vazifalar"), "a letter the game only takes on its own canvas, too: " + lines);
        assertTrue(lines.contains("P   Pauza"), "the client's own control, said the game's way: " + lines);
    }

    @Test
    void aKeysCodeAndItsNameAreTheGamesToRead() {
        assertEquals(KeyInput.KEY_C, Hotkeys.codeOf('c'));
        assertEquals(KeyInput.KEY_1, Hotkeys.codeOf('1'), "a digit is bindable too");
        assertEquals(KeyInput.KEY_0, Hotkeys.codeOf('0'));
        assertEquals(-1, Hotkeys.codeOf('-'));
        assertEquals(KeyInput.KEY_ESCAPE, CanvasInput.ESCAPE);
        assertEquals(KeyInput.KEY_RETURN, CanvasInput.ENTER);
        assertEquals(KeyInput.KEY_SPACE, CanvasInput.SPACE);
        assertEquals("C", Hotkeys.nameOf(KeyInput.KEY_C));
        assertEquals("4", Hotkeys.nameOf(KeyInput.KEY_4));
        assertEquals("F5", Hotkeys.nameOf(KeyInput.KEY_F5));
        assertEquals("Esc", Hotkeys.nameOf(KeyInput.KEY_ESCAPE));
    }
}
