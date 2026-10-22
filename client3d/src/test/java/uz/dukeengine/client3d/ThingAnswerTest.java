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

        var press = ThingAnswer.toThePress(KEY.answeredAs("use"), visuals, null);

        assertEquals(new ThingAnswer(OrderMarkers.Kind.CONTEXT, false, "use"), press,
                "the ring of the game's own orders, in its colour, and the word's voice");
        assertEquals(ThingAnswer.toTheWord("use", visuals, null), press,
                "exactly a right click's answer on the gate");
    }

    @Test
    void aPressTheGameSaysNothingOfIsAnsweredAsAnAttackAsEver() {
        var strike = new CommandButton("power:strike", null, "Strike", "S", true, CommandButton.Aim.UNIT, null);

        assertEquals(new ThingAnswer(OrderMarkers.Kind.ATTACK, false, "power:strike"),
                ThingAnswer.toThePress(strike, ringing(), null), "the attack's ring, and the button's own name voiced");
        assertEquals(new ThingAnswer(OrderMarkers.Kind.ATTACK, false, "use:3"),
                ThingAnswer.toThePress(KEY.answeredAs("use").answeredAs(null), ringing(), null),
                "answered as none again");
    }

    @Test
    void theWordsMarkIsTheGames() {
        var visuals = ringing().wordMark("open", Visuals.WordMark.FLASH).wordMark("take", Visuals.WordMark.NONE)
                .orderAnswer("take", null);

        assertEquals(new ThingAnswer(null, true, "move"),
                ThingAnswer.toThePress(KEY.answeredAs("open"), visuals, null),
                "the gate flashed as when it is selected, and a word with no voice of its own answered as a move");
        assertEquals(new ThingAnswer(null, false, null), ThingAnswer.toThePress(KEY.answeredAs("take"), visuals, null),
                "nothing drawn and nothing said");
    }

    @Test
    void aLookRingingNoOrdersOfTheGamesAnswersTheWordWithAMovesArrowheads() {
        var visuals = Visuals.create();

        assertEquals(new ThingAnswer(OrderMarkers.Kind.MOVE, false, "move"),
                ThingAnswer.toThePress(KEY.answeredAs("use"), visuals, null));
        assertEquals(ThingAnswer.toTheWord("use", visuals, null),
                ThingAnswer.toThePress(KEY.answeredAs("use"), visuals, null));
    }

    /** One word answered two ways by what it was aimed at, its own answer where the rule gives none. */
    @Test
    void aWordIsAnsweredByItsTarget() {
        var visuals = Visuals.create().orderAnswer("attack", "attack")
                .orderAnswerBy("attack", target -> target != null && target.airborne() ? "attackair" : null);
        var helicopter = new AnswerTarget("Comanche", AnswerTarget.Relation.HOSTILE, true);
        var tank = new AnswerTarget("Crusader", AnswerTarget.Relation.HOSTILE, false);

        assertEquals("attackair", ThingAnswer.toTheWord("attack", visuals, helicopter).voice());
        assertEquals("attack", ThingAnswer.toTheWord("attack", visuals, tank).voice());
        assertEquals("attack", visuals.orderAnswerFor("attack", null), "on the ground");
    }

    /** The reference's VoiceMoveUpgraded, and VoiceClearBuilding against VoiceSubdue: the same word, read two ways. */
    @Test
    void aWordIsAnsweredByWhatTheSelectionHoldsAndWhatTheTargetHolds() {
        var visuals = Visuals.create()
                .orderAnswerBy("move", (_, selection) -> selection != null
                        && selection.conditions().contains("upgraded") ? "moveupgraded" : null)
                .orderAnswerBy("enter", (target, _) -> target == null || !target.structure() ? null
                        : target.conditions().contains("occupied") ? "clear" : "subdue");
        var upgraded = new AnswerSelection("Ranger", java.util.Set.of("upgraded"));
        var plain = new AnswerSelection("Ranger", java.util.Set.of());
        var occupied = new AnswerTarget("Barracks", AnswerTarget.Relation.HOSTILE, false,
                java.util.Set.of("occupied"), true);
        var empty = new AnswerTarget("Barracks", AnswerTarget.Relation.HOSTILE, false, java.util.Set.of(), true);

        assertEquals("moveupgraded", ThingAnswer.toTheWord("move", visuals, null, upgraded).voice());
        assertEquals("move", ThingAnswer.toTheWord("move", visuals, null, plain).voice());
        assertEquals("move", ThingAnswer.toTheWord("move", visuals, null).voice(), "none selected: the word's own");
        assertEquals("clear", ThingAnswer.toTheWord("enter", visuals, occupied, plain).voice());
        assertEquals("subdue", ThingAnswer.toTheWord("enter", visuals, empty, plain).voice());
    }

    @Test
    void theFormThatReadsBothComesFirstAndTheOldFormIsTheFallback() {
        var visuals = Visuals.create()
                .orderAnswerBy("attack", target -> target != null && target.airborne() ? "attackair" : null)
                .orderAnswerBy("attack", (target, _) -> target != null && target.structure() ? "bombard" : null);
        var helicopter = new AnswerTarget("Comanche", AnswerTarget.Relation.HOSTILE, true);
        var bunker = new AnswerTarget("Bunker", AnswerTarget.Relation.HOSTILE, false, java.util.Set.of(), true);

        assertEquals("bombard", ThingAnswer.toTheWord("attack", visuals, bunker, null).voice());
        assertEquals("attackair", ThingAnswer.toTheWord("attack", visuals, helicopter, null).voice());
    }
}
