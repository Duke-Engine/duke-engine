package uz.duke.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;

/**
 * Each kind of monster behaves like the thing its name promises.
 *
 * <p>They share one brain, so what makes a runner a runner is entirely in the
 * numbers — which is exactly the claim worth testing. If a brute and a runner
 * fought the same way, the parameters would be decoration.
 *
 * <p>Fought in an open arena so the distances are the test's own, but through the
 * seam the real dungeon is built on: the same creatures, the same behaviour, the
 * same data files.
 */
class MonsterKindsTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String ARENA = arena();

    private static String arena() {
        var text = new StringBuilder();
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 60; x++) {
                boolean edge = x == 0 || y == 0 || x == 59 || y == 39;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private record Fight(DukeGame game, GameObject hero, GameObject monster) {
    }

    /** A standing hero and one monster of the named kind, a set distance away. */
    private static Fight fight(String kind, float gap) {
        var arena = Dungeon.world(ARENA, SETTINGS);
        var game = arena.game();
        game.spawn("Hero", arena.hero(), 150f, 200f);
        game.spawn(kind, arena.dungeon(), 150f + gap, 200f);
        game.runHeadless(1);
        return new Fight(game, creature(game, "Hero"), creature(game, kind));
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(template))
                .findFirst().orElse(null);
    }

    private static float gapBetween(Fight fight) {
        return fight.hero().getPosition().distance(fight.monster().getPosition());
    }

    /** Every kind the file names can actually be built and behaves without error. */
    @Test
    void everyKindTheFileNamesCanBeSpawned() {
        for (var kind : SETTINGS.monsters()) {
            // Far beyond anything notices, so this asks only whether it can be built.
            var fight = fight(kind.name(), 900f);
            assertNotNull(fight.monster(), kind.name() + " should have been spawned");
            fight.game().runHeadless(60);
            assertNotNull(creature(fight.game(), kind.name()),
                    kind.name() + " should have survived a quiet minute");
        }
    }

    /** The runner is the one you cannot walk away from. */
    @Test
    void theRunnerClosesFasterThanTheBrute() {
        // Inside the brute's shorter notice, so both have seen him and only
        // their speed differs.
        float start = 65f;
        var runner = fight("Runner", start);
        var brute = fight("Brute", start);

        runner.game().runHeadless(120);
        brute.game().runHeadless(120);

        assertTrue(gapBetween(runner) < gapBetween(brute),
                "the runner should have closed further, but was " + gapBetween(runner)
                        + " against the brute's " + gapBetween(brute));
    }

    /**
     * The archer stops well short and shoots from there — the whole reason it is a
     * different thing to fight rather than a differently coloured skeleton.
     */
    @Test
    void theArcherStopsShortAndShootsFromThere() {
        var fight = fight("Archer", 120f);
        float heroHealthBefore = fight.hero().getBody().getHealth();

        fight.game().runHeadless(400);

        float settled = gapBetween(fight);
        assertTrue(settled > 30f,
                "the archer should keep its distance, but closed to " + settled);
        assertTrue(fight.hero().getBody().getHealth() < heroHealthBefore,
                "and hit him from out there");
    }

    /** The skeleton, by contrast, walks all the way in. */
    @Test
    void theSkeletonClosesAllTheWay() {
        var fight = fight("Skeleton", 80f);

        fight.game().runHeadless(400);

        assertTrue(gapBetween(fight) < 30f,
                "a melee monster should have closed, but stopped at " + gapBetween(fight));
    }

    /** The revenant heals itself: hurt it and leave it, and the damage comes back. */
    @Test
    void theRevenantHealsWhatYouDoNotFinish() {
        // Far enough away that it never notices him and simply stands there.
        var fight = fight("Revenant", 900f);
        var body = fight.monster().getBody();
        body.damage(40f);
        float wounded = body.getHealth();

        fight.game().runHeadless(120);

        assertTrue(body.getHealth() > wounded,
                "it should have recovered, but sat at " + body.getHealth());
    }

    /** A monster that notices nothing stays where it was put. */
    @Test
    void aMonsterThatCannotSeeHimStaysPut() {
        var fight = fight("Skeleton", 900f);
        var startedAt = fight.monster().getPosition();

        fight.game().runHeadless(120);

        assertTrue(fight.monster().getPosition().distance(startedAt) < 1f,
                "nothing to chase, so nothing to do");
    }

    /** Tougher kinds are worth more, so choosing a harder fight pays for itself. */
    @Test
    void aHarderMonsterIsWorthMoreExperience() {
        int runner = experienceValueOf("Runner");
        int brute = experienceValueOf("Brute");
        int boss = experienceValueOf("Boss");

        assertTrue(brute > runner, "a brute should be worth more than a runner");
        assertTrue(boss > brute, "and the boss more than either");
    }

    private static int experienceValueOf(String kind) {
        var fight = fight(kind, 900f);
        return fight.monster().findModule(uz.duke.rts.module.ExperienceModule.class)
                .getExperienceValue();
    }

    /** Behaviour is data: a re-tuned file gives a differently behaved monster. */
    @Test
    void changingTheFileChangesHowAKindBehaves() {
        var blind = DungeonSettings.parse("""
                DungeonMonster Skeleton
                  SenseRadius = 1
                  ChaseRadius = 1
                  CloseDistance = 4
                  RepathFrames = 10
                  MinDepth = 1
                  Weight = 1
                End
                """);
        var arena = Dungeon.world(ARENA, blind);
        var game = arena.game();
        game.spawn("Hero", arena.hero(), 150f, 200f);
        game.spawn("Skeleton", arena.dungeon(), 230f, 200f);
        game.runHeadless(1);
        var monster = creature(game, "Skeleton");
        var startedAt = monster.getPosition();

        game.runHeadless(200);

        assertEquals(0f, monster.getPosition().distance(startedAt), 1f,
                "a skeleton that can see one unit ahead should never notice him");
    }

    /** The arena is only the stage; the coordinates are the test's. */
    @Test
    void theArenaIsOpenGround() {
        var fight = fight("Skeleton", 100f);
        assertEquals(new Coord3D(150f, 200f, 0f).x(), fight.hero().getPosition().x(), 0.01f);
    }
}
