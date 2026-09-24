package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.message.GameMessage;

/**
 * An order nobody could have given from the bar — a unit the side may not make yet — refused on every machine alike:
 * the money stays where it was and the worlds agree.
 */
class RefusedOrderTest {

    private static final String UNITS = """
            Object
              Name = Headquarters
              Modules = [
                ActiveBody
                  MaxHealth = 2000
                End
              ]
            End

            Object
              Name = Barracks
              BuildCost = 300
              Modules = [
                ActiveBody
                  MaxHealth = 800
                End,
                ProductionUpdate
                  Builds = [Commando]
                End
              ]
            End

            Object
              Name = Commando
              BuildCost = 1000
              BuildTime = 1
              Prerequisites = [Headquarters]
              Modules = [
                ActiveBody
                  MaxHealth = 150
                End
              ]
            End
            """;

    private static DukeGame match(boolean headquarters) {
        var game = DukeGame.create("refused-order").loadUnits(UNITS);
        var one = game.addPlayer("One", Color.BLUE);
        game.addPlayer("Two", Color.RED);
        game.money(one, 2000);
        game.spawn("Barracks", one, 100, 100);
        if (headquarters) {
            game.spawn("Headquarters", one, 300, 100);
        }
        return game;
    }

    @Test
    void aCraftedOrderForARefusedUnitLeavesTheMoneyAndTheWorldsAgree() {
        var first = match(false);
        var second = match(false);
        first.runHeadless(1);
        second.runHeadless(1);

        var crafted = new GameMessage.QueueProduction(1, new ObjectId(1), "Commando");
        first.postCommand(crafted);
        second.postCommand(crafted);
        first.runHeadless(60);
        second.runHeadless(60);

        assertEquals(2000, first.getLogic().getRtsPlayer(1).getMoney(), "nothing charged: no headquarters stands");
        assertEquals(2000, second.getLogic().getRtsPlayer(1).getMoney());
        assertEquals(1, first.getLogic().getObjects().size(), "and nothing made");
        assertEquals(first.getLogic().checksum(), second.getLogic().checksum());
    }

    @Test
    void withTheHeadquartersStandingTheSameOrderIsTaken() {
        var game = match(true);
        game.runHeadless(1);

        game.postCommand(new GameMessage.QueueProduction(1, new ObjectId(1), "Commando"));
        game.runHeadless(60);

        assertEquals(1000, game.getLogic().getRtsPlayer(1).getMoney(), "charged, and made");
    }
}
