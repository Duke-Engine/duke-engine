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
        fight.orders().toggleHold(fight.hero().getPlayerIndex());

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
        fight.orders().toggleHold(fight.hero().getPlayerIndex());
        fight.game().runHeadless(30);
        assertFalse(fight.shooting(), "nothing should have started on its own");

        fight.game().postCommand(new uz.duke.rts.message.GameMessage.AttackObject(
                fight.game().getLocalPlayerIndex(), java.util.List.of(fight.hero().getId()),
                fight.skeleton().getId()));
        fight.game().runHeadless(10);

        assertTrue(fight.shooting(), "he was pointed at it and refused");
    }

    /** And the order comes off again. */
    @Test
    void tellingHimTwiceLetsHimLooseAgain() {
        var fight = standoff();
        fight.orders().toggleHold(fight.hero().getPlayerIndex());
        fight.game().runHeadless(30);
        fight.orders().toggleHold(fight.hero().getPlayerIndex());

        fight.game().runHeadless(20);

        assertTrue(fight.shooting(), "the order should be a toggle, not a one-way switch");
    }

    /** It arrives as a command, on a frame boundary, like every other order. */
    @Test
    void theOrderTravelsThroughTheCommandQueue() {
        var session = Dungeon.newSession(7L, SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        var orders = session.orders();
        assertFalse(orders.isHolding(game.getLocalPlayerIndex()));

        game.postCommand(new HoldGround(game.getLocalPlayerIndex()));
        game.runHeadless(2);

        assertTrue(orders.isHolding(game.getLocalPlayerIndex()),
                "the command should have been applied on the next frame");
    }
}
