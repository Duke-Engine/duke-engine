package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The menus say the game's words, English where it names none, and stand at the side it chooses. */
class MenuWordsTest {

    @Test
    void aWordTheGameNamesIsSaidItsWayAndTheRestAsEver() {
        var shell = Shell.standard().word("Resume", "Davom etish").word("Quit to the desktop", "Chiqish");

        assertEquals("Davom etish", shell.wordFor("Resume"));
        assertEquals("Chiqish", shell.wordFor("Quit to the desktop"));
        assertEquals("Settings", shell.wordFor("Settings"), "a word it names not: the client's own");
    }

    @Test
    void theRowsStandAtTheSideTheGameChoosesInFromItsEdgeAndInTheMiddleAsEver() {
        float screen = 1600f;
        float column = 360f;

        assertEquals(620f, StoneMenu.columnLeft(MenuSide.MIDDLE, 40f, screen, column), "the middle, the margin unused");
        assertEquals(40f, StoneMenu.columnLeft(MenuSide.LEFT, 40f, screen, column));
        assertEquals(1200f, StoneMenu.columnLeft(MenuSide.RIGHT, 40f, screen, column));

        var shell = Shell.standard();
        assertEquals(MenuSide.MIDDLE, shell.menuSide(), "a game that asks nothing: the middle");
        shell.menuAt(MenuSide.LEFT, 64f);
        assertEquals(MenuSide.LEFT, shell.menuSide());
        assertEquals(64f, shell.menuMargin());
    }
}
