package uz.duke.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
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
        return corridor(30);
    }

    private static String corridor(int length) {
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

    // ---- the same fault from the monsters' side, which is where it survived ----

    /**
     * A kind that never notices anything, and a kind that always does.
     *
     * <p>The Skeletons are furniture again; the Brute is the one trying to get
     * past one of them.
     */
    private static final DungeonSettings A_QUEUE = DungeonSettings.parse("""
            DungeonMonster Skeleton
              SenseRadius = 1
              ChaseRadius = 1
              CloseDistance = 4
            End
            DungeonMonster Brute
              SenseRadius = 100000
              ChaseRadius = 100000
              CloseDistance = 4
              RepathFrames = 10
            End
            """);

    private record Queue(DukeGame game, GameObject hero, GameObject blocker, GameObject chaser) {
    }

    /**
     * A hero walking away down a corridor, with a body in it and something behind
     * the body that wants past.
     *
     * <p><b>The hero has to keep walking</b>, and that is the whole reason this
     * exists next to the test above rather than being covered by it. Wedged
     * against something that is not moving, a creature settles: the brain sees
     * nothing worth re-planning for, the locomotor runs out of patience after two
     * seconds and stops. Wedged behind something while its quarry is on the move,
     * every few steps the hero takes are a reason to re-plan, and every re-plan
     * hands the locomotor a fresh leg and a fresh two seconds of patience. So it
     * shuffled for as long as the chase lasted, which in a real game is always.
     */
    private static Queue aQueueInACorridor() {
        var arena = Dungeon.world(corridor(120), A_QUEUE);
        var game = arena.game();
        game.spawn("Hero", arena.hero(), 500f, 15f);
        game.spawn("Skeleton", arena.dungeon(), 400f, 15f); // deaf; simply in the way
        game.spawn("Brute", arena.dungeon(), 350f, 15f);    // and this one wants past
        game.runHeadless(1);

        var hero = creature(game, "Hero", 0);
        game.postCommand(new GameMessage.MoveTo(game.getLocalPlayerIndex(),
                List.of(hero.getId()), new Coord3D(1150f, 15f, 0f)));
        return new Queue(game, hero, creature(game, "Skeleton", 0), creature(game, "Brute", 0));
    }

    /** How long the Brute spends walking, and how far it actually gets, over {@code frames}. */
    private static Watch watch(Queue queue, int frames) {
        var move = queue.chaser().findModule(MoveUpdate.class);
        int walking = 0;
        float travelled = 0f;
        var was = queue.chaser().getPosition();
        for (int frame = 0; frame < frames; frame++) {
            queue.game().runHeadless(1);
            if (move.isMoving()) {
                walking++;
            }
            travelled += queue.chaser().getPosition().distance(was);
            was = queue.chaser().getPosition();
        }
        return new Watch(walking, travelled);
    }

    /**
     * Behind a body it cannot pass, it stands — however much the hero moves.
     *
     * <p>Time in the walking state again rather than ground covered, for the same
     * reason: what the player saw was a creature playing its walk animation with
     * its feet going nowhere.
     */
    @Test
    void aMonsterBehindAnotherStandsStillWhileTheHeroKeepsMoving() {
        var queue = aQueueInACorridor();
        queue.game().runHeadless(150); // it walks up to the one in front and wedges
        var heroWas = queue.hero().getPosition();

        var watch = watch(queue, 150);

        assertTrue(queue.hero().getPosition().distance(heroWas) > 50f,
                "the hero has to be on the move or this proves nothing");
        assertTrue(queue.chaser().getPosition().distance(queue.blocker().getPosition()) < 40f,
                "it never caught up to the one in front, so nothing was in its way");
        assertEquals(0, watch.framesWalking(),
                "it should have settled, but was still walking on " + watch.framesWalking()
                        + " of the last 150 frames");
        assertTrue(watch.travelled() < 1f,
                "and should not have shuffled, but covered " + watch.travelled() + " units");
    }

    /**
     * A living creature is never part of the map.
     *
     * <p>Pinned here because two other things lean on it and neither says so on
     * its own. {@link WayAhead} asks the <em>world</em> who is standing in front of
     * it, precisely because the grid will not say; and {@code Destination} clamps
     * a click against the grid, which is only safe to do because what it finds
     * there is furniture and never a skeleton who will have walked off by the time
     * the hero arrives. If a creature ever were baked in, both would be wrong at
     * once and in ways that look like different bugs.
     */
    @Test
    void aCreatureIsNeverBakedIntoTheMap() {
        var queue = aQueueInACorridor();
        queue.game().runHeadless(30);
        var grid = queue.game().getLogic().getPathGrid();
        var standing = queue.blocker().getPosition();

        assertFalse(grid.isBlocked(grid.toCellX(standing), grid.toCellY(standing)),
                "a skeleton standing in a corridor has turned the corridor into wall");
    }

    /** And it sets off again the moment the way opens, rather than waiting to be asked. */
    @Test
    void andItSetsOffAgainWhenTheWayOpens() {
        var queue = aQueueInACorridor();
        queue.game().runHeadless(150);
        var stoodAt = queue.chaser().getPosition();

        queue.blocker().getBody().damage(100_000f); // the one in front falls
        var watch = watch(queue, 90);

        assertTrue(queue.chaser().getPosition().distance(stoodAt) > 20f,
                "the way opened and it stayed put, covering " + watch.travelled() + " units");
    }
}
