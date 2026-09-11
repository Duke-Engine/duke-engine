package uz.duke.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;
import uz.duke.rts.message.GameMessage;

/**
 * Something that cannot get past stands still instead of treading the floor.
 *
 * <p>A corridor one cell wide with a body already in it is a shape the dungeon
 * keeps producing, and what used to happen there was not quite standing still.
 * The locomotor gives up on a leg after two seconds of getting no closer — but
 * the brain re-issued the order the very frame it stopped, and every order wipes
 * that count. So the creature was in the walking state for fifty-nine frames out
 * of every sixty, with the walk animation running and its feet going nowhere.
 *
 * <p>Which is why what is measured here is <b>time spent walking</b> rather than
 * distance covered: wedged against stone the position was already still, and a
 * test that watched only the position would have passed throughout.
 */
class WedgedTest {

    /** Deaf, so the skeletons are furniture and the hero is the only one trying. */
    private static final DungeonSettings DEAF = DungeonSettings.parse("""
            DungeonMonster Skeleton
              SenseRadius = 1
              ChaseRadius = 1
              CloseDistance = 4
            End
            """);

    /** One cell of floor between two walls, thirty cells long. */
    private static String corridor() {
        int length = 30;
        var text = new StringBuilder();
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < length; x++) {
                text.append(y == 1 && x > 0 && x < length - 1 ? '.' : '#');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject creature(DukeGame game, String template, int which) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(template))
                .skip(which)
                .findFirst()
                .orElseThrow();
    }

    private record Watch(int framesWalking, float travelled) {
    }

    /**
     * Send the hero at a skeleton with another skeleton in the way, then watch
     * the last {@code watched} of {@code frames}.
     */
    private static Watch wedge(int frames, int watched) {
        var arena = Dungeon.world(corridor(), DEAF);
        var game = arena.game();
        game.spawn("Hero", arena.hero(), 25f, 15f);
        game.spawn("Skeleton", arena.dungeon(), 70f, 15f);  // in the way
        game.spawn("Skeleton", arena.dungeon(), 200f, 15f); // what he was sent at
        game.runHeadless(1);

        var hero = creature(game, "Hero", 0);
        var quarry = creature(game, "Skeleton", 1);
        game.postCommand(new GameMessage.AttackObject(
                game.getLocalPlayerIndex(), List.of(hero.getId()), quarry.getId()));

        game.runHeadless(frames - watched);
        var move = hero.findModule(MoveUpdate.class);
        int walking = 0;
        float travelled = 0f;
        var was = hero.getPosition();
        for (int frame = 0; frame < watched; frame++) {
            game.runHeadless(1);
            if (move.isMoving()) {
                walking++;
            }
            travelled += hero.getPosition().distance(was);
            was = hero.getPosition();
        }
        return new Watch(walking, travelled);
    }

    /**
     * Wedged behind a body he cannot pass, he settles rather than shoving.
     *
     * <p>Two seconds of trying is the locomotor's own patience, and the first
     * six seconds are left to it; what is asserted is that the five after that
     * are quiet — no walking, and no walking on the spot.
     */
    @Test
    void aHeroWhoCannotGetPastStopsTrying() {
        var watch = wedge(330, 150);

        assertEquals(0, watch.framesWalking(),
                "he should have settled, but was still walking on " + watch.framesWalking()
                        + " of the last 150 frames");
        assertTrue(watch.travelled() < 1f,
                "and should not have wandered, but covered " + watch.travelled() + " units");
    }

    /**
     * And he really is wedged — the corridor is the test, not a spelling mistake.
     *
     * <p>If the skeleton in the way were somewhere he could walk round, or if he
     * could reach his target after all, the test above would pass by settling at
     * the far end and would prove nothing at all.
     */
    @Test
    void theWayPastIsGenuinelyShut() {
        var arena = Dungeon.world(corridor(), DEAF);
        var game = arena.game();
        game.spawn("Hero", arena.hero(), 25f, 15f);
        game.spawn("Skeleton", arena.dungeon(), 70f, 15f);
        game.spawn("Skeleton", arena.dungeon(), 200f, 15f);
        game.runHeadless(1);
        var hero = creature(game, "Hero", 0);
        var quarry = creature(game, "Skeleton", 1);
        game.postCommand(new GameMessage.AttackObject(
                game.getLocalPlayerIndex(), List.of(hero.getId()), quarry.getId()));

        game.runHeadless(210);

        assertTrue(hero.getPosition().distance(quarry.getPosition()) > 100f,
                "he got through, so nothing was ever in his way");
    }
}
