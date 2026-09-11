package uz.duke.dungeon.run;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;

/**
 * The bar describes whatever is selected, not always the hero.
 *
 * <p>Two halves that cannot see each other: the client knows <em>which</em>
 * creature, because selection is a fact about a screen, and the simulation knows
 * <em>what it is worth</em>, because damage is a template times whatever this
 * floor multiplies by. They meet over one command — see {@link Watching} — and
 * this holds the meeting still.
 *
 * <p>A creature's card is deliberately shorter than his. The fields it leaves out
 * are as much the point as the ones it carries: a skeleton with an experience bar
 * and an empty bag drawn under it would be a second hero rather than a monster.
 */
class WatchingTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private record Watched(DukeGame game, GameObject hero, GameObject skeleton,
            uz.duke.dungeon.ai.Orders orders) {

        String line() {
            game.runHeadless(1);
            return game.getSnapshot().status();
        }

        void pickOut(GameObject creature) {
            orders.watch(hero.getPlayerIndex(), creature == null ? null : creature.getId());
        }
    }

    /**
     * A real dungeon, with the hero and the first creature in it that is not his.
     *
     * <p>The generated game rather than a purpose-built room, because the thing
     * being tested is the whole chain — command to standing order to status line —
     * and a hand-wired run would prove only that this test can wire one.
     */
    private static Watched standoff() {
        var session = Dungeon.newSession(7L, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals("Hero"))
                .findFirst().orElseThrow();
        var skeleton = game.getLogic().getObjects().stream()
                .filter(o -> o.getPlayerIndex() != hero.getPlayerIndex())
                .filter(o -> o.getBody() != null && !o.isEffectivelyDead())
                .filter(o -> o.findModule(uz.duke.rts.module.WeaponUpdate.class) != null)
                .findFirst().orElseThrow();
        return new Watched(game, hero, skeleton, session.orders());
    }

    @Test
    void withNothingPickedOutTheBarIsHis() {
        var watched = standoff();

        var line = watched.line();

        assertTrue(line.startsWith("name=Erika"), line);
        assertTrue(line.contains("|skill="), "his own card carries his skills: " + line);
    }

    @Test
    void pickingOutASkeletonDescribesTheSkeleton() {
        var watched = standoff();
        watched.pickOut(watched.skeleton());

        var line = watched.line();

        assertTrue(line.startsWith("name="), line);
        assertFalse(line.startsWith("name=Erika"), "it should have stopped being about him: " + line);
        assertTrue(line.contains("|hp="), "how much of it there is: " + line);
        assertTrue(line.contains("|stat="), "and what it hits for: " + line);
    }

    /**
     * And it leaves out everything that is his rather than the creature's.
     *
     * <p>The half of the feature that is easy to get wrong by doing nothing: a
     * card built by copying the hero's and changing the numbers would carry his
     * skills, his bag and his experience under a skeleton's name.
     */
    @Test
    void aSkeletonsCardCarriesNothingThatIsHis() {
        var watched = standoff();
        watched.pickOut(watched.skeleton());

        var line = watched.line();

        assertFalse(line.contains("|xp="), "a skeleton is not earning anything: " + line);
        assertFalse(line.contains("|skill="), "nor casting anything: " + line);
        assertFalse(line.contains("|it="), "nor carrying anything: " + line);
        assertFalse(line.contains("|cmd="), "nor taking orders: " + line);
        assertFalse(line.contains("|rank="), "nor holding a level: " + line);
    }

    /** The floor is the floor, whoever is being looked at. */
    @Test
    void theDepthStaysOnTheBar() {
        var watched = standoff();
        watched.pickOut(watched.skeleton());

        var line = watched.line();

        assertTrue(line.contains("|depth="), "the corner should not go blank: " + line);
        assertTrue(line.contains("|face="), "and the frame should stop showing an archer: " + line);
    }

    /** Letting go puts his own card back. */
    @Test
    void lettingGoGivesHimTheBarBack() {
        var watched = standoff();
        watched.pickOut(watched.skeleton());
        assertFalse(watched.line().startsWith("name=Erika"));

        watched.pickOut(null);

        assertTrue(watched.line().startsWith("name=Erika"), "his own card should be back");
    }

    /**
     * Picking out one of his own is not a creature card at all.
     *
     * <p>Clicking the hero is the ordinary case and must give the whole bar —
     * skills, bag and all — rather than a two-figure card about himself.
     */
    @Test
    void pickingOutHisOwnHeroIsStillTheWholeBar() {
        var watched = standoff();
        watched.pickOut(watched.hero());

        var line = watched.line();

        assertTrue(line.startsWith("name=Erika"), line);
        assertTrue(line.contains("|skill="), "his own card, entire: " + line);
    }

    /** And a creature that dies hands the bar back rather than leaving a corpse on it. */
    @Test
    void aDeadCreatureGivesTheBarBack() {
        var watched = standoff();
        watched.pickOut(watched.skeleton());
        assertFalse(watched.line().startsWith("name=Erika"));

        watched.skeleton().getBody().damage(100000f, uz.duke.core.module.DamageType.EXPLOSION);

        assertTrue(watched.line().startsWith("name=Erika"),
                "a panel describing a corpse is a panel that looks broken");
    }
}
