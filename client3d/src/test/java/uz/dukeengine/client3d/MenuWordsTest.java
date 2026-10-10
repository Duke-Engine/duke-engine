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

    /**
     * The backdrop runs behind the front end: the game's own, or the client's front menu, its questions and the
     * settings opened from it — never behind the pause menu or a match.
     */
    @Test
    void theBackdropIsKeptGoingBehindTheClientsFrontMenuToo() {
        assertEquals(true, DukeRtsApp.onTheFrontEnd(DukeRtsApp.Screen.MENU, false, null), "the stone front menu");
        assertEquals(true, DukeRtsApp.onTheFrontEnd(DukeRtsApp.Screen.SETTINGS, false, DukeRtsApp.Screen.MENU),
                "settings opened from it");
        assertEquals(false, DukeRtsApp.onTheFrontEnd(DukeRtsApp.Screen.SETTINGS, false, DukeRtsApp.Screen.PAUSED),
                "settings opened from the pause menu");
        assertEquals(false, DukeRtsApp.onTheFrontEnd(DukeRtsApp.Screen.PAUSED, false, null));
        assertEquals(false, DukeRtsApp.onTheFrontEnd(DukeRtsApp.Screen.PLAYING, false, null));
        assertEquals(true, DukeRtsApp.onTheFrontEnd(DukeRtsApp.Screen.FRONT, true, null), "the game's own, as ever");
        assertEquals(false, DukeRtsApp.onTheFrontEnd(DukeRtsApp.Screen.MENU, true, null));
    }

    @Test
    void theGamesOwnFrontRowsAreKeptInOrder() {
        var shell = Shell.standard().frontRows(java.util.List.of(new Shell.Option("Davom etish", "", () -> { }),
                new Shell.Option("Yangi o'yin", "", () -> { })));
        assertEquals(java.util.List.of("Davom etish", "Yangi o'yin"),
                shell.frontRows().stream().map(Shell.Option::label).toList());
        assertEquals("Orqaga", Shell.standard().word("Back", "Orqaga").wordFor("Back"));
    }

    /** The backdrop taking the world's place leaves the front menu up, its rows taking keys and clicks (E33). */
    @Test
    void theMenuThatWasUpStaysUpWhenTheBackdropTakesTheWorldsPlace() {
        var up = new boolean[] {true};
        DukeRtsApp.forgetKeepingTheMenu(() -> up[0], () -> up[0] = false, () -> up[0] = true);
        assertEquals(true, up[0], "shown again after forgetting hid it");

        var down = new boolean[] {false};
        DukeRtsApp.forgetKeepingTheMenu(() -> down[0], () -> down[0] = false, () -> down[0] = true);
        assertEquals(false, down[0], "a menu that was not up is not put up");
    }
}
