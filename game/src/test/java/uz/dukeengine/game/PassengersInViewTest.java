package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rts.module.ContainModule;

/** What a transport holds reaches the client, in the order it was taken in. */
class PassengersInViewTest {

    private static final String UNITS = DukeGame.STARTER_UNITS + """
            Object
              Name = Humvee
              KindOf = [VEHICLE, SELECTABLE]
              Modules = [
                ActiveBody
                  MaxHealth = 240
                End,
                ContainModule
                  Slots = 5
                End
              ]
            End
            """;

    @Test
    void aTransportsViewListsItsPassengersInTheOrderTheyGotIn() {
        var game = DukeGame.create("passengers-test").loadUnits(UNITS).map(60, 40);
        var you = game.addPlayer("You", Color.BLUE);
        game.localPlayer(you);
        game.spawn("Humvee", you, 200, 200);
        game.spawn("Rifleman", you, 100, 100).spawn("Rifleman", you, 110, 100).spawn("Rifleman", you, 120, 100);
        game.runHeadless(1);
        var things = game.getLogic().getObjects();
        GameObject humvee = things.stream().filter(t -> t.getTemplate().name().equals("Humvee")).findFirst().orElseThrow();
        var riflemen = things.stream().filter(t -> t.getTemplate().name().equals("Rifleman")).toList();
        var hold = humvee.findModule(ContainModule.class);
        hold.load(riflemen.get(2));
        hold.load(riflemen.get(0));
        hold.load(riflemen.get(1));
        hold.unload(riflemen.get(0));

        game.runHeadless(1);

        var view = game.getSnapshot().units().stream().filter(u -> u.id() == humvee.getId().value())
                .findFirst().orElseThrow();
        assertEquals(List.of(riflemen.get(2).getId().value(), riflemen.get(1).getId().value()), view.passengers());
    }
}
