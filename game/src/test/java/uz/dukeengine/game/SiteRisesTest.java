package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.view.UnitView;
import uz.dukeengine.rts.message.GameMessage;

/** How far a building is built reaches the client from the simulation's own progress, going up and coming down. */
class SiteRisesTest {

    private static final String UNITS = """
            Object
              Name = Dozer
              KindOf = [SELECTABLE]
              Geometry = Cylinder
                Radius = 3
                Height = 6
              End
              Modules = [
                ActiveBody
                  MaxHealth = 100
                End,
                MoveUpdate
                  Speed = 60
                End
              ]
            End
            Object
              Name = Barracks
              KindOf = [STRUCTURE, SELECTABLE]
              BuildCost = 100
              BuildTime = 1
              Geometry = Box
                MajorRadius = 9
                MinorRadius = 9
                Height = 16
              End
              Modules = [
                ActiveBody
                  MaxHealth = 600
                End
              ]
            End
            """;

    private static UnitView barracks(DukeGame game) {
        return game.getSnapshot().units().stream().filter(view -> view.templateName().equals("Barracks"))
                .findFirst().orElse(null);
    }

    @Test
    void aSiteIsSeenRisingFromNothingToWholeAndASoldBuildingSinkingBelowIt() {
        var game = DukeGame.create("Rising").loadUnits(UNITS).map(40, 40);
        var me = game.addPlayer("Me", Color.CYAN);
        game.localPlayer(me).spawn("Dozer", me, 100f, 100f);
        game.money(me, 1000);
        game.runHeadless(1);
        var dozer = game.getSnapshot().units().getFirst();
        game.postCommand(new GameMessage.Construct(me.getIndex(), new uz.dukeengine.core.thing.ObjectId(dozer.id()),
                "Barracks", new Coord3D(140f, 100f, 0f), 0f));

        var seen = new ArrayList<Float>();
        for (int frame = 0; frame < 400; frame++) {
            game.runHeadless(1);
            var site = barracks(game);
            if (site != null) {
                seen.add(site.built());
                if (site.built() >= 1f) {
                    break;
                }
            }
        }
        assertTrue(seen.getFirst() < 0.1f, "it rises from nothing: " + seen.getFirst());
        for (int at = 1; at < seen.size(); at++) {
            assertTrue(seen.get(at) >= seen.get(at - 1), "and only up");
        }
        assertEquals(1f, seen.getLast(), "whole");

        game.postCommand(new GameMessage.Sell(me.getIndex(),
                new uz.dukeengine.core.thing.ObjectId(barracks(game).id())));
        game.runHeadless(10);
        assertTrue(barracks(game).built() < 1f, "sold, it comes down again: " + barracks(game).built());
    }
}
