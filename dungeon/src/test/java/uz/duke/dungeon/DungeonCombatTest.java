package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;
import uz.duke.rts.message.GameMessage;

/**
 * Combat as the player meets it: what he clicks on, what comes for him unbidden,
 * and what the skeletons do about it.
 *
 * <p>Fought in a purpose-built open room rather than a generated dungeon, so the
 * distances are the test's own and nothing depends on where a seed happened to put
 * a wall. The creatures and behaviour are the game's, through the same seam the
 * real dungeon is built on — a test that wired up its own would be proving
 * something about a world nobody plays.
 *
 * <p>Nothing here asserts a balance figure. Where a distance has to be named it is
 * taken from the game's own settings, so re-tuning the game moves the test with it
 * instead of breaking it.
 */
class DungeonCombatTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** A big empty room: 40×30 cells of floor inside a stone border. */
    private static final String ARENA = arena();

    private static String arena() {
        int width = 40;
        int height = 30;
        var text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private record Fight(DukeGame game, GameObject hero, GameObject skeleton) {
    }

    /** A hero and one skeleton, placed exactly where the test wants them. */
    private static Fight fight(float heroX, float heroY, float skeletonX, float skeletonY) {
        var world = Dungeon.world(ARENA, SETTINGS);
        var game = world.game();
        game.spawn("Hero", world.hero(), heroX, heroY);
        game.spawn("Skeleton", world.dungeon(), skeletonX, skeletonY);
        game.runHeadless(1);
        return new Fight(game, creature(game, "Hero"), creature(game, "Skeleton"));
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(template))
                .findFirst()
                .orElse(null);
    }

    /**
     * Clicking a distant skeleton kills it. This is exactly what the 3D client
     * sends on a right-click — an attack order and nothing else — and the engine's
     * weapon deliberately will not walk the hero into range, so without the game
     * supplying that half the hero would stand still and the skeleton would live.
     */
    @Test
    void clickingADistantSkeletonSendsTheHeroToKillIt() {
        var fight = fight(60f, 150f, 300f, 150f);
        var skeletonId = fight.skeleton().getId();
        float startDistance = fight.hero().getPosition().distance(fight.skeleton().getPosition());

        fight.game().postCommand(new GameMessage.AttackObject(
                fight.game().getLocalPlayerIndex(), List.of(fight.hero().getId()), skeletonId));
        fight.game().runHeadless(600);

        assertTrue(fight.hero().getPosition().distance(new Coord3D(60f, 150f, 0f)) > startDistance / 4f,
                "the hero should have left where he was standing and closed the distance");
        assertNull(fight.game().getLogic().findObject(skeletonId),
                "the skeleton he was pointed at should be dead");
    }

    /**
     * And the engine's own auto-acquire still works: walked next to a skeleton with
     * no attack order at all, the hero fights it. The click behaviour is an
     * addition, not a replacement.
     */
    @Test
    void theHeroAutoAttacksWhatHeWalksInto() {
        var fight = fight(60f, 150f, 300f, 150f);
        var skeletonId = fight.skeleton().getId();

        // Only a move order — never an AttackObject.
        fight.game().postCommand(new GameMessage.MoveTo(fight.game().getLocalPlayerIndex(),
                List.of(fight.hero().getId()), fight.skeleton().getPosition()));
        fight.game().runHeadless(600);

        assertNull(fight.game().getLogic().findObject(skeletonId),
                "a skeleton the hero walked into should have been engaged unprompted");
    }

    /**
     * The skeletons are no longer furniture: one that notices the hero comes to
     * him. The hero is given no orders at all, so any closing is the skeleton's
     * own doing.
     */
    @Test
    void aSkeletonThatSensesTheHeroAdvancesOnHim() {
        float gap = SETTINGS.skeletonSenseRadius() * 0.7f; // comfortably within notice
        var fight = fight(150f, 150f, 150f + gap, 150f);
        float before = fight.hero().getPosition().distance(fight.skeleton().getPosition());

        fight.game().runHeadless(90);

        float after = fight.hero().getPosition().distance(fight.skeleton().getPosition());
        assertTrue(after < before,
                "the skeleton should have closed on the hero, but went from " + before + " to " + after);
    }

    /** A skeleton far beyond its notice keeps to itself — aggro spreads room by room. */
    @Test
    void aSkeletonOutOfRangeStaysPut() {
        float gap = SETTINGS.skeletonChaseRadius() * 2f; // well outside anything it reacts to
        var fight = fight(60f, 150f, 60f + gap, 150f);
        var startedAt = fight.skeleton().getPosition();

        fight.game().runHeadless(90);

        assertTrue(fight.skeleton().getPosition().distance(startedAt) < 1f,
                "a skeleton that cannot sense the hero should not have moved");
    }

    /** Skeletons fight back once they arrive — the advance is not a harmless parade. */
    @Test
    void anAdvancingSkeletonDrawsBlood() {
        float gap = SETTINGS.skeletonSenseRadius() * 0.7f;
        var fight = fight(150f, 150f, 150f + gap, 150f);
        var hero = fight.hero();
        float before = hero.getBody().getHealth();

        fight.game().runHeadless(400);

        assertNotNull(fight.game().getLogic().findObject(hero.getId()));
        assertTrue(hero.getBody().getHealth() < before,
                "a skeleton that walked over should have hit the hero");
    }
}
