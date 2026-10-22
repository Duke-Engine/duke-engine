package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectRevived;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.module.ActiveBody;
import uz.dukeengine.core.module.KeepsDead;
import uz.dukeengine.core.module.Module;
import uz.dukeengine.core.pathfind.PathGrid;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.Span;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.rts.RtsTemplate;

/**
 * A bridge brought down and raised again — the reference's {@code BridgeScaffoldBehavior}: kept in the world dead, its
 * deck closed by the game as it falls, and brought back with {@code GameObject.revive}, its deck opened again and the
 * bridge drawn whole along its line.
 */
class RevivedSpanTest {

    /** Keeps a fallen bridge in the world, as a landmark bridge's ruin stays. */
    static final class Ruin extends Module implements KeepsDead {
        Ruin(GameObject owner) {
            super(owner);
        }

        @Override
        public boolean keepsDead() {
            return true;
        }
    }

    @Test
    void aBridgeKilledAndRevivedJoinsItsBanksAgainAndIsDrawnWholeAndARemovedThingIsRefused() {
        var game = DukeGame.create("Bridges").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS).map(40, 40)
                .addUnits(List.of(RtsTemplate.named("Bridge").module(new ActiveBody.Data(1000f)).build()));
        var me = game.addPlayer("Me", Color.BLUE);
        game.localPlayer(me).spawn("Bridge", me, 200f, 200f).spawn("Rifleman", me, 50f, 200f);
        game.runHeadless(1);
        var logic = game.getLogic();
        var bridge = logic.getObjects().stream().filter(o -> o.getTemplate().name().equals("Bridge"))
                .findFirst().orElseThrow();
        bridge.addModule(new Ruin(bridge));

        var grid = new PathGrid(40, 40);
        for (int cy = 0; cy < 40; cy++) {
            for (int cx = 15; cx < 25; cx++) {
                grid.setBlocked(cx, cy, true); // a river down the whole map
            }
        }
        logic.setPathGrid(grid);
        int deck = logic.addDeck(new Coord3D(130f, 180f, 20f), new Coord3D(130f, 220f, 20f),
                new Coord3D(270f, 220f, 20f), new Coord3D(270f, 180f, 20f));
        var line = new Span(new Coord3D(130f, 200f, 20f), new Coord3D(270f, 200f, 20f));
        bridge.setSpan(line);
        logic.onDied(died -> {
            if (died.object().equals(bridge.getId())) {
                logic.setDeckOpen(deck, false);
            }
        });
        logic.onRevived(thing -> {
            if (thing == bridge) {
                logic.setDeckOpen(deck, true);
            }
        });
        var near = new Coord3D(50f, 200f, 0f);
        var far = new Coord3D(350f, 200f, 0f);
        game.runHeadless(1);
        assertTrue(logic.joined(near, far), "standing, the bridge joins the banks");

        bridge.getBody().damage(5000f);
        game.runHeadless(1);
        assertTrue(bridge.hasDied() && logic.findObject(bridge.getId()) != null, "fallen, and kept in the world");
        assertFalse(logic.joined(near, far), "fallen, its deck closed: the banks apart");
        assertEquals(0f, viewOf(game).health(), "drawn fallen");

        assertTrue(bridge.revive(600f));
        game.runHeadless(1);

        assertTrue(logic.joined(near, far), "raised, the banks joined again");
        assertFalse(bridge.isEffectivelyDead() || bridge.hasDied());
        var view = viewOf(game);
        assertEquals(600f, view.health(), "drawn whole");
        assertEquals(line, view.span(), "along its line");
        assertTrue(game.getSnapshot().events().stream().anyMatch(event -> event instanceof ObjectRevived revived
                && revived.object().equals(bridge.getId())), "the picture told it is alive again");
        assertFalse(bridge.revive(600f), "a living thing is not brought back");

        bridge.getBody().damage(5000f);
        game.runHeadless(1);
        assertFalse(logic.joined(near, far), "killed again, it falls again");
        logic.destroyObject(bridge);
        game.runHeadless(1);
        assertFalse(bridge.revive(600f), "a thing removed is refused");
        assertTrue(logic.findObject(bridge.getId()) == null);
    }

    private static UnitView viewOf(DukeGame game) {
        return game.getSnapshot().units().stream().filter(view -> view.templateName().equals("Bridge"))
                .findFirst().orElseThrow();
    }
}
