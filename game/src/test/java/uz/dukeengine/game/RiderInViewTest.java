package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.rts.module.ContainModule;

/** A rider is shown, on what it rides, and clicked as that: never selectable itself. */
class RiderInViewTest {

    @Test
    void aRiderIsInTheViewRidingOnItsCarrierAndCannotBeSelectedItself() {
        var game = DukeGame.create("overlord").loadUnits(DukeGame.STARTER_UNITS).map(40, 40);
        var china = game.addPlayer("China", Color.RED);
        game.localPlayer(china);
        game.spawn("Barracks", china, 100f, 100f);
        game.spawn("Rifleman", china, 100f, 100f);
        game.runHeadless(1);
        var carrier = game.getLogic().getObjects().get(0);
        var rider = game.getLogic().getObjects().get(1);
        var hold = new ContainModule(carrier, new ContainModule.Data(1, null, false, "GUNNER"));
        carrier.addModule(hold);
        hold.load(rider);
        game.runHeadless(1);

        var view = game.getSnapshot().units().stream().filter(one -> one.id() == rider.getId().value()).findFirst()
                .orElseThrow();
        assertEquals(carrier.getId().value(), view.ridesOn(), "on what it rides");
        assertFalse(view.selectable(), "clicked as its carrier, never itself");
    }
}
