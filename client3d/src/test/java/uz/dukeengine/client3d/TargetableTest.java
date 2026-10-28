package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.player.PlayerList;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.game.DukeGame;

/** A neutral thing the game made a target ({@code objectTargetable}): a plain click attacks it, and no other neutral. */
class TargetableTest {

    @Test
    void aPlainClickAttacksATargetableNeutralAndNoOtherNeutralNorItsOwn() {
        var game = DukeGame.create("targetable").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40);
        var me = game.addPlayer("Me", Color.BLUE);
        var them = game.addPlayer("Them", Color.RED);
        game.localPlayer(me).spawn("Rifleman", me, 100f, 100f).spawn("Rifleman", them, 120f, 100f)
                .spawnNeutral("Rifleman", 100f, 120f).spawnNeutral("Rifleman", 110f, 110f);
        game.runHeadless(1);
        var neutrals = game.getLogic().getObjects().stream()
                .filter(thing -> thing.getPlayerIndex() == PlayerList.NEUTRAL_INDEX).toList();
        neutrals.getFirst().setStatus(ObjectStatus.TARGETABLE);
        game.runHeadless(1);

        int local = game.getLocalPlayerIndex();
        assertTrue(Orders.attackedOnAClick(view(game, neutrals.getFirst().getId().value()), local),
                "the barrel the map made a target");
        assertFalse(Orders.attackedOnAClick(view(game, neutrals.get(1).getId().value()), local),
                "any other neutral's thing, as ever");
        assertTrue(Orders.attackedOnAClick(viewOf(game, them.getIndex()), local), "an enemy's, as ever");
        assertFalse(Orders.attackedOnAClick(viewOf(game, local), local), "nor his own");
    }

    private static UnitView view(DukeGame game, int id) {
        return game.getSnapshot().units().stream().filter(unit -> unit.id() == id).findFirst().orElseThrow();
    }

    private static UnitView viewOf(DukeGame game, int player) {
        return game.getSnapshot().units().stream().filter(unit -> unit.playerIndex() == player).findFirst()
                .orElseThrow();
    }
}
