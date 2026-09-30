package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.core.view.UnitView;

/** The selection told to a game that draws its own HUD, and the game's own pick taken up by the client. */
class SelectionLinkTest {

    private static final int ME = 1;
    private static final int THEM = 2;

    private final DukeGame game = DukeGame.create("selection-test");
    private final SelectionLink link = new SelectionLink();
    private final LinkedHashSet<Integer> selected = new LinkedHashSet<>();

    private static UnitView unit(int id, int player, boolean selectable) {
        return new UnitView(id, "Tank", player, 0f, 0f, 0f, 100f, 100f, false, selectable, false, false, -1);
    }

    private static final List<UnitView> UNITS = List.of(
            unit(5, ME, true), unit(3, ME, true), unit(7, ME, true), unit(8, ME, true),
            unit(9, THEM, true), unit(10, THEM, true), unit(11, ME, false));

    @Test
    void aUnitSelectedByClickIsHeldByTheGameOnTheNextFrameAndClearingEmptiesIt() {
        selected.add(5); // a click
        link.tell(selected, game); // the next frame
        assertEquals(List.of(5), game.getSelection());

        selected.add(3); // shift-click
        link.tell(selected, game);
        assertEquals(List.of(5, 3), game.getSelection(), "in the order the player chose them");

        selected.clear();
        link.tell(selected, game);
        assertEquals(List.of(), game.getSelection());
    }

    @Test
    void theGamesOwnPickIsTakenUpOnceAndToldBack() {
        selected.add(5);

        game.select(List.of(new ObjectId(8), new ObjectId(7)));
        assertTrue(link.takeUp(selected, game, UNITS, ME));
        link.tell(selected, game);

        assertEquals(List.of(8, 7), List.copyOf(selected), "those, in the game's order, in place of what was");
        assertEquals(List.of(8, 7), game.getSelection());
        assertFalse(link.takeUp(selected, game, UNITS, ME), "taken once");
    }

    @Test
    void aPickIsHeldToTheClientsRuleOfWhatMayBeSelectedTogether() {
        var ids = List.of(new ObjectId(9), new ObjectId(5), new ObjectId(11), new ObjectId(42));
        assertEquals(List.of(5), SelectionLink.admitted(ids, UNITS, ME),
                "the player's own: not an enemy beside them, not the unselectable, not what is gone");
        assertEquals(List.of(9), SelectionLink.admitted(List.of(new ObjectId(9)), UNITS, ME),
                "one of someone else's, alone, to look at");
        assertEquals(List.of(), SelectionLink.admitted(List.of(new ObjectId(9), new ObjectId(10)), UNITS, ME));
    }

    @Test
    void aPickTheGameAsksToBeAnsweredIsAnsweredOnceAndOneThatDoesNotIsSilent() {
        var voiced = new java.util.ArrayList<Integer>();
        game.select(List.of(new ObjectId(7), new ObjectId(8)), true);
        assertTrue(link.takeUp(selected, game, UNITS, ME, voiced::add));
        assertEquals(List.of(7), voiced, "its select voice, once");

        game.select(List.of(new ObjectId(5)));
        link.takeUp(selected, game, UNITS, ME, voiced::add);
        assertEquals(List.of(7), voiced, "not asking: silent, as now");
    }
}
