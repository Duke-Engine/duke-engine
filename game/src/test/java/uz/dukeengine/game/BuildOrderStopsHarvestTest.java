package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.HarvestUpdate;
import uz.dukeengine.rts.module.SupplyModule;

/** A worker loading at its pile and told to build drops its supply work, as the reference's does: it builds. */
class BuildOrderStopsHarvestTest {

    private static final String UNITS = """
            Object
              Name = Worker
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
                End,
                HarvestUpdate
                  LoadPerTrip = 100
                  FramesPerUnit = 10
                  UnitOfLoad = 10
                End
              ]
            End
            Object
              Name = Pile
              Geometry = Cylinder
                Radius = 5
                Height = 5
              End
              Modules = [
                SupplyModule
                  Amount = 1000
                End
              ]
            End
            Object
              Name = Depot
              KindOf = [STRUCTURE]
              Geometry = Box
                MajorRadius = 10
                MinorRadius = 10
                Height = 8
              End
              Modules = [
                SupplyDepot
                End
              ]
            End
            Object
              Name = Barracks
              KindOf = [STRUCTURE, SELECTABLE]
              BuildCost = 100
              BuildTime = 2
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

    private static GameObject named(DukeGame game, String template) {
        return game.getLogic().getObjects().stream().filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    @Test
    void aHarvestingBuilderToldToBuildPutsTheBuildingUpAndTakesNoMoreLoad() {
        var game = DukeGame.create("Building").loadUnits(UNITS).map(60, 60);
        var me = game.addPlayer("Me", Color.CYAN);
        var local = game.localPlayer(me);
        local.spawn("Depot", me, 100f, 100f);
        local.spawn("Pile", me, 400f, 100f);
        local.spawn("Worker", me, 390f, 100f);
        game.money(me, 1000);
        game.runHeadless(40);
        var worker = named(game, "Worker");
        var harvest = worker.findModule(HarvestUpdate.class);
        var pile = named(game, "Pile").findModule(SupplyModule.class);
        assertTrue(harvest.getCarrying() > 0, "loading at its pile");

        game.postCommand(new GameMessage.Construct(me.getIndex(), worker.getId(), "Barracks",
                new Coord3D(400f, 300f, 0f), 0f));
        game.runHeadless(1);
        int carried = harvest.getCarrying();
        int left = pile.getRemaining();

        int frame = 0;
        for (; frame < 1500; frame++) {
            var site = named(game, "Barracks");
            if (site != null && !site.hasStatus(uz.dukeengine.core.thing.ObjectStatus.UNDER_CONSTRUCTION)) {
                break;
            }
            game.runHeadless(1);
        }
        assertTrue(frame < 1500, "the barracks went up");
        game.runHeadless(300);
        assertEquals(carried, harvest.getCarrying(), "it keeps what it carried, and took no more");
        assertEquals(left, pile.getRemaining());
        assertTrue(harvest.isPaused());
        assertTrue(worker.getPosition().distance(new Coord3D(400f, 100f, 0f)) > 100f, "nor went back to the pile");
    }

    /** A build order refused — no money — is not told: the worker goes on with its supply work. */
    @Test
    void aBuildOrderRefusedLeavesTheWorkAsItWas() {
        var game = DukeGame.create("Broke").loadUnits(UNITS).map(60, 60);
        var me = game.addPlayer("Me", Color.CYAN);
        var local = game.localPlayer(me);
        local.spawn("Depot", me, 100f, 100f);
        local.spawn("Pile", me, 400f, 100f);
        local.spawn("Worker", me, 390f, 100f);
        game.runHeadless(40);
        var worker = named(game, "Worker");
        game.postCommand(new GameMessage.Construct(me.getIndex(), worker.getId(), "Barracks",
                new Coord3D(400f, 300f, 0f), 0f));
        game.runHeadless(1);

        assertTrue(!worker.findModule(HarvestUpdate.class).isPaused());
    }
}
