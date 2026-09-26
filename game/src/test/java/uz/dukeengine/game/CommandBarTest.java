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
        var pressed = new ArrayList<uz.dukeengine.game.view.CommandPress>();
        var game = headless().onCommandPressed(pressed::add);

        game.setSelection(List.of(4));
        game.pressCommand("stop");

        assertEquals(1, pressed.size());
        assertEquals("stop", pressed.getFirst().id(), "the game's own word, never read by the engine");
        assertEquals(List.of(4), pressed.getFirst().selection(), "and what it was about");
        assertEquals(null, pressed.getFirst().place(), "a button that aims at nothing carries no place");
        assertEquals(-1, pressed.getFirst().target());
    }

    @Test
    void aRefusedPressIsToldApartFromEveryPressTaken() {
        var pressed = new ArrayList<uz.dukeengine.game.view.CommandPress>();
        var refused = new ArrayList<uz.dukeengine.game.view.CommandPress>();
        var game = headless().onCommandPressed(pressed::add).onPressRefused(refused::add);
        game.setSelection(List.of(4));

        var place = new uz.dukeengine.core.math.Coord3D(120f, 80f, 0f);
        game.refusePress("build:Barracks", place, 45f);

        assertEquals(1, refused.size());
        assertEquals("build:Barracks", refused.getFirst().id(), "the button's id");
        assertEquals(place, refused.getFirst().place(), "and the place");
        assertEquals(List.of(4), refused.getFirst().selection());
        assertTrue(pressed.isEmpty(), "and no press taken");
    }

    /**
     * A button that aims comes back with where it was aimed. A building's corner, a rally point and where
     * an airstrike lands arrive the same way; the engine does not know which.
     */
    @Test
    void anAimedPressComesBackWithItsPlaceOrItsTarget() {
        var pressed = new ArrayList<uz.dukeengine.game.view.CommandPress>();
        var game = headless().onCommandPressed(pressed::add);
        game.setSelection(List.of(4));

        var place = new uz.dukeengine.core.math.Coord3D(120f, 80f, 0f);
        game.pressCommand("build:Barracks", place, 45f, -1);
        game.pressCommand("power:Airstrike", null, 0f, 17);

        assertEquals(place, pressed.getFirst().place());
        assertEquals(45f, pressed.getFirst().facing(), "and faced the way the ghost showed");
        assertEquals(17, pressed.get(1).target());
        assertEquals(CommandButton.Aim.NOW, CommandButton.of("x", null, "x", null).aim(), "the default aims at nothing");
        assertEquals(0f, new CommandButton("b", null, "b", null, true, CommandButton.Aim.GROUND, "Barracks").facing(),
                "and a button that says nothing of facing faces along +x");
    }

    /**
     * Whether an armed button's place will do is asked with the facing the ghost has at that moment: for a
     * box, the footprint the player sees is the question.
     */
    @Test
    void theAimIsJudgedAtTheFacingTheGhostHas() {
        var asked = new ArrayList<Float>();
        var game = headless().aimFits((button, place, facing) -> {
            asked.add(facing);
            return facing < 90f;
        });
        game.addPlayer("Me", java.awt.Color.CYAN);
        game.runHeadless(1);

        var place = new uz.dukeengine.core.math.Coord3D(50f, 50f, 0f);
        game.setAim("build:Barracks", place, 45f);
        game.runHeadless(1);
        assertTrue(game.getSnapshot().aimFits());
        assertEquals(45f, asked.getLast());

        game.setAim("build:Barracks", place, 135f);
        game.runHeadless(1);
        assertFalse(game.getSnapshot().aimFits(), "turned, the same place is a different answer");
        assertEquals(135f, asked.getLast());
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
