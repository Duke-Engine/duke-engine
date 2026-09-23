package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.game.view.CommandButton;

/**
 * What the player may do with what he has selected: the conversation, without a window.
 *
 * <p>Three things to hold: the selection goes one way and the buttons the other, the buttons are worked
 * out where the state they are about lives, and a press comes back as the button's own word and nothing
 * else. What the buttons <em>look</em> like is the client's, and is not here.
 */
class CommandBarTest {

    private static DukeGame headless() {
        return DukeGame.create("Bar")
                .loadUnits(DukeGame.STARTER_UNITS)
                .map(20, 20);
    }

    @Test
    void theGameIsAskedAboutWhateverTheWindowLastSelected() {
        var asked = new ArrayList<List<Integer>>();
        var game = headless().commandBar(selection -> {
            asked.add(selection);
            return selection.isEmpty() ? List.of()
                    : List.of(CommandButton.of("train.rifleman", "icons/rifleman.png", "Rifleman", "B"));
        });
        game.addPlayer("Me", java.awt.Color.CYAN);
        game.runHeadless(2);

        game.setSelection(List.of(7, 9));
        assertEquals(List.of(7, 9), game.getSelection(), "in the order the window holds them");

        // And the answer travels back in the snapshot, worked out from the selection just reported.
        game.runHeadless(2);
        assertEquals(List.of(List.of(7, 9)), asked.stream().distinct().filter(s -> !s.isEmpty()).toList(),
                "asked about exactly what the window said it had");
        assertEquals(List.of("train.rifleman"),
                game.getSnapshot().commands().stream().map(CommandButton::id).toList());

        // The window's list, copied: what the game is handed cannot change under it mid-frame.
        var mutable = new ArrayList<>(List.of(1));
        game.setSelection(mutable);
        mutable.add(2);
        assertEquals(List.of(1), game.getSelection());
    }

    @Test
    void aPressComesBackAsTheButtonsOwnWordAndWhatWasSelected() {
        var pressed = new ArrayList<String>();
        var about = new ArrayList<List<Integer>>();
        var game = headless().onCommandPressed((id, selection) -> {
            pressed.add(id);
            about.add(selection);
        });

        game.setSelection(List.of(4));
        game.pressCommand("build.barracks");

        assertEquals(List.of("build.barracks"), pressed, "the game's own word, never read by the engine");
        assertEquals(List.of(List.of(4)), about, "and what it was about");
    }

    /** A game that asked for none of this is a game that had none of it, which is every game until now. */
    @Test
    void aGameThatNamedNoBarSendsNoButtonsAndTakesNoPresses() {
        var game = headless();
        game.addPlayer("Me", java.awt.Color.CYAN);
        game.runHeadless(2);

        assertEquals(List.of(), game.getSnapshot().commands());
        game.pressCommand("anything"); // no handler: nothing happens, and nothing throws
    }

    /**
     * A button that cannot be pressed is still drawn — dim, and in its place. A bar whose buttons come and
     * go as money does is a bar nobody can learn, and learning it is most of what makes an RTS playable.
     */
    @Test
    void aButtonThatCannotBePressedIsStillOneOfTheButtons() {
        var poor = new CommandButton("build.barracks", "icons/barracks.png", "Barracks", "B", false);
        var rich = CommandButton.of("build.barracks", "icons/barracks.png", "Barracks", "B");

        assertFalse(poor.available());
        assertTrue(rich.available());
        assertEquals(poor.id(), rich.id(), "the same button either way, so it keeps its place");
        assertEquals("", new CommandButton("x", null, null, null, true).label(), "a nameless one is blank");
    }
}
