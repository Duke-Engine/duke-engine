package uz.duke.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.rts.message.GameMessage;
import uz.duke.core.thing.ObjectId;

/** The Unity-style promise, verified headlessly: a game in a few lines that runs. */
class DukeGameTest {

    @Test
    void starterGameBootsSpawnsAndSnapshots() {
        var game = DukeGame.create("test")
                .loadUnits(DukeGame.STARTER_UNITS)
                .map(30, 20);
        var you = game.addPlayer("You", Color.BLUE);
        var foe = game.addPlayer("Foe", Color.RED);
        game.enemies(you, foe).money(you, 1000);
        game.spawn("Barracks", you, 50, 50)
                .spawn("Rifleman", you, 70, 50)
                .spawn("Tank", foe, 200, 150);

        game.runHeadless(10);

        assertEquals(3, game.getLogic().getObjectCount());
        assertEquals(1000, game.getLogic().getRtsPlayer(you.getIndex()).getMoney());

        var snapshot = game.getSnapshot();
        assertEquals(10, snapshot.frame());
        // Fog of war: the enemy tank at (200,150) is far outside vision range.
        assertEquals(2, snapshot.units().size());
        assertTrue(snapshot.units().stream().allMatch(u -> u.playerIndex() == you.getIndex()));
    }

    @Test
    void builtInCommandRoutingMovesUnits() {
        var game = DukeGame.create("test").loadUnits(DukeGame.STARTER_UNITS);
        var you = game.addPlayer("You", Color.BLUE);
        game.spawn("Rifleman", you, 0, 0);

        game.onStart(g -> g.postCommand(new GameMessage.MoveTo(
                you.getIndex(), List.of(new ObjectId(1)), new Coord3D(100f, 0f, 0f))));
        game.runHeadless(60); // 2 seconds at speed 14 → ~28 units of progress

        var unit = game.getLogic().findObject(new ObjectId(1));
        assertNotNull(unit);
        assertTrue(unit.getPosition().x() > 20f, "built-in MoveTo routing should move the unit");
    }

    @Test
    void unitsOfOtherPlayersCannotBeCommanded() {
        var game = DukeGame.create("test").loadUnits(DukeGame.STARTER_UNITS);
        var you = game.addPlayer("You", Color.BLUE);
        var foe = game.addPlayer("Foe", Color.RED);
        game.enemies(you, foe);
        game.spawn("Rifleman", foe, 0, 0); // id 1, owned by the enemy

        game.onStart(g -> g.postCommand(new GameMessage.MoveTo(
                you.getIndex(), List.of(new ObjectId(1)), new Coord3D(100f, 0f, 0f))));
        game.runHeadless(30);

        assertEquals(0f, game.getLogic().findObject(new ObjectId(1)).getPosition().x(), 1e-4f,
                "you cannot order another player's units around");
    }

    @Test
    void everySecondsAndDefeatCallbacksFire() {
        var game = DukeGame.create("test").loadUnits(DukeGame.STARTER_UNITS);
        var you = game.addPlayer("You", Color.BLUE);
        var foe = game.addPlayer("Foe", Color.RED);
        game.enemies(you, foe);
        game.spawn("Tank", you, 0, 0);
        game.spawn("Rifleman", foe, 20, 0); // within tank range 30 → auto-acquired

        var ticks = new AtomicInteger();
        var defeated = new AtomicReference<GamePlayer>();
        game.everySeconds(1, g -> ticks.incrementAndGet());
        game.onPlayerDefeated((g, p) -> defeated.set(p));

        game.runHeadless(121); // just past 4 seconds of game time

        assertEquals(4, ticks.get(), "everySeconds(1) fires once per game second");
        assertEquals(foe, defeated.get(), "the lone rifleman dies to the tank → foe annihilated");
    }

    @Test
    void unknownTemplateFailsWithHelpfulError() {
        var game = DukeGame.create("test").loadUnits(DukeGame.STARTER_UNITS);
        game.addPlayer("You", Color.BLUE);
        game.spawn("Battlecruiser", null, 0, 0);
        var error = assertThrows(IllegalArgumentException.class, () -> game.runHeadless(1));
        assertTrue(error.getMessage().contains("Battlecruiser"));
        assertTrue(error.getMessage().contains("loadUnits"));
    }

    @Test
    void configurationAfterStartIsRejected() {
        var game = DukeGame.create("test").loadUnits(DukeGame.STARTER_UNITS);
        game.addPlayer("You", Color.BLUE);
        game.runHeadless(1);
        assertThrows(IllegalStateException.class, () -> game.map(10, 10));
        assertThrows(IllegalStateException.class, () -> game.loadUnits("Object X\nEnd"));
    }

    @Test
    void moveOrderOverridesCurrentTarget() {
        var game = DukeGame.create("test").loadUnits(DukeGame.STARTER_UNITS);
        var you = game.addPlayer("You", Color.BLUE);
        var foe = game.addPlayer("Foe", Color.RED);
        game.enemies(you, foe);
        game.spawn("Tank", you, 0, 0);        // id 1
        game.spawn("Barracks", foe, 25, 0);   // id 2, in range

        game.onStart(g -> g.postCommand(new GameMessage.AttackObject(
                you.getIndex(), List.of(new ObjectId(1)), new ObjectId(2))));
        game.everySeconds(1, g -> g.postCommand(new GameMessage.MoveTo(
                you.getIndex(), List.of(new ObjectId(1)), new Coord3D(0f, 100f, 0f))));
        game.runHeadless(40);

        var tank = game.getLogic().findObject(new ObjectId(1));
        assertTrue(tank.getPosition().y() > 1f, "explicit move order should take over from attacking");
        assertFalse(game.getSnapshot().units().isEmpty());
    }
}
