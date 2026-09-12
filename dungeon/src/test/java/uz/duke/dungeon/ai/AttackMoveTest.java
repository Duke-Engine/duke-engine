package uz.duke.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;
import uz.duke.rts.module.WeaponUpdate;

/**
 * "Go there, and kill what you meet on the way."
 *
 * <p>The order that is neither of the two it is made of. A walk passes what it
 * goes by on purpose — a player who wanted that fight would have pointed at it —
 * and an attack is about one creature and ends with it. This is the one a player
 * gives when he is advancing into a room he cannot see into yet, which is the
 * case neither of the other two can express: he cannot name what is in there, and
 * he does not want to walk past it.
 *
 * <p>So what is pinned here is the shape of the whole errand rather than any one
 * step of it: he sets off, he stops for what he runs into, he finishes it, he
 * carries on, and when he gets there he has no order left.
 */
class AttackMoveTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** An open room, so nothing here turns on where a seed put a wall. */
    private static final String ARENA = arena();

    private static String arena() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                text.append(x == 0 || y == 0 || x == 39 || y == 29 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private record March(DukeGame game, GameObject hero, Orders orders,
            uz.duke.game.GamePlayer dungeon) {

        int player() {
            return hero.getPlayerIndex();
        }

        void sendTo(float x) {
            orders.attackMove(player(), new Coord3D(x, 150f, 0f));
        }

        boolean stillMarching() {
            return orders.attackMovingTo(player()) != null;
        }

        boolean fighting() {
            var weapon = hero.findModule(WeaponUpdate.class);
            return weapon != null && weapon.isAttacking();
        }

        boolean walking() {
            var legs = hero.findModule(MoveUpdate.class);
            return legs != null && legs.isMoving();
        }

        float x() {
            return hero.getPosition().x();
        }
    }

    /** The hero at 150, and a skeleton standing on his way at {@code foeAt}, or none. */
    private static March field(Float foeAt) {
        var arena = Dungeon.world(ARENA, SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        if (foeAt != null) {
            game.spawn("Skeleton", arena.dungeon(), foeAt, 150f);
        }
        game.runHeadless(1);
        return new March(game, creature(game, "Rogue"), arena.orders(), arena.dungeon());
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals(template))
                .findFirst().orElseThrow();
    }

    // ---- an empty room: it is a walk ----

    /** With nothing in the way he simply goes there — and arrives with no order left. */
    @Test
    void anEmptyRoomIsJustAWalk() {
        var march = field(null);
        march.sendTo(300f);

        march.game().runHeadless(200);

        assertTrue(march.x() > 290f, "he got as far as " + march.x() + " of 300");
        assertFalse(march.stillMarching(),
                "he is there and still under orders to get there, so nothing else can reach him");
    }

    // ---- and what makes it not a walk ----

    /**
     * ★ He stops for what he runs into, rather than walking past it.
     *
     * <p>The whole of the order. The skeleton is eighty off — further than its own
     * {@code SenseRadius}, so it is not coming to him and this is him walking into
     * it — and it stands between him and where he was sent.
     */
    @Test
    void heStopsForWhatHeMeets() {
        var march = field(230f);
        march.sendTo(330f);

        march.game().runHeadless(40);

        assertTrue(march.fighting(), "he walked straight past it at " + march.x());
        assertFalse(march.walking(), "he is shooting it on the move rather than standing to it");
    }

    /**
     * ★ And carries on when it is down.
     *
     * <p>The half that is easy to leave out, and leaving it out is worse than not
     * having the order: the player advances, something dies, and his hero is
     * standing in an empty corridor waiting to be told again.
     */
    @Test
    void andCarriesOnWhenItIsDown() {
        var march = field(230f);
        march.sendTo(330f);

        march.game().runHeadless(600);

        assertTrue(march.x() > 320f, "he stopped where the fight was, at " + march.x());
        assertFalse(march.stillMarching(), "he is there, so the errand should be spent");
    }

    /** And having arrived he is guarding, which is where every order in this game ends. */
    @Test
    void arrivingHeIsGuardingAgain() {
        var march = field(null);
        march.sendTo(300f);
        march.game().runHeadless(200);
        assertFalse(march.stillMarching(), "the premise: he arrived");

        march.game().spawn("Skeleton", march.dungeon(), 330f, 150f);
        march.game().runHeadless(30);

        assertTrue(march.fighting(),
                "something came within reach of a hero standing at rest and he ignored it");
    }

    // ---- the order arrives, and the player's next word calls it off ----

    /**
     * A player with no creatures at all, so that these two are about the command
     * handler and nothing else.
     *
     * <p>Not fussiness. Given to the real player these would both have passed
     * whatever the handler did, because his hero clears the errand himself the
     * moment he arrives or finds he cannot get there — and on a generated floor a
     * spot picked out of the air is very often somewhere he cannot get to. A test
     * that passes because the thing it is watching happened for another reason is
     * worse than no test.
     */
    private static final int NOBODY_IN_PARTICULAR = 7;

    /** It travels as a command, on a frame boundary, like every other order. */
    @Test
    void theOrderTravelsThroughTheCommandQueue() {
        var session = Dungeon.newSession(7L, SETTINGS);
        var game = session.game();
        game.runHeadless(1);

        game.postCommand(new AttackMove(NOBODY_IN_PARTICULAR, new Coord3D(120f, 120f, 0f)));
        game.runHeadless(2);

        assertNotNull(session.orders().attackMovingTo(NOBODY_IN_PARTICULAR),
                "the command should have been applied on the next frame");
    }

    // ---- and the player's next word calls it off ----

    /**
     * ★ Sent somewhere else, he goes there instead.
     *
     * <p>Otherwise it is an order the player cannot get rid of: he says "go over
     * there" and his hero finishes an errand from a minute ago, which reads as the
     * game having stopped listening rather than as an order still in force.
     *
     * <p>None of these three can be heard where the order itself is heard. They
     * are the engine's own and it applies them itself — the game's command handler
     * is the door for commands it does not recognise — so the errand finds out by
     * noticing that the walk it put on his legs is not there any more. Which is
     * worth a test precisely because it is indirect.
     */
    @Test
    void beingSentSomewhereElseEndsIt() {
        var march = field(null);
        march.sendTo(330f);
        march.game().runHeadless(10);
        assertTrue(march.stillMarching(), "the premise: he is on his way");

        march.game().postCommand(new uz.duke.rts.message.GameMessage.MoveTo(
                march.game().getLocalPlayerIndex(),
                java.util.List.of(march.hero().getId()),
                new Coord3D(150f, 250f, 0f)));
        march.game().runHeadless(4);

        assertFalse(march.stillMarching(),
                "he was sent somewhere else and is still fighting his way to the old spot");
    }

    /** Pointed at something, that is what he is doing now. */
    @Test
    void beingPointedAtSomethingEndsIt() {
        var march = field(230f);
        march.sendTo(330f);
        march.game().runHeadless(5);
        assertTrue(march.stillMarching(), "the premise: he is on his way");

        march.game().postCommand(new uz.duke.rts.message.GameMessage.AttackObject(
                march.game().getLocalPlayerIndex(),
                java.util.List.of(march.hero().getId()),
                creature(march.game(), "Skeleton").getId()));
        march.game().runHeadless(4);

        assertFalse(march.stillMarching(), "the errand outlived the order that replaced it");
    }

    /** And Stop means stop, not "stop and pick it up again when I let you go". */
    @Test
    void beingToldToStopEndsIt() {
        var march = field(null);
        march.sendTo(330f);
        march.game().runHeadless(10);
        assertTrue(march.stillMarching(), "the premise: he is on his way");

        march.orders().hold(march.player(), true);
        march.game().runHeadless(4);
        march.orders().hold(march.player(), false);
        march.game().runHeadless(10);

        assertFalse(march.stillMarching(),
                "told to stop and then let loose, he set off again on an order he was given"
                        + " before the one that cancelled it");
    }
}
