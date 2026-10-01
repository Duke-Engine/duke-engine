package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.CommandButton;

/**
 * A key used on a gate is the game's own order, not an attack: a press the game answers as one of its words is marked
 * and voiced as a click giving that word on the thing is, and a press it says nothing of is answered as an ability
 * aimed at an enemy always was.
 */
class ThingAnswerTest {

    private static final int YELLOW = 0xFFD84A;

    /** The dungeon's bag slot holding the key: armed by a click, pressed on a thing. */
    private static final CommandButton KEY =
            new CommandButton("use:3", "icons/key.png", "Key", null, true, CommandButton.Aim.UNIT, null);

    /** A game whose look rings its own orders yellow, as the dungeon's does. */
    private static Visuals ringing() {
        return Visuals.create().orderMark(OrderMark.DEFAULTS.contextRing(YELLOW));
    }

    @Test
    void aPressAnsweredAsTheGamesWordIsAnsweredAsAClickGivingItIs() {
        var visuals = ringing().orderAnswer("use", "use");

        var press = ThingAnswer.toThePress(KEY.answeredAs("use"), visuals);

        assertEquals(new ThingAnswer(OrderMarkers.Kind.CONTEXT, false, "use"), press,
                "the ring of the game's own orders, in its colour, and the word's voice");
        assertEquals(ThingAnswer.toTheWord("use", visuals), press, "exactly a right click's answer on the gate");
    }

    @Test
    void aPressTheGameSaysNothingOfIsAnsweredAsAnAttackAsEver() {
        var strike = new CommandButton("power:strike", null, "Strike", "S", true, CommandButton.Aim.UNIT, null);

        assertEquals(new ThingAnswer(OrderMarkers.Kind.ATTACK, false, "power:strike"),
                ThingAnswer.toThePress(strike, ringing()), "the attack's ring, and the button's own name voiced");
        assertEquals(new ThingAnswer(OrderMarkers.Kind.ATTACK, false, "use:3"),
                ThingAnswer.toThePress(KEY.answeredAs("use").answeredAs(null), ringing()), "answered as none again");
    }

    @Test
    void theWordsMarkIsTheGames() {
        var visuals = ringing().wordMark("open", Visuals.WordMark.FLASH).wordMark("take", Visuals.WordMark.NONE)
                .orderAnswer("take", null);

        assertEquals(new ThingAnswer(null, true, "move"), ThingAnswer.toThePress(KEY.answeredAs("open"), visuals),
                "the gate flashed as when it is selected, and a word with no voice of its own answered as a move");
        assertEquals(new ThingAnswer(null, false, null), ThingAnswer.toThePress(KEY.answeredAs("take"), visuals),
                "nothing drawn and nothing said");
    }

    @Test
    void aLookRingingNoOrdersOfTheGamesAnswersTheWordWithAMovesArrowheads() {
        var visuals = Visuals.create();

        assertEquals(new ThingAnswer(OrderMarkers.Kind.MOVE, false, "move"),
                ThingAnswer.toThePress(KEY.answeredAs("use"), visuals));
        assertEquals(ThingAnswer.toTheWord("use", visuals), ThingAnswer.toThePress(KEY.answeredAs("use"), visuals));
    }
}
