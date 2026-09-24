package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.core.thing.ObjectStatus;
import uz.dukeengine.rts.message.GameMessage;

/** Deaths told to the game as they happen, credited to the side that dealt them; a sale is no death. */
class DeathsHeardTest {

    private static GameObject found(DukeGame game, String template, int player) {
        return game.getLogic().getObjects().stream()
                .filter(thing -> thing.getTemplate().name().equals(template) && thing.getPlayerIndex() == player)
                .findFirst().orElseThrow();
    }

    @Test
    void aTankOfTheSecondSideKillingOneOfTheThirdIsHeardOnceWithTheSecondAsItsKiller() {
        var game = DukeGame.create("deaths-test").loadUnits(DukeGame.STARTER_UNITS).map(80, 40);
        var one = game.addPlayer("One", Color.BLUE);
        var two = game.addPlayer("Two", Color.RED);
        var three = game.addPlayer("Three", Color.GREEN);
        game.enemies(one, two).enemies(two, three).enemies(one, three);
        game.spawn("Rifleman", one, 700, 300);
        game.spawn("Tank", two, 100, 100);
        game.spawn("Tank", three, 125, 100);
        var heard = new CopyOnWriteArrayList<ObjectDied>();
        game.onDied(heard::add);
        game.runHeadless(1);
        found(game, "Tank", 3).setStatus(ObjectStatus.DISABLED); // a sitting target, so only one side fights
        var killer = found(game, "Tank", 2).getId();

        for (int frame = 0; frame < 900 && heard.isEmpty(); frame++) {
            game.runHeadless(1);
        }
        game.runHeadless(30);

        assertEquals(1, heard.size(), "heard once");
        var died = heard.getFirst();
        assertEquals("Tank", died.templateName());
        assertEquals(3, died.playerIndex());
        assertEquals(killer, died.killer());
        assertEquals(2, died.killerPlayerIndex(), "credited to the side whose tank fired");
    }

    @Test
    void aBuildingSoldIsNotHeardAsADeath() {
        var game = DukeGame.create("sold-test").loadUnits(DukeGame.STARTER_UNITS).map(80, 40);
        var one = game.addPlayer("One", Color.BLUE);
        game.localPlayer(one);
        game.spawn("Barracks", one, 200, 200);
        var died = new CopyOnWriteArrayList<ObjectDied>();
        var sold = new CopyOnWriteArrayList<GameObject>();
        game.onDied(died::add).onSold(sold::add);
        game.runHeadless(1);
        var barracks = found(game, "Barracks", 1).getId();

        game.postCommand(new GameMessage.Sell(1, barracks));
        game.runHeadless(300);

        assertEquals(1, sold.size(), "sold, and down");
        assertTrue(died.isEmpty(), "and not a death: nobody destroyed it");
        assertNull(game.getLogic().findObject(barracks));
    }
}
