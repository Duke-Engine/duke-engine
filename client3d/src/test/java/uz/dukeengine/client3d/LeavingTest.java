package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.Colour;
import uz.dukeengine.game.DukeGame;

/** A game's own rows on the pause menu, and the game told once, with the world still, before its match is let go. */
class LeavingTest {

    private static DukeRtsApp app(DukeGame game) {
        return new DukeRtsApp(game, null, Shell.standard(), null, null, null, null, null, null, null);
    }

    @Test
    void theGameIsToldOnceWithTheWorldStillBeforeItsMatchIsLetGo() {
        var game = DukeGame.create("leaving-test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(20, 20);
        game.addPlayer("Alone", Colour.BLUE);
        game.boot();
        var app = app(game);
        var told = new AtomicInteger();
        var stillWhenTold = new ArrayList<Boolean>();
        app.onLeaving(() -> {
            told.incrementAndGet();
            stillWhenTold.add(game.getLogic().isGamePaused());
        });

        app.leave();
        app.leave();

        assertEquals(1, told.get(), "once, however many ways it goes");
        assertEquals(List.of(true), stillWhenTold, "the simulation paused before the game is told");
    }

    @Test
    void withNoMatchThereIsNothingToTell() {
        var app = app(DukeGame.create("no-match"));
        var told = new AtomicInteger();
        app.onLeaving(told::incrementAndGet);

        app.leave();

        assertEquals(0, told.get());
    }

    @Test
    void theGamesPauseRowsAreKeptInTheOrderGiven() {
        var slots = new Shell.Question("SAVE", "", List.of(new Shell.Option("Slot 1", "", () -> { })));
        var rows = new ArrayList<>(List.of(new Shell.Option("Save", "", null, slots),
                new Shell.Option("Load", "", () -> { })));
        var shell = Shell.standard().pauseRows(rows);
        rows.clear();

        assertEquals(List.of("Save", "Load"), shell.pauseRows().stream().map(Shell.Option::label).toList());
        assertEquals(slots, shell.pauseRows().getFirst().next());
        assertTrue(Shell.standard().pauseRows().isEmpty(), "a game that names none adds none");
        assertThrows(UnsupportedOperationException.class, () -> shell.pauseRows().clear());
    }
}
