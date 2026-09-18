package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.game.DukeGame;
import uz.duke.rts.message.GameMessage;

/**
 * The dungeon is playable: the hero goes where he is sent, fights what he is
 * pointed at, and the room is solid.
 *
 * <p>Played headlessly, with no window and no mouse — the same simulation the
 * player would be driving, just given its orders directly.
 */
class DungeonTest {

    private static GameObject heroOf(DukeGame game) {
        return creature(game, "Rogue");
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst()
                .orElse(null);
    }

    private static DukeGame started() {
        var game = Dungeon.create();
        game.runHeadless(1); // boot the world
        return game;
    }

    @Test
    void theRoomIsPopulated() {
        var game = started();

        assertNotNull(heroOf(game), "there should be a hero");
        assertEquals(3, game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals("Skeleton")).count());
    }

    @Test
    void theHeroWalksWhereHeIsSent() {
        var game = started();
        var hero = heroOf(game);
        var destination = new Coord3D(330f, 190f, 0f);

        game.postCommand(new GameMessage.MoveTo(game.getLocalPlayerIndex(),
                List.of(hero.getId()), destination));
        game.runHeadless(400);

        assertTrue(hero.getPosition().distance(destination) < 12f,
                "the hero should have arrived, but stopped at " + hero.getPosition());
    }

    @Test
    void theHeroGoesToASkeletonAndKillsIt() {
        var game = started();
        var hero = heroOf(game);
        var skeleton = creature(game, "Skeleton");
        var skeletonId = skeleton.getId();

        // What a click on a skeleton sends: walk to it, then engage. The order
        // matters — a move order clears the current target.
        game.postCommand(new GameMessage.MoveTo(game.getLocalPlayerIndex(),
                List.of(hero.getId()), skeleton.getPosition()));
        game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                List.of(hero.getId()), skeletonId));
        game.runHeadless(500);

        // Behaviour, not balance: he reached it, it died, he lived. Asserting a
        // health figure would make this test fail every time the game is tuned,
        // which teaches you to edit the test rather than believe it.
        assertNull(game.getLogic().findObject(skeletonId), "the skeleton should be dead and gone");
        assertNotNull(game.getLogic().findObject(hero.getId()), "the hero should still be alive");
        assertTrue(hero.getBody().getHealth() > 0f, "the hero should have survived the fight");
    }

    @Test
    void skeletonsFightBack() {
        var game = started();
        var hero = heroOf(game);
        float before = hero.getBody().getHealth();

        game.postCommand(new GameMessage.MoveTo(game.getLocalPlayerIndex(),
                List.of(hero.getId()), creature(game, "Skeleton").getPosition()));
        game.runHeadless(400);

        assertTrue(hero.getBody().getHealth() < before,
                "standing next to a skeleton should cost something");
    }

    @Test
    void theWallsAreSolid() {
        var game = started();
        var hero = heroOf(game);
        var terrain = game.getTerrain();

        // Ordered straight into the far wall, through the pillars.
        game.postCommand(new GameMessage.MoveTo(game.getLocalPlayerIndex(),
                List.of(hero.getId()), new Coord3D(20f, 20f, 0f)));

        for (int frame = 0; frame < 400; frame++) {
            game.runHeadless(1);
            var at = hero.getPosition();
            assertTrue(!terrain.isTerrainBlocked(terrain.toCellX(at), terrain.toCellY(at)),
                    "the hero walked into stone at " + at);
        }
    }
}
