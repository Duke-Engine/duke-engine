package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The nearest thing worth an order in front of him, found on each snapshot; turned round, the one behind (E94). */
class InteractTest {

    @Test
    void theTreeBeforeHimThenTheWellBehind() {
        var game = DukeGame.create("interact").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40);
        var me = game.addPlayer("Me", Color.BLUE);
        var world = game.addPlayer("World", Color.GRAY);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.spawn("Tank", world, 110f, 100f); // the "tree", ten before him
        game.spawn("Rifleman", world, 90f, 100f); // the "well", ten behind
        game.contextOrder((selection, target) -> target.getTemplate().name().equals("Tank") ? "Chop" : "Fill");
        game.interactWithin(14f, (float) Math.toRadians(100));
        game.runHeadless(1);
        var hero = game.getLogic().getObjects().getFirst();
        game.setSelection(List.of(hero.getId().value()));
        hero.setOrientation(0f); // facing +x
        game.runHeadless(2);
        assertEquals("Chop", game.interactTarget().word(), "the tree before him");

        hero.setOrientation((float) Math.PI);
        game.runHeadless(2);
        assertEquals("Fill", game.interactTarget().word(), "turned round: the well");

        game.interactWithin(0f, 0f);
        game.runHeadless(2);
        assertNull(game.interactTarget(), "none looked for");
    }

    /** A scenery tree is offered by the ground rule under it, and a piece the rule names nothing for is not (E96). */
    @Test
    void aSceneryTreeIsFoundByTheGroundRule() {
        var game = DukeGame.create("interact").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40);
        var me = game.addPlayer("Me", Color.BLUE);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f);
        game.groundOrder((selection, place) -> place.x() > 108f ? "Chop" : null);
        game.interactWithin(14f, (float) Math.toRadians(100));
        game.runHeadless(1);
        var hero = game.getLogic().getObjects().getFirst();
        game.getLogic().setSceneryFootprints(List.of(
                new uz.dukeengine.core.pathfind.SceneryFootprint(110f, 100f, 3f, 0),
                new uz.dukeengine.core.pathfind.SceneryFootprint(105f, 101f, 2f, 1)));
        game.setSelection(List.of(hero.getId().value()));
        hero.setOrientation(0f);
        game.runHeadless(2);

        var target = game.interactTarget();
        assertEquals("Chop", target.word(), "the tree, past the rock the rule names nothing for");
        assertEquals(-1, target.unit());
        assertEquals(110f, target.x());
    }
}
