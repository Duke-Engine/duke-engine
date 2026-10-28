package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * The answer to a click that gives the game's word, named by the game: a supply truck sent to a depot says its supply
 * line, a steered power says nothing on the click, and a word it names nothing for is answered as a move, as always.
 */
class OrderAnswerTest {

    @Test
    void aWordAnsweredWithSupplyNoneOrNothingNamed() {
        var visuals = Visuals.create().orderAnswer("Dock", "supply").orderAnswer("Steer", null);

        assertEquals("supply", visuals.orderAnswerFor("Dock"), "ordered.supply.<first selected>");
        assertNull(visuals.orderAnswerFor("Steer"), "nothing played");
        assertEquals("move", visuals.orderAnswerFor("Enter"), "no answer named: ordered.move.<first selected>, as now");
    }

    private static final AnswerTarget PLANE = new AnswerTarget("Raptor", AnswerTarget.Relation.HOSTILE, true);
    private static final AnswerTarget TANK = new AnswerTarget("Crusader", AnswerTarget.Relation.HOSTILE, false);

    @Test
    void thePlainOrdersAnswerAsTheyAlwaysDidWhereTheGameNamesNothing() {
        var visuals = Visuals.create();

        assertEquals("attack", Orders.plainAnswer(visuals, Orders.ATTACK, TANK, null));
        assertEquals("attack", Orders.plainAnswer(visuals, Orders.ATTACK_GROUND, null, null));
        assertEquals("move", Orders.plainAnswer(visuals, Orders.MOVE, null, null));
        assertEquals("move", Orders.plainAnswer(visuals, Orders.FORCE_MOVE, TANK, null));
    }

    @Test
    void anAttackOnAnAircraftAnswersByTheGamesRule() {
        var visuals = Visuals.create().orderAnswerBy(Orders.ATTACK,
                (java.util.function.Function<AnswerTarget, String>) target -> target.airborne() ? "attackair" : null);

        assertEquals("attackair", Orders.plainAnswer(visuals, Orders.ATTACK, PLANE, null),
                "ordered.attackair.<template>");
        assertEquals("attack", Orders.plainAnswer(visuals, Orders.ATTACK, TANK, null), "ordered.attack.<template>");
    }

    @Test
    void aForcedMoveOntoWhatTheSelectionCrushesAnswersCrushAndAPlainMoveCanBeSilent() {
        var tank = new AnswerSelection("Crusader", java.util.Set.of());
        var visuals = Visuals.create().orderAnswer(Orders.MOVE, null).orderAnswerBy(Orders.FORCE_MOVE,
                (target, selection) -> target != null && selection != null ? "crush" : null);

        assertEquals("crush", Orders.plainAnswer(visuals, Orders.FORCE_MOVE, TANK, tank));
        assertEquals("move", Orders.plainAnswer(visuals, Orders.FORCE_MOVE, null, tank), "onto open ground");
        assertNull(Orders.plainAnswer(visuals, Orders.MOVE, null, tank), "named silent");
        assertEquals(true, KeyMap.Control.FORCE_MOVE.isHeld(), "held while the click is given");
    }
}
