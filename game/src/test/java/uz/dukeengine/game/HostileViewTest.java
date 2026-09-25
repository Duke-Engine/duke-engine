package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import org.junit.jupiter.api.Test;

/** Whether a view's viewer takes its thing's owner for an enemy: not its owner, its ally, nor a neutral — its enemy. */
class HostileViewTest {

    private static boolean hostileTo(int viewer) {
        var game = DukeGame.create("Sides").loadUnits(DukeGame.STARTER_UNITS).map(60, 60);
        var owner = game.addPlayer("GLA", Color.GREEN);
        var ally = game.addPlayer("Ally", Color.YELLOW);
        var enemy = game.addPlayer("USA", Color.BLUE);
        var neutral = game.addPlayer("Neutral", Color.GRAY);
        var viewing = switch (viewer) {
            case 0 -> owner;
            case 1 -> ally;
            case 2 -> enemy;
            default -> neutral;
        };
        game.allies(owner, ally).enemies(owner, enemy).enemies(ally, enemy).localPlayer(viewing);
        game.spawn("Rifleman", owner, 100f, 100f);
        if (viewing != owner) {
            game.spawn("Rifleman", viewing, 110f, 100f); // near enough to see it
        }
        game.runHeadless(1);
        return game.getSnapshot().units().stream().filter(view -> view.playerIndex() == owner.getIndex())
                .findFirst().orElseThrow().hostile();
    }

    @Test
    void onlyItsOwnersEnemyTakesItForAnEnemy() {
        assertEquals(false, hostileTo(0), "its owner");
        assertEquals(false, hostileTo(1), "his ally");
        assertEquals(true, hostileTo(2), "his enemy");
        assertEquals(false, hostileTo(3), "a neutral");
    }
}
