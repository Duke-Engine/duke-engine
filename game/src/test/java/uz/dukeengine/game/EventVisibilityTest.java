package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.event.ObjectDied;
import uz.dukeengine.core.thing.GameObject;

/** Fog of war applies to what happened, not just to what is. */
class EventVisibilityTest {

    private static GameObject enemyRifleman(DukeGame game, int foeIndex) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getPlayerIndex() == foeIndex)
                .findFirst()
                .orElseThrow();
    }

    private static DukeGame twoRiflemen(float enemyX, float enemyY) {
        var game = DukeGame.create("fog")
                .loadUnits(DukeGame.STARTER_UNITS)
                .map(70, 45);
        var you = game.addPlayer("You", Color.BLUE);
        var foe = game.addPlayer("Foe", Color.RED);
        game.enemies(you, foe).localPlayer(you);
        game.spawn("Rifleman", you, 50f, 50f);      // vision range 40
        game.spawn("Rifleman", foe, enemyX, enemyY);
        return game;
    }

    @Test
    void aDeathYouCanSeeIsReported() {
        var game = twoRiflemen(80f, 50f); // 30 away: well inside vision
        game.runHeadless(1);
        enemyRifleman(game, 2).getBody().damage(999f);

        game.runHeadless(1);

        assertEquals(1, game.getSnapshot().events().stream()
                .filter(ObjectDied.class::isInstance).count());
    }

    @Test
    void aDeathInTheDarkIsNotReported() {
        var game = twoRiflemen(600f, 400f); // far outside vision
        game.runHeadless(1);
        enemyRifleman(game, 2).getBody().damage(999f);

        game.runHeadless(1);

        assertTrue(game.getSnapshot().events().stream()
                        .noneMatch(ObjectDied.class::isInstance),
                "you should not hear an explosion in territory you have no eyes on");
    }
}
