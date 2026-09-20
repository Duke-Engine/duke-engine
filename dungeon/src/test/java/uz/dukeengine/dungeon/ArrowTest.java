package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.ExperienceModule;

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
        game.spawn("Rogue", world.hero(), 150f, 150f);
        game.spawn("Skeleton", world.dungeon(), 150f + gap, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Rogue");
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
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static long arrowsInTheAir(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals("Arrow"))
                .count();
    }

    /** Firing puts something in the world rather than simply hurting the target. */
    @Test
    void aShotBecomesAnArrow() {
        var duel = shootAt(50f);

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
        var duel = shootAt(50f);
        float before = duel.victim().getBody().getHealth();

        duel.game().runHeadless(2);

        assertTrue(arrowsInTheAir(duel.game()) > 0, "it should be in the air");
        assertEquals(before, duel.victim().getBody().getHealth(), 0.01f,
                "but nothing has reached the skeleton yet");
    }

    /** It arrives, and then it hurts. */
    @Test
    void whenItArrivesTheDamageLands() {
        var duel = shootAt(50f);
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
        var duel = shootAt(50f);
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
        var duel = shootAt(50f);
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
        var duel = shootAt(30f);

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
        var duel = shootAt(50f);
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
        var duel = shootAt(45f);
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
        var duel = shootAt(50f);
        duel.game().runHeadless(2);

        var arrow = creature(duel.game(), "Arrow");
        assertNotNull(arrow, "one should be in the air");
        assertTrue(arrow.getBody() == null,
                "with a body, monsters would acquire it as a target");
        assertTrue(arrow.getGeometry().isPoint(),
                "with a shape, it would shoulder monsters aside on its way past");
    }

    /**
     * He shoots or he walks, never both.
     *
     * <p>A bow is drawn standing still. An archer who looses arrows at a jog is an
     * archer for whom moving costs nothing, and then the range is not something he
     * has to hold — it is something he keeps while retreating for ever.
     */
    @Test
    void heDoesNotShootWhileWalking() {
        var world = Dungeon.world(arena(), SETTINGS);
        var game = world.game();
        game.spawn("Rogue", world.hero(), 150f, 150f);
        // Beyond his bow to begin with, and beyond it again when he arrives, so
        // the only stretch in which he could hit it is the walk past it.
        game.spawn("Skeleton", world.dungeon(), 300f, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Rogue");
        var victim = creature(game, "Skeleton");
        var destination = new Coord3D(450f, 150f, 0f);

        game.postCommand(new GameMessage.MoveTo(game.getLocalPlayerIndex(),
                List.of(hero.getId()), destination));
        game.runHeadless(360);

        assertTrue(hero.getPosition().distance(destination) < 20f,
                "he should have walked past it and arrived, but stopped at "
                        + hero.getPosition());
        assertEquals(victim.getBody().getMaxHealth(), victim.getBody().getHealth(), 0.01f,
                "he shot it on the way past");
    }

    /** And starts again the moment he stops. */
    @Test
    void heShootsAgainOnceHeIsStanding() {
        var world = Dungeon.world(arena(), SETTINGS);
        var game = world.game();
        game.spawn("Rogue", world.hero(), 150f, 150f);
        game.spawn("Skeleton", world.dungeon(), 300f, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Rogue");
        var victim = creature(game, "Skeleton");

        // Sent to a spot the skeleton is standing within bow-shot of, and then
        // left there long enough for the bow to come up.
        game.postCommand(new GameMessage.MoveTo(game.getLocalPlayerIndex(),
                List.of(hero.getId()), new Coord3D(250f, 150f, 0f)));
        game.runHeadless(240);

        assertTrue(victim.getBody().getHealth() < victim.getBody().getMaxHealth(),
                "standing within reach of it, he should have shot it");
    }

    /**
     * Sending him somewhere still outranks an attack order, which is the thing
     * remembering the target himself could most easily have broken.
     *
     * <p>The brain now keeps hold of what he was pointed at, because walking
     * disarms him and a disarmed weapon has forgotten. Something has to notice
     * when the player changes his mind, and what notices is where he is headed.
     */
    @Test
    void aMoveOrderStillCancelsAnAttackOrder() {
        var world = Dungeon.world(arena(), SETTINGS);
        var game = world.game();
        game.spawn("Rogue", world.hero(), 150f, 150f);
        game.spawn("Skeleton", world.dungeon(), 320f, 150f); // beyond his bow: an order
        game.runHeadless(1);
        var hero = creature(game, "Rogue");
        var victim = creature(game, "Skeleton");

        game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                List.of(hero.getId()), victim.getId()));
        game.runHeadless(20);
        assertTrue(hero.getPosition().x() > 155f, "he should have set off toward it");

        // Well off his line to the skeleton, and inside the arena: the map this
        // test builds is 60 by 30 cells, which is 600 by 300 in world units.
        var away = new Coord3D(150f, 250f, 0f);
        game.postCommand(new GameMessage.MoveTo(game.getLocalPlayerIndex(),
                List.of(hero.getId()), away));
        game.runHeadless(500);

        assertTrue(hero.getPosition().distance(away) < 25f,
                "he went back to the skeleton instead, ending at " + hero.getPosition());
    }

    /** The hero still fights: none of this leaves him unable to kill anything. */
    @Test
    void heCanStillKillWhatHeShootsAt() {
        var duel = shootAt(50f);

        duel.game().runHeadless(400);

        assertTrue(duel.victim().isEffectivelyDead(), "a skeleton should not survive that");
        assertFalse(duel.hero().isEffectivelyDead(), "and he should have won at range");
    }
}
