package uz.duke.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.rts.message.GameMessage;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.ObjectId;

/** The in-game production flow, command-driven — what the build menu does. */
class ProductionCommandTest {

    @Test
    void queueCommandChargesBuildsAndRalliesTheUnit() {
        var game = DukeGame.create("t").loadUnits(DukeGame.STARTER_UNITS);
        var you = game.addPlayer("You", Color.BLUE);
        game.money(you, 500);
        game.spawn("Barracks", you, 100, 100);   // id 1 — consumes power,
        game.spawn("PowerPlant", you, 60, 100);  // id 2 — so it needs a plant

        game.onStart(g -> {
            g.postCommand(new GameMessage.SetRallyPoint(you.getIndex(),
                    new ObjectId(1), new Coord3D(200f, 100f, 0f)));
            g.postCommand(new GameMessage.QueueProduction(you.getIndex(),
                    new ObjectId(1), "Rifleman"));
        });
        game.runHeadless(60); // rifleman build time is 1.5s = 45 frames

        assertEquals(500 - 120, game.getLogic().getRtsPlayer(you.getIndex()).getMoney(),
                "queueing charges the rifleman's cost");
        assertEquals(3, game.getLogic().getObjectCount(), "the rifleman was produced");

        var produced = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Rifleman"))
                .findFirst().orElseThrow();
        boolean movingToRally = produced.findModule(MoveUpdate.class).isMoving()
                || produced.getPosition().distance(new Coord3D(200f, 100f, 0f)) < 15f;
        assertTrue(movingToRally, "the produced unit heads to the rally point");
    }

    @Test
    void buildMenuIsTheContract() {
        var game = DukeGame.create("t").loadUnits(DukeGame.STARTER_UNITS);
        var you = game.addPlayer("You", Color.BLUE);
        game.money(you, 5000);
        game.spawn("Barracks", you, 100, 100); // id 1; Builds = Rifleman Tank

        game.onStart(g -> g.postCommand(new GameMessage.QueueProduction(you.getIndex(),
                new ObjectId(1), "PowerPlant"))); // structures are not on the menu
        game.runHeadless(10);

        assertEquals(5000, game.getLogic().getRtsPlayer(you.getIndex()).getMoney(),
                "off-menu requests are refused and never charged");
        assertEquals(1, game.getLogic().getObjectCount());
    }

    @Test
    void otherPlayersCannotUseYourFactory() {
        var game = DukeGame.create("t").loadUnits(DukeGame.STARTER_UNITS);
        var you = game.addPlayer("You", Color.BLUE);
        var foe = game.addPlayer("Foe", Color.RED);
        game.enemies(you, foe);
        game.money(you, 500).money(foe, 500);
        game.spawn("Barracks", you, 100, 100); // id 1, yours

        game.onStart(g -> g.postCommand(new GameMessage.QueueProduction(foe.getIndex(),
                new ObjectId(1), "Rifleman")));
        game.runHeadless(60);

        assertEquals(500, game.getLogic().getRtsPlayer(foe.getIndex()).getMoney());
        assertEquals(1, game.getLogic().getObjectCount(), "no unit for the enemy");
    }

    @Test
    void buildOptionsExposeTheMenuForUis() {
        var game = DukeGame.create("t").loadUnits(DukeGame.STARTER_UNITS);
        game.addPlayer("You", Color.BLUE);
        game.runHeadless(1);

        var options = game.getBuildOptions("Barracks");
        assertEquals(2, options.size());
        assertEquals("Rifleman", options.get(0).templateName());
        assertEquals(120, options.get(0).cost());
        assertEquals("Battle Tank", options.get(1).displayName());
    }
}
