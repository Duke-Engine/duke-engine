package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.view.UnitView;

/**
 * A thing drawn as another: the other template's look to every viewer, in the colours of the player it is disguised as
 * to an enemy and in its own to its side — the reference's disguised bomb truck.
 */
class DrawnAsTest {

    private static UnitView truckSeenBy(int viewer) {
        var game = DukeGame.create("Disguise").loadUnits(DukeGame.STARTER_UNITS).map(60, 60);
        var gla = game.addPlayer("GLA", Color.GREEN);
        var usa = game.addPlayer("USA", Color.BLUE);
        game.enemies(gla, usa).localPlayer(viewer == 1 ? gla : usa);
        game.spawn("Rifleman", gla, 100f, 100f).spawn("Tank", usa, 120f, 100f);
        game.runHeadless(1);
        game.runOnSimThread(() -> {
            var truck = game.getLogic().getObjects().getFirst();
            truck.drawAs("Tank", usa.getIndex());
            truck.setDrawnOpacity(0.5f);
        });
        game.runHeadless(1);
        return game.getSnapshot().units().stream().filter(view -> view.templateName().equals("Rifleman"))
                .findFirst().orElseThrow();
    }

    @Test
    void itShowsTheOtherModelToBothSidesInTheOtherPlayersColourToAnEnemyAndItsOwnToItsSide() {
        var toItsSide = truckSeenBy(1);
        var toTheEnemy = truckSeenBy(2);

        assertEquals("Tank", toItsSide.looksAs(), "the other template's look to its side");
        assertEquals("Tank", toTheEnemy.looksAs(), "and to the enemy");
        assertEquals(toItsSide.playerIndex(), toItsSide.wears(), "its own colours to its side");
        assertEquals(2, toTheEnemy.wears(), "the player it is disguised as, to the enemy");
        assertEquals("Rifleman", toTheEnemy.templateName(), "and what it is stays what it is");
        assertEquals(0.5f, toTheEnemy.opacity());
    }
}
