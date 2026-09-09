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
        return fight(heroX, heroY, skeletonX, skeletonY, "Skeleton");
    }

    /** The same, against a named kind — for a fight the hero must not end at once. */
    private static Fight fight(float heroX, float heroY, float foeX, float foeY, String kind) {
        var world = Dungeon.world(ARENA, SETTINGS);
        var game = world.game();
        game.spawn("Hero", world.hero(), heroX, heroY);
        game.spawn(kind, world.dungeon(), foeX, foeY);
        game.runHeadless(1);
        return new Fight(game, creature(game, "Hero"), creature(game, kind));
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
     * Sent somewhere, he goes there — even straight past something that takes a
     * swing at him on the way.
     *
     * <p>His weapon picks up whatever comes within reach, and the game's own
     * closing behaviour used to read that as "arrived" and halt him. So walking a
     * hero past a skeleton stopped him dead in front of it: the game cancelled the
     * order the player had just given. Where he was sent outranks what his weapon
     * noticed, and the weapon fires in passing anyway.
     */
    @Test
    void aMoveOrderOutranksSomethingHePassesOnTheWay() {
        // The skeleton sits beside the line he is walking, close enough for his
        // weapon to reach it and for it to reach him.
        var fight = fight(60f, 150f, 180f, 150f);
        var destination = new Coord3D(320f, 150f, 0f);

        fight.game().postCommand(new GameMessage.MoveTo(fight.game().getLocalPlayerIndex(),
                List.of(fight.hero().getId()), destination));
        fight.game().runHeadless(500);

        assertTrue(fight.hero().getPosition().distance(destination) < 20f,
                "he should have walked past it and arrived, but stopped at "
                        + fight.hero().getPosition());
    }

    /** And once he has arrived, he defends himself again. */
    @Test
    void havingArrivedHeFightsWhateverFollowedHim() {
        var fight = fight(60f, 150f, 180f, 150f);
        var skeletonId = fight.skeleton().getId();

        fight.game().postCommand(new GameMessage.MoveTo(fight.game().getLocalPlayerIndex(),
                List.of(fight.hero().getId()), new Coord3D(320f, 150f, 0f)));
        fight.game().runHeadless(900);

        assertNull(fight.game().getLogic().findObject(skeletonId),
                "the skeleton that chased him down should have been dealt with");
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

    /**
     * Sent back the way he came, the hero turns round rather than driving a
     * circle. {@code TurnRate} is SAGE's vehicle parameter, and with one set the
     * hero banked like a tank; a man pivots.
     *
     * <p>Checked by the shape of the path, not by any number in the data: an arc
     * swings wide of the line it is travelling along, so if he never leaves that
     * line he never arced.
     */
    @Test
    void reversingDirectionTurnsOnTheSpotInsteadOfDrivingACircle() {
        var world = Dungeon.world(ARENA, SETTINGS);
        var game = world.game();
        game.spawn("Hero", world.hero(), 120f, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Hero");
        int player = game.getLocalPlayerIndex();

        // Get him walking east, so he has a heading to reverse.
        game.postCommand(new GameMessage.MoveTo(player, List.of(hero.getId()),
                new Coord3D(260f, 150f, 0f)));
        game.runHeadless(40);
        float turnedAtX = hero.getPosition().x();

        // Now straight back the way he came: a 180° reversal.
        game.postCommand(new GameMessage.MoveTo(player, List.of(hero.getId()),
                new Coord3D(120f, 150f, 0f)));

        float widestSwing = 0f;
        float furthestEast = turnedAtX;
        for (int frame = 0; frame < 120; frame++) {
            game.runHeadless(1);
            widestSwing = Math.max(widestSwing, Math.abs(hero.getPosition().y() - 150f));
            furthestEast = Math.max(furthestEast, hero.getPosition().x());
        }

        assertTrue(widestSwing < 5f,
                "turning round should not swing him off the line, but he strayed "
                        + widestSwing + " units sideways");
        assertTrue(furthestEast - turnedAtX < 10f,
                "nor should he coast onward while turning, but he ran "
                        + (furthestEast - turnedAtX) + " units past the turn");
        assertTrue(hero.getPosition().x() < turnedAtX,
                "and he should be heading back west");
    }

    /**
     * A hero attacking something behind him turns to face it. The engine sets a
     * heading only while walking, so a fighter who has arrived — or who never
     * moved, because the fight came to him — would otherwise strike over his
     * shoulder.
     */
    @Test
    void theHeroTurnsToFaceWhatHeIsAttacking() {
        // Close enough to swing without walking, and directly behind him: any
        // turning here is the game's doing, since he never takes a step.
        // The boss, not a skeleton: a skeleton spawned inside the hero's reach is
        // killed in the boot frame and never survives to be faced. What is being
        // asked here is which way he turns, and that must not depend on how hard
        // the game currently has him hitting.
        var fight = fight(200f, 150f, 182f, 150f, "Boss");
        var hero = fight.hero();
        var skeleton = fight.skeleton();
        hero.setOrientation(0f); // looking east; the skeleton is west

        assertTrue(offBy(hero, skeleton) > 1f, "he should start out looking the wrong way");

        fight.game().runHeadless(30);

        assertNotNull(fight.game().getLogic().findObject(skeleton.getId()),
                "the skeleton should still be alive for him to be facing");
        assertTrue(offBy(hero, skeleton) < 0.2f,
                "the hero should have turned to face it, but was off by "
                        + offBy(hero, skeleton) + " radians");
    }

    /** And so does a skeleton: it looks at the hero it is hitting. */
    @Test
    void aSkeletonTurnsToFaceTheHero() {
        var fight = fight(200f, 150f, 182f, 150f, "Boss");
        var skeleton = fight.skeleton();
        skeleton.setOrientation((float) StrictMath.PI); // looking away from the hero

        fight.game().runHeadless(30);

        assertTrue(offBy(skeleton, fight.hero()) < 0.2f,
                "the skeleton should be looking at what it is hitting");
    }

    /** How far {@code fighter}'s heading is from pointing at {@code target}, in radians. */
    private static float offBy(GameObject fighter, GameObject target) {
        float wanted = (float) StrictMath.atan2(
                target.getPosition().y() - fighter.getPosition().y(),
                target.getPosition().x() - fighter.getPosition().x());
        float difference = fighter.getOrientation() - wanted;
        // Shortest way round, so 359° counts as 1° rather than as nearly a full turn.
        return (float) Math.abs(StrictMath.atan2(
                StrictMath.sin(difference), StrictMath.cos(difference)));
    }

    /**
     * A skeleton that walks up and starts hitting the hero gets hit back, with no
     * order given. This is the case the player is actually in most of the time —
     * standing still while the dungeon comes to him — and it is the one that broke
     * when the skeletons learned to advance: they stopped at their own maximum
     * reach, which was further than the hero's, so he stood there being chipped at
     * and never once swung back.
     */
    @Test
    void theHeroFightsBackAgainstWhateverAttacksHim() {
        float gap = SETTINGS.skeletonSenseRadius() * 0.7f;
        var fight = fight(150f, 150f, 150f + gap, 150f);
        var skeleton = fight.skeleton();
        float skeletonHealthBefore = skeleton.getBody().getHealth();

        // The hero is given no orders whatsoever. Everything that happens is the
        // skeleton advancing and the hero answering on his own.
        fight.game().runHeadless(400);

        assertTrue(skeleton.getBody().getHealth() < skeletonHealthBefore
                        || fight.game().getLogic().findObject(skeleton.getId()) == null,
                "a skeleton attacking the hero should have been fought back, but it is "
                        + "untouched at " + skeleton.getBody().getHealth() + " health");
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
