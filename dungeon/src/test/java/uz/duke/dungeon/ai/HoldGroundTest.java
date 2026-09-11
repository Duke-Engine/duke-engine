package uz.duke.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;
import uz.duke.rts.module.WeaponUpdate;

/**
 * Told to hold his ground, the hero stops picking fights.
 *
 * <p>The fourth order on the panel, and the only one of the four the engine has
 * no word for: stop cancels a walk and says nothing about his bow. Without it
 * there was no way to walk past something, wait at a door, or creep up on a boss
 * — he shot at whatever he could see, always, and that is right nearly all of the
 * time and wrong at exactly the moments a player would want to choose.
 *
 * <p>What it must <b>not</b> do is stop him obeying. An order to attack is still
 * an order, and a test that only checked he had gone quiet would pass just as
 * well on a hero who had stopped listening.
 */
class HoldGroundTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** An open room, so nothing here turns on where a seed put a wall. */
    private static final String ARENA = arena();

    private static String arena() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                boolean edge = x == 0 || y == 0 || x == 39 || y == 29;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private record Standoff(DukeGame game, GameObject hero, GameObject skeleton, Orders orders) {

        boolean shooting() {
            var weapon = hero.findModule(WeaponUpdate.class);
            return weapon != null && weapon.isAttacking();
        }
    }

    /** The hero and one skeleton, close enough for him to see and shoot it. */
    private static Standoff standoff() {
        var arena = Dungeon.world(ARENA, SETTINGS);
        var game = arena.game();
        game.spawn("Hero", arena.hero(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 190f, 150f);
        game.runHeadless(1);
        return new Standoff(game, creature(game, "Hero"), creature(game, "Skeleton"),
                arena.orders());
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(template))
                .findFirst().orElseThrow();
    }

    /** Left alone, he shoots what he can see — which is what makes the order worth having. */
    @Test
    void byDefaultHeShootsWhatHeCanSee() {
        var fight = standoff();

        fight.game().runHeadless(20);

        assertTrue(fight.shooting(), "he should have picked the skeleton up by himself");
    }

    @Test
    void holdingHisGroundHeLeavesItAlone() {
        var fight = standoff();
        fight.orders().hold(fight.hero().getPlayerIndex(), true);

        fight.game().runHeadless(60);

        assertFalse(fight.shooting(), "he was told to hold and started a fight anyway");
    }

    /**
     * And he still does as he is told.
     *
     * <p>Holding ground means "start nothing", not "hear nothing". A hero who
     * ignored an attack order while holding would be a hero the player has to
     * remember to switch off, which is a worse control than none.
     */
    @Test
    void anOrderStillReachesHimWhileHeHolds() {
        var fight = standoff();
        fight.orders().hold(fight.hero().getPlayerIndex(), true);
        fight.game().runHeadless(30);
        assertFalse(fight.shooting(), "nothing should have started on its own");

        fight.game().postCommand(new uz.duke.rts.message.GameMessage.AttackObject(
                fight.game().getLocalPlayerIndex(), java.util.List.of(fight.hero().getId()),
                fight.skeleton().getId()));
        fight.game().runHeadless(10);

        assertTrue(fight.shooting(), "he was pointed at it and refused");
    }

    /**
     * And Guard lets him loose again.
     *
     * <p>Two buttons rather than one that toggles, because each of them is also
     * the lamp for the state it sets -- see {@link Doing} -- and "the other one"
     * is not a state a lamp can show.
     */
    @Test
    void guardingAgainLetsHimLoose() {
        var fight = standoff();
        fight.orders().hold(fight.hero().getPlayerIndex(), true);
        fight.game().runHeadless(30);
        assertFalse(fight.shooting(), "he should be standing");

        fight.orders().hold(fight.hero().getPlayerIndex(), false);
        fight.game().runHeadless(20);

        assertTrue(fight.shooting(), "told to guard, he should pick fights again");
    }

    /**
     * Stop drops what he was already doing, rather than only refusing to start
     * anything new.
     *
     * <p>The difference the player feels. A hero who finished the walk he was on
     * and only then stood still would make Stop a suggestion -- and it is the one
     * order that has to be instant, because it is what he presses when something
     * has gone wrong.
     */
    @Test
    void stopDropsWhatHeWasAlreadyDoing() {
        var fight = standoff();
        fight.game().runHeadless(20);
        assertTrue(fight.shooting(), "he should have started on his own first");

        fight.orders().hold(fight.hero().getPlayerIndex(), true);
        fight.game().runHeadless(2);

        assertFalse(fight.shooting(),
                "Stop should have taken the fight off him, not waited for it to end");
    }

    /**
     * And it comes off the moment the player wants something else.
     *
     * <p>Otherwise Stop is a state he can enter and not leave: he presses it, then
     * clicks the floor, and the hero stands there refusing -- which reads as the
     * game having stopped listening rather than as an order still in force.
     */
    @Test
    void anOrderTakesTheStandingOrderOffAgain() {
        var fight = standoff();
        fight.orders().hold(fight.hero().getPlayerIndex(), true);
        fight.game().runHeadless(10);
        assertTrue(fight.orders().isHolding(fight.hero().getPlayerIndex()));

        fight.game().postCommand(new uz.duke.rts.message.GameMessage.MoveTo(
                fight.game().getLocalPlayerIndex(),
                java.util.List.of(fight.hero().getId()),
                new uz.duke.core.math.Coord3D(300f, 150f, 0f)));
        fight.game().runHeadless(4);

        assertFalse(fight.orders().isHolding(fight.hero().getPlayerIndex()),
                "he was told to walk, so he is not standing still any more");
    }

    /** It arrives as a command, on a frame boundary, like every other order. */
    @Test
    void theOrderTravelsThroughTheCommandQueue() {
        var session = Dungeon.newSession(7L, SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        var orders = session.orders();
        assertFalse(orders.isHolding(game.getLocalPlayerIndex()));

        game.postCommand(new HoldGround(game.getLocalPlayerIndex(), true));
        game.runHeadless(2);
        assertTrue(orders.isHolding(game.getLocalPlayerIndex()),
                "the command should have been applied on the next frame");

        game.postCommand(new HoldGround(game.getLocalPlayerIndex(), false));
        game.runHeadless(2);
        assertFalse(orders.isHolding(game.getLocalPlayerIndex()),
                "and the other button should have taken it off again");
    }
}
