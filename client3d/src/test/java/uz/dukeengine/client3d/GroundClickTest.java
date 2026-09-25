package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.game.view.UnitView;
import uz.dukeengine.rts.message.GameMessage;

/**
 * A click on open ground gives a rally point only as the game says: a game that names ground orders decides them, a
 * thing that can move is moved, and a click nothing selected may take orders nothing and shows a refusal.
 */
class GroundClickTest {

    private static final int LOCAL = 1;
    private static final Vector3f GROUND = new Vector3f(300f, 0f, 200f); // x, height, y
    private static final Coord3D PLACE = new Coord3D(300f, 200f, 0f);

    /** A war factory of his with a production queue: a structure, which cannot move. */
    private static final UnitView FACTORY = new UnitView(7, "WarFactory", LOCAL, 100f, 100f, 0f, 1000f, 1000f,
            true, true, false, false, 0);
    /** A power plant of his: a structure with no queue. */
    private static final UnitView POWER = new UnitView(8, "PowerPlant", LOCAL, 160f, 100f, 0f, 800f, 800f,
            true, true, false, false, -1);
    /** A dozer of his, with a production queue of its own for its upgrades: it can move. */
    private static final UnitView DOZER = new UnitView(9, "Dozer", LOCAL, 120f, 100f, 0f, 250f, 250f,
            false, true, false, false, 0);

    private static Cursors.Over overOpenGround(boolean groundTakes, String word) {
        return new Cursors.Over(true, null, true, false, false, false, true, true, word, null, groundTakes);
    }

    @Test
    void aRuleAnsweringSetRallyPointForAFactoryShowsItsPointerAndSendsItsOrderNotTheClientsOwn() {
        var click = DukeRtsApp.groundClick(LOCAL, List.of(FACTORY), "SetRallyPoint", true, GROUND);

        assertEquals(new GameMessage.GameOrder(LOCAL, "SetRallyPoint", List.of(new ObjectId(7)),
                new Coord3D(300f, 200f, 0f), null, 0), click);
        assertEquals("SetRallyPoint", Cursors.situationFor(overOpenGround(false, "SetRallyPoint")));
    }

    @Test
    void theSameRuleAnsweringNothingForALoneDozerWithAQueueMovesIt() {
        var click = DukeRtsApp.groundClick(LOCAL, List.of(DOZER), null, true, GROUND);

        assertEquals(new GameMessage.MoveTo(LOCAL, List.of(new ObjectId(9)), PLACE, true), click);
    }

    @Test
    void inAGameWithNoRuleALoneDozerIsMovedToo() {
        var click = DukeRtsApp.groundClick(LOCAL, List.of(DOZER), null, false, GROUND);

        assertEquals(new GameMessage.MoveTo(LOCAL, List.of(new ObjectId(9)), PLACE, true), click);
    }

    @Test
    void aLoneBuildingWithAQueueAndNoWordShowsDenyAndSendsNothing() {
        assertNull(DukeRtsApp.groundClick(LOCAL, List.of(FACTORY), null, true, GROUND));
        assertEquals(Cursors.DENY, Cursors.situationFor(
                overOpenGround(DukeRtsApp.groundTakes(List.of(FACTORY), true), null)));
    }

    /**
     * Two buildings selected with no word: the move pointer, and a click answered as a move — its mark and the game's
     * move hint — with nobody in it and nothing sent, as the reference refuses the ground only to a lone structure.
     */
    @Test
    void twoBuildingsWithNoWordShowTheMovePointerAndAClickIsAnsweredAsAMoveOfNobody() {
        var both = List.of(FACTORY, POWER);
        assertEquals(Cursors.MOVE, Cursors.situationFor(overOpenGround(DukeRtsApp.groundTakes(both, true), null)));

        var click = DukeRtsApp.groundClick(LOCAL, both, null, true, GROUND);
        assertEquals(new GameMessage.MoveTo(LOCAL, List.of(), PLACE, true), click, "a move of nobody");
        var hint = OrderMark.DEFAULTS.model("models/scmovehint.glb", "SCMoveHint", 40);
        assertTrue(DukeRtsApp.hintsMove(hint, java.util.Set.of(7, 8), both), "the move hint laid");
    }

    @Test
    void inAGameWithNoRuleALoneFactoryStillTakesTheClickAsItsRallyPoint() {
        var click = DukeRtsApp.groundClick(LOCAL, List.of(FACTORY), null, false, GROUND);

        assertEquals(new GameMessage.SetRallyPoint(LOCAL, new ObjectId(7), PLACE), click);
        assertEquals(Cursors.MOVE, Cursors.situationFor(overOpenGround(true, null)));
    }

    @Test
    void aFactoryAndADozerTogetherMoveTheDozerAlone() {
        var click = DukeRtsApp.groundClick(LOCAL, List.of(FACTORY, DOZER), null, false, GROUND);

        assertEquals(new GameMessage.MoveTo(LOCAL, List.of(new ObjectId(9)), PLACE, true), click);
    }
}
