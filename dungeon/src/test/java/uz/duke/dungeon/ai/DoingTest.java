package uz.duke.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;
import uz.duke.rts.module.WeaponUpdate;

/**
 * Exactly one of the four buttons is lit, and it is the right one.
 *
 * <p>The buttons were four things to press and nothing else, which is half a
 * control. Nobody plays by clicking them — the mouse and the keys are faster and
 * always were — so what earns them their space on the bar is what they can tell
 * the player: that the order he gave took, that his hero is still walking, that
 * the thing he is looking at across the room is coming for him.
 *
 * <p>Which makes the two ways this can be wrong both silent. A state that lights
 * nothing leaves the bar looking broken; a state that lights two makes it a
 * decoration. Both are the sort of thing a reader would assume could not happen.
 */
class DoingTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

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

    private record Fight(DukeGame game, GameObject hero, GameObject skeleton, Orders orders) {
    }

    private static Fight fight(float apart) {
        var arena = Dungeon.world(arena(), SETTINGS);
        var game = arena.game();
        game.spawn("Hero", arena.hero(), 150f, 150f);
        game.spawn("Skeleton", arena.dungeon(), 150f + apart, 150f);
        game.runHeadless(1);
        return new Fight(game, creature(game, "Hero"), creature(game, "Skeleton"),
                arena.orders());
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(template))
                .findFirst().orElseThrow();
    }

    /**
     * Idle is a state, not the absence of one.
     *
     * <p>A creature with nothing to do is guarding the ground it is on and will
     * start a fight with whatever comes near — which is what the fourth button
     * means, and the reason the bar is never blank.
     */
    @Test
    void withNothingToDoItIsGuarding() {
        // Far enough that neither has noticed the other.
        var fight = fight(10_000f);

        assertEquals(Doing.GUARDING, Doing.of(fight.hero(), false));
        assertEquals(Doing.GUARDING, Doing.of(fight.skeleton(), false));
    }

    /** Walking somewhere shows as walking. */
    @Test
    void walkingShowsAsWalking() {
        var fight = fight(10_000f);
        fight.hero().findModule(MoveUpdate.class)
                .moveTo(new uz.duke.core.math.Coord3D(300f, 150f, 0f));

        assertEquals(Doing.WALKING, Doing.of(fight.hero(), false));
    }

    /**
     * A fight outranks the walk that is carrying it out.
     *
     * <p>He walks at what he was sent at, so both are true while he closes — and
     * the order was the attack. A button reading "walking" while he runs down a
     * skeleton he was pointed at would be answering a question nobody asked.
     */
    @Test
    void fightingOutranksTheWalkingThatCarriesItOut() {
        var fight = fight(200f);
        fight.hero().findModule(WeaponUpdate.class).attack(fight.skeleton().getId());
        fight.hero().findModule(MoveUpdate.class)
                .moveTo(fight.skeleton().getPosition());

        assertEquals(Doing.FIGHTING, Doing.of(fight.hero(), false));
    }

    /** And a standing order outranks everything that can be read off the creature. */
    @Test
    void beingToldToStopOutranksAllOfIt() {
        var fight = fight(200f);
        fight.hero().findModule(WeaponUpdate.class).attack(fight.skeleton().getId());
        fight.hero().findModule(MoveUpdate.class).moveTo(fight.skeleton().getPosition());

        assertEquals(Doing.STANDING, Doing.of(fight.hero(), true),
                "he has been told to stand still, whatever else is true of him");
    }

    /**
     * Every state lights one button, and no two light the same one.
     *
     * <p>The whole promise of the row: the player can read it at a glance because
     * exactly one lamp is on. A second state sharing a lamp would make two quite
     * different situations look identical and nothing would say so.
     */
    @Test
    void everyStateLightsItsOwnButton() {
        var lit = EnumSet.noneOf(Doing.class);
        var buttons = new java.util.HashSet<Integer>();
        for (var doing : Doing.values()) {
            lit.add(doing);
            assertEquals(true, buttons.add(doing.button()),
                    doing + " lights a button another state already lights");
        }
        assertEquals(Doing.values().length, buttons.size());
        for (var button : buttons) {
            assertNotEquals(-1, button, "every state has to light something");
        }
    }

    /** Asked about nothing at all, it says the one thing that is safe to draw. */
    @Test
    void nothingSelectedIsGuarding() {
        assertEquals(Doing.GUARDING, Doing.of(null, false));
        assertEquals(Doing.STANDING, Doing.of(null, true));
    }
}
