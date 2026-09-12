package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.client3d.Hotkeys;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.ai.Orders;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;
import uz.duke.rts.module.WeaponUpdate;

/**
 * The fourth button: stand where you are, and fight whatever comes to you.
 *
 * <p>What the standing order does once it lands is {@code HoldGroundTest}'s. What
 * is here is the half that was missing — that pressing this is an <b>order</b> at
 * all.
 *
 * <p>It used only to take a hold OFF. So it worked, exactly once, as the undo for
 * Stop: press it any other time and nothing whatever happened. A hero walking
 * walked on, a hero chasing chased on, and the button lit and dimmed the whole
 * while as though it were doing something. It was the one control on the bar a
 * player could press all game without ever once seeing it act, while its own word
 * said Himoya.
 *
 * <p><b>Pressed through the real binding</b>, not by posting the command this file
 * thinks it sends. That distinction is not pedantry here: three separate faults in
 * this game have been a key that was bound, drawn, listed and lit and sent
 * nothing, and not one of them was visible to a test that could see the binding
 * but not press it.
 */
class GuardOrderTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** The real controls, out of the real file — the same object the client gets. */
    private static final Hotkeys KEYS = Main.controls(SETTINGS);

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

    private record Field(DukeGame game, GameObject hero, Orders orders) {

        boolean walking() {
            var legs = hero.findModule(MoveUpdate.class);
            return legs != null && legs.isMoving();
        }

        boolean shooting() {
            var weapon = hero.findModule(WeaponUpdate.class);
            return weapon != null && weapon.isAttacking();
        }

        float x() {
            return hero.getPosition().x();
        }

        void order(uz.duke.rts.message.GameMessage order) {
            game.postCommand(order);
        }

        java.util.List<uz.duke.core.thing.ObjectId> him() {
            return java.util.List.of(hero.getId());
        }
    }

    /** The hero at 150, and a skeleton that many units to his right, or none. */
    private static Field field(Float foeAt) {
        var arena = Dungeon.world(ARENA, SETTINGS);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 150f);
        if (foeAt != null) {
            game.spawn("Skeleton", arena.dungeon(), foeAt, 150f);
        }
        game.runHeadless(1);
        return new Field(game, creature(game, "Rogue"), arena.orders());
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals(template))
                .findFirst().orElseThrow();
    }

    // ---- it is an order ----

    /** ★ Told to defend while he is walking, he stands. */
    @Test
    void defendStopsAWalk() {
        var field = field(null);
        field.order(new uz.duke.rts.message.GameMessage.MoveTo(
                field.game().getLocalPlayerIndex(), field.him(),
                new Coord3D(350f, 150f, 0f)));
        field.game().runHeadless(10);
        assertTrue(field.walking(), "the premise: he set off");
        float reached = field.x();

        assertTrue(KEYS.pressNow(field.game(), 'F'), "F is not a key that acts at once");
        field.game().runHeadless(10);

        assertFalse(field.walking(), "told to defend, he walked on");
        assertEquals(reached, field.x(), 4f, "he carried on for another "
                + (field.x() - reached) + " after being told to stand where he was");
    }

    /**
     * ★ And told to defend while he is chasing, he stops chasing.
     *
     * <p>The harder half, and the one that reaches past the order into the brain:
     * dropping the target is not enough on its own, because the note about what
     * the player once pointed him at outlives the target itself — so he picks the
     * same creature up again as soon as it is near enough to shoot, reads it as
     * the old order, and sets off after it.
     */
    @Test
    void defendEndsAChase() {
        var field = field(260f);
        field.order(new uz.duke.rts.message.GameMessage.AttackObject(
                field.game().getLocalPlayerIndex(), field.him(),
                creature(field.game(), "Skeleton").getId()));
        field.game().runHeadless(10);
        assertTrue(field.walking(), "the premise: he set off after it");

        KEYS.pressNow(field.game(), 'F');
        field.game().runHeadless(30);

        assertFalse(field.walking(), "told to defend, he went on chasing");
    }

    // ---- and standing is not the same as doing nothing ----

    /**
     * Defending, he fights back.
     *
     * <p>The whole difference between this button and Stop, which stands and
     * starts nothing. Thirty off, which is inside the skeleton's own
     * {@code SenseRadius}: it notices him and comes at him, so what is being asked
     * here is not "does he shoot at things" but "does he answer something that is
     * hitting him while he is under orders to stand".
     *
     * <p>The other side of the pair — that Stop does NOT answer it — is
     * {@code HoldGroundTest.holdingHisGroundHeLeavesItAlone}, and the two are only
     * worth anything read together.
     */
    @Test
    void defendingHeFightsBack() {
        var field = field(180f);
        KEYS.pressNow(field.game(), 'F');

        field.game().runHeadless(40);

        assertTrue(field.shooting(), "it came at him and he stood there taking it");
    }

    /**
     * And a hero who has come to rest is defending, without being told to.
     *
     * <p>The state he is in for most of a run: he finishes what he was sent to do
     * and then watches the ground he is on. A hero who arrived somewhere and had
     * to be told to look up would be one the player has to remember to switch on.
     */
    @Test
    void comingToRestHeIsDefending() {
        var field = field(220f);
        field.order(new uz.duke.rts.message.GameMessage.MoveTo(
                field.game().getLocalPlayerIndex(), field.him(),
                new Coord3D(180f, 150f, 0f)));

        field.game().runHeadless(120);

        assertFalse(field.walking(), "the premise: he got there");
        assertTrue(field.shooting(),
                "he came to rest with a skeleton on top of him and did nothing about it");
    }
}
