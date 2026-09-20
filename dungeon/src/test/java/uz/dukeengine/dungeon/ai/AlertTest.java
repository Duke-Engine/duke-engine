package uz.dukeengine.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;

/**
 * A room fights as a room: the one that sees him brings the rest.
 *
 * <p>Without it a roomful is a queue. The hero stands in the doorway, the nearest
 * skeleton walks up and dies, the next one notices and walks up and dies, and a
 * fight that ought to be dangerous is a series of duels he wins one at a time.
 * The shout is the only thing that makes a room's population mean anything.
 *
 * <p>Every distance here is the test's own, declared in the settings it builds,
 * so the assertions say something about the mechanism rather than about the
 * numbers the shipped skeleton happens to carry today.
 */
class AlertTest {

    /** A big empty room: 40×30 cells of floor inside a stone border. */
    private static final String ARENA = floor(false);

    /**
     * The same room cut in two by a wall, with the only way round it at the top.
     *
     * <p>The gap matters as much as the wall does: a wall with no way past it
     * would let the test below pass on a skeleton that never moved because it
     * <em>could</em> not, which proves nothing about what it heard.
     */
    private static final String DIVIDED = floor(true);

    private static String floor(boolean divided) {
        int width = 40;
        int height = 30;
        var text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                boolean wall = divided && x == 24 && y > 2;
                text.append(edge || wall ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /**
     * Skeletons of a chosen sight and hearing.
     *
     * <p>A sense radius of 60 is deliberately shorter than the 140 between the
     * hero and the far skeleton, so anything that one does is something it was
     * told rather than something it saw.
     */
    private static DungeonSettings skeletons(float senseRadius, float alertRadius) {
        return DungeonSettings.parse("""
                Monster
                  Name = Skeleton
                  SenseRadius = %s
                  ChaseRadius = 400
                  CloseDistance = 4
                  AlertRadius = %s
                End
                """.formatted(senseRadius, alertRadius));
    }

    private static GameObject creature(DukeGame game, String template, int which) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(template))
                .skip(which)
                .findFirst()
                .orElseThrow();
    }

    /** What the far skeleton did: how far it walked, and how much nearer it got. */
    private record Answer(float moved, float closed) {
    }

    /**
     * Set a fight going and watch the skeleton that cannot see it start.
     *
     * <p>The near one begins inside its own eyes and so opens the fight; the far
     * one begins well outside them, and near enough to the first to hear it.
     */
    private static Answer farSkeleton(String map, DungeonSettings settings) {
        var arena = Dungeon.world(map, settings);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 200f);
        game.spawn("Skeleton", arena.dungeon(), 200f, 200f); // 50 away: it sees him
        game.spawn("Skeleton", arena.dungeon(), 290f, 200f); // 140 away: it cannot
        game.runHeadless(1);

        var hero = creature(game, "Rogue", 0);
        var far = creature(game, "Skeleton", 1);
        var startedAt = far.getPosition();
        float gapBefore = startedAt.distance(hero.getPosition());

        game.runHeadless(200);
        return new Answer(far.getPosition().distance(startedAt),
                gapBefore - far.getPosition().distance(hero.getPosition()));
    }

    @Test
    void oneSkeletonStartingAFightBringsTheRoomWithIt() {
        float closed = farSkeleton(ARENA, skeletons(60f, 120f)).closed();

        assertTrue(closed > 40f,
                "the far skeleton should have come when its neighbour engaged, but closed "
                        + closed + " units");
    }

    /**
     * And it is really the shout that fetched it.
     *
     * <p>The same room with nobody shouting: the far skeleton is outside its own
     * senses and should stand there. Without this, the test above would pass just
     * as well on a skeleton that could see the hero all along.
     */
    @Test
    void withoutTheShoutTheFarSkeletonNeverNotices() {
        float moved = farSkeleton(ARENA, skeletons(60f, 0f)).moved();

        assertTrue(moved < 5f,
                "it noticed him by itself, so the test above proves nothing: it walked "
                        + moved + " units");
    }

    /**
     * A shout does not carry through stone.
     *
     * <p>This is what makes it a room rather than a radius. A fight at the door
     * of one room emptying the next one through the wall would be worse than the
     * queue it replaced, and it is the one way this mechanism could quietly ruin
     * the pacing of a whole floor.
     */
    @Test
    void aShoutDoesNotCarryThroughAWall() {
        float moved = farSkeleton(DIVIDED, skeletons(60f, 120f)).moved();

        assertTrue(moved < 5f,
                "the wall should have swallowed it, but the far skeleton walked "
                        + moved + " units");
    }

    /**
     * And the wall is not simply a cage.
     *
     * <p>The same map and the same places, with eyes long enough to find the hero
     * unaided: it comes round through the gap. So what kept it still in the test
     * above was not being unable to move.
     */
    @Test
    void theWalledOffSkeletonCanStillGetRoundWhenItWantsTo() {
        float moved = farSkeleton(DIVIDED, skeletons(400f, 0f)).moved();

        assertTrue(moved > 40f,
                "it was walled in rather than deaf, so the test above measured nothing: "
                        + "it walked only " + moved + " units");
    }
}
