package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.message.GameMessage;

/**
 * A melee hero given an attack order walks to it.
 *
 * <p>The archer proved this years ago, and it turns out he proved it only for
 * himself: the reported fault is that the knight, pointed at a skeleton across
 * the room, stands where he is. He fights back once the skeleton reaches him,
 * which is the shape of a bug about <em>orders</em> rather than about combat.
 *
 * <p>Written against both of them on purpose. What differs between the two is
 * reach — sixty against eleven — so a rule that holds for one and not the other
 * is a rule that was written in terms of the archer's reach without anybody
 * meaning to.
 */
class KnightOrderTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final String ARENA = room(60, 40);

    private static String room(int width, int height) {
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

    /** One hero of the named kind, and one skeleton well out of anybody's reach. */
    private static Fight fight(String heroTemplate) {
        var world = Dungeon.world(ARENA, SETTINGS);
        var game = world.game();
        game.spawn(heroTemplate, world.hero(), 150f, 150f);
        // Far enough to be outside the archer's sixty as well as the knight's
        // eleven, so neither of them is already in range when the order lands.
        game.spawn("Skeleton", world.dungeon(), 330f, 150f);
        game.runHeadless(1);
        return new Fight(game, creature(game, heroTemplate), creature(game, "Skeleton"));
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static void order(Fight fight) {
        fight.game().postCommand(new GameMessage.AttackObject(
                fight.game().getLocalPlayerIndex(), java.util.List.of(fight.hero().getId()),
                fight.skeleton().getId()));
    }

    /**
     * Every hero stops somewhere he can actually reach from.
     *
     * <p>The invariant the fault broke, written down so the next hero cannot break
     * it again quietly. Being wrong here looks like nothing at all: he walks, he
     * stops, and then he stands swinging at air until whatever he was sent at
     * closes the rest of the distance itself.
     *
     * <p>Asked of the file rather than of a fight, because it is a fact about the
     * two numbers and should fail the moment they disagree — not only when
     * somebody plays the hero they disagree about.
     */
    @Test
    void everyHeroStopsInsideHisOwnReach() {
        var templates = templates();
        for (var him : SETTINGS.heroes()) {
            float stopsAt = him.closeDistance() > 0f
                    ? him.closeDistance() : SETTINGS.combat().closeDistance();
            float reaches = reachOf(templates, him.name());

            assertTrue(stopsAt < reaches, him.name() + " stops " + stopsAt + " away and reaches "
                    + reaches + " — he will stand there and never swing");
        }
    }

    private static float reachOf(uz.dukeengine.core.thing.ThingFactory templates, String template) {
        for (var entry : templates.findTemplate(template).modules()) {
            if (entry instanceof uz.dukeengine.rts.module.WeaponUpdate.Data weapon) {
                return weapon.attackRange();
            }
        }
        return 0f;
    }

    private static uz.dukeengine.core.thing.ThingFactory templates() {
        var game = Dungeon.world(ARENA, SETTINGS).game();
        game.runHeadless(1);
        return game.getLogic().getThingFactory();
    }

    /**
     * Pointed at something across the room, he goes to it.
     *
     * <p>The fault as reported, and asked of both heroes at once. Twenty frames is
     * two thirds of a second — long enough that a hero who has decided to walk has
     * plainly moved, and short enough that one who has not cannot have drifted
     * there by accident.
     */
    @Test
    void aHeroPointedAtSomethingWalksToIt() {
        for (var him : SETTINGS.heroes()) {
            var fight = fight(him.name());
            float startedAt = fight.hero().getPosition().x();

            order(fight);
            fight.game().runHeadless(20);

            float moved = fight.hero().getPosition().x() - startedAt;
            assertTrue(moved > 5f, him.name() + " was ordered to attack something 180 away"
                    + " and moved " + moved + " — he is standing where he was put");
        }
    }

    /**
     * Pointed at something just outside his reach, he still walks the last stretch.
     *
     * <p>The fault as the player met it, which the test above does not reach.
     * Pointed across a room, the knight walked — the old stopping distance was 48
     * and he was 180 away. Pointed at something twenty away he did not, because
     * twenty is inside 48 and the order counted itself already satisfied. He then
     * stood there until the skeleton walked over and hit him, and trading blows
     * after that looks enough like fighting to fool a test that only asks whether
     * the skeleton got hurt: <em>it did</em>, under the bug, on the skeleton's
     * initiative. So the thing asked here is whether <b>he</b> moved.
     *
     * <p>The distance is his own reach and most of it again, so every hero has to
     * cross ground and none is asked to walk at something he could already hit.
     */
    @Test
    void pointedAtSomethingJustOutOfReachHeStillWalksToIt() {
        var templates = templates();
        for (var him : SETTINGS.heroes()) {
            float gap = reachOf(templates, him.name()) * 1.8f;
            var world = Dungeon.world(ARENA, SETTINGS);
            var game = world.game();
            game.spawn(him.name(), world.hero(), 150f, 150f);
            game.spawn("Skeleton", world.dungeon(), 150f + gap, 150f);
            game.runHeadless(1);
            var fight = new Fight(game, creature(game, him.name()), creature(game, "Skeleton"));
            float startedAt = fight.hero().getPosition().x();

            order(fight);
            game.runHeadless(20);

            float moved = fight.hero().getPosition().x() - startedAt;
            assertTrue(moved > 1f, him.name() + " was pointed at something " + gap
                    + " away, needs to be within " + reachOf(templates, him.name())
                    + " to swing, and moved " + moved + " — he is waiting to be come to");
        }
    }

    /** And he keeps going until he is close enough to swing. */
    @Test
    void andHeKeepsGoingUntilHeCanReachIt() {
        for (var him : SETTINGS.heroes()) {
            var fight = fight(him.name());

            order(fight);
            fight.game().runHeadless(400);

            float apart = Math.abs(fight.skeleton().getPosition().x()
                    - fight.hero().getPosition().x());
            assertTrue(apart < 60f, him.name() + " stopped " + apart + " away from what he"
                    + " was sent at");
        }
    }

    /**
     * And the thing he was sent at ends up hurt.
     *
     * <p>The whole order, end to end: walk to it and hit it. Asked separately from
     * the walking because the two fail for different reasons — one is about orders
     * and the other about weapons — and a single test would not say which.
     */
    @Test
    void andWhatHeWasSentAtIsHurt() {
        for (var him : SETTINGS.heroes()) {
            var fight = fight(him.name());
            float whole = fight.skeleton().getBody().getHealth();

            order(fight);
            fight.game().runHeadless(600);

            assertTrue(fight.skeleton().isEffectivelyDead()
                            || fight.skeleton().getBody().getHealth() < whole,
                    him.name() + " reached it and never hit it");
        }
    }
}
