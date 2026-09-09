package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;
import uz.duke.rts.message.GameMessage;
import uz.duke.rts.module.ExperienceModule;

/**
 * The hero's shots are real: they leave the bow, cross the distance, and hurt
 * what they reach when they reach it.
 *
 * <p>The whole difference from a weapon that simply hits is time, and everything
 * here is about what happens during it — the arrow existing, the victim being
 * unharmed while it is in the air, the target moving underneath it, and the
 * experience for a kill that the weapon could not credit because nothing had died
 * yet when it let go.
 */
class ArrowTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static String arena() {
        int width = 60;
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

    private record Duel(DukeGame game, GameObject hero, GameObject victim) {
    }

    /** The hero and one skeleton, a chosen distance apart, and the shot ordered. */
    private static Duel shootAt(float gap) {
        var world = Dungeon.world(arena(), SETTINGS);
        var game = world.game();
        game.spawn("Hero", world.hero(), 150f, 150f);
        game.spawn("Skeleton", world.dungeon(), 150f + gap, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Hero");
        var victim = creature(game, "Skeleton");
        game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                List.of(hero.getId()), victim.getId()));
        return new Duel(game, hero, victim);
    }

    /**
     * The first frame after the spawn is when the shot is loosed: everything here
     * is inside his bow, so his weapon acquires and fires without waiting for the
     * order to be applied. The order is posted anyway, since aiming him at a
     * particular thing is what the test means.
     */

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals(template))
                .findFirst().orElse(null);
    }

    private static long arrowsInTheAir(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Arrow"))
                .count();
    }

    /** Firing puts something in the world rather than simply hurting the target. */
    @Test
    void aShotBecomesAnArrow() {
        var duel = shootAt(70f);

        duel.game().runHeadless(2);

        assertTrue(arrowsInTheAir(duel.game()) > 0, "the bow fired but nothing left it");
    }

    /**
     * And the target is untouched while it is on its way.
     *
     * <p>The point of the whole change: the weapon no longer lands its own shot,
     * so a monster hit at range is hurt where the arrow is, not where the archer
     * is.
     */
    @Test
    void nothingIsHurtWhileTheArrowIsStillFlying() {
        var duel = shootAt(70f);
        float before = duel.victim().getBody().getHealth();

        duel.game().runHeadless(2);

        assertTrue(arrowsInTheAir(duel.game()) > 0, "it should be in the air");
        assertEquals(before, duel.victim().getBody().getHealth(), 0.01f,
                "but nothing has reached the skeleton yet");
    }

    /** It arrives, and then it hurts. */
    @Test
    void whenItArrivesTheDamageLands() {
        var duel = shootAt(70f);
        float before = duel.victim().getBody().getHealth();

        duel.game().runHeadless(30);

        assertTrue(duel.victim().getBody().getHealth() < before,
                "the arrow never got there");
    }

    /**
     * It follows what it was loosed at.
     *
     * <p>A shot that led its target would miss whenever the target turned, and the
     * hero's range would be a suggestion. Here the skeleton is walking at him the
     * whole time the arrow is out, and it still connects.
     */
    @Test
    void itChasesATargetThatIsMoving() {
        var duel = shootAt(80f);
        var startedAt = duel.victim().getPosition();
        float before = duel.victim().getBody().getHealth();

        duel.game().runHeadless(40);

        assertTrue(duel.victim().getPosition().distance(startedAt) > 5f,
                "the skeleton should have been moving for this to say anything");
        assertTrue(duel.victim().getBody().getHealth() < before, "and still been hit");
    }

    /**
     * It appears at the bow, not inside the archer.
     *
     * <p>Started at his own position it comes out of his chest, which is what it
     * looked like. The bow is held out in front, and in front is where he is
     * facing, because he turns to shoot.
     */
    @Test
    void anArrowStartsOutInFrontOfTheArcher() {
        var duel = shootAt(70f);
        var arrow = firstArrow(duel);

        float outInFront = arrow.getPosition().distance(duel.hero().getPosition());
        assertTrue(outInFront > 1f, "it started inside him, " + outInFront + " away");
        assertTrue(arrow.getPosition().distance(duel.victim().getPosition())
                        < duel.hero().getPosition().distance(duel.victim().getPosition()),
                "and in front of him rather than behind");
    }

    /**
     * Except against something almost touching him, where "in front" would be
     * past it, and the arrow would have to turn round and come back.
     */
    @Test
    void aShotAtSomethingCloseStartsShortOfIt() {
        var duel = shootAt(12f);
        var arrow = firstArrow(duel);

        float heroToVictim = duel.hero().getPosition().distance(duel.victim().getPosition());
        float arrowToVictim = arrow.getPosition().distance(duel.victim().getPosition());
        assertTrue(arrowToVictim > 0f && arrowToVictim <= heroToVictim,
                "it should have appeared between them, not past the target");
    }

    /**
     * The arrow as it is loosed, before it has moved.
     *
     * <p>Caught on the frame it is made rather than a frame later, because at
     * close range there is no later: an arrow crosses eight units a frame, so one
     * loosed at something twelve away has already landed and gone by the next
     * time anybody looks.
     */
    private static GameObject firstArrow(Duel duel) {
        var arrow = creature(duel.game(), "Arrow");
        if (arrow == null) {
            duel.game().runHeadless(1);
            arrow = creature(duel.game(), "Arrow");
        }
        assertNotNull(arrow, "one should have been loosed");
        return arrow;
    }

    /** Nothing stays in the air for ever: an arrow that lands is gone. */
    @Test
    void anArrowThatArrivesIsTakenAway() {
        var duel = shootAt(40f);

        duel.game().runHeadless(2);
        assertTrue(arrowsInTheAir(duel.game()) > 0);
        duel.game().runHeadless(20);

        assertEquals(0, arrowsInTheAir(duel.game()), "it hit and should be gone");
    }

    /**
     * An arrow whose target dies before it lands goes away too.
     *
     * <p>Otherwise it would follow a corpse that is no longer there, and the
     * dungeon would fill up quietly with things nobody can see.
     */
    @Test
    void anArrowWhoseTargetDiesFirstIsGivenUp() {
        var duel = shootAt(80f);
        duel.game().runHeadless(2);
        assertTrue(arrowsInTheAir(duel.game()) > 0, "one is in the air");

        duel.victim().getBody().damage(10000f); // something else finishes it
        duel.game().runHeadless(4);

        assertEquals(0, arrowsInTheAir(duel.game()), "it should have been given up on");
    }

    /**
     * A kill by arrow still earns experience.
     *
     * <p>The engine's weapon credits a kill when its shot lands, and its shot no
     * longer lands — the victim was alive when it let go. So the arrow has to do
     * it, and if it did not, levelling would quietly stop working the day archery
     * arrived.
     */
    @Test
    void killingWithAnArrowStillEarnsExperience() {
        var duel = shootAt(60f);
        var experience = duel.hero().findModule(ExperienceModule.class);
        assertNotNull(experience);
        assertEquals(0, experience.getExperience(), "he has killed nothing yet");
        duel.victim().getBody().damage(duel.victim().getBody().getHealth() - 1f); // one hit left

        duel.game().runHeadless(60);

        assertTrue(duel.victim().isEffectivelyDead(), "the arrow should have finished it");
        assertTrue(experience.getExperience() > 0,
                "and the kill should have been credited to him");
    }

    /**
     * Arrows are not things to be shot at, and not things to bump into.
     *
     * <p>Neither is stated anywhere in the data — they follow from the arrow
     * having no body and no geometry — so they are stated here, where breaking
     * either would be caught.
     */
    @Test
    void anArrowIsNeitherATargetNorAnObstacle() {
        var duel = shootAt(80f);
        duel.game().runHeadless(2);

        var arrow = creature(duel.game(), "Arrow");
        assertNotNull(arrow, "one should be in the air");
        assertTrue(arrow.getBody() == null,
                "with a body, monsters would acquire it as a target");
        assertTrue(arrow.getTemplate().getGeometry().isPoint(),
                "with a shape, it would shoulder monsters aside on its way past");
    }

    /** The hero still fights: none of this leaves him unable to kill anything. */
    @Test
    void heCanStillKillWhatHeShootsAt() {
        var duel = shootAt(70f);

        duel.game().runHeadless(400);

        assertTrue(duel.victim().isEffectivelyDead(), "a skeleton should not survive that");
        assertFalse(duel.hero().isEffectivelyDead(), "and he should have won at range");
    }
}
