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
        game.spawn("Rogue", arena.hero(), 150f, 200f);
        game.spawn(kind, arena.dungeon(), 150f + gap, 200f);
        game.runHeadless(1);
        return new Fight(game, creature(game, "Rogue"), creature(game, kind));
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
        float start = 35f;
        var runner = fight("Runner", start);
        var brute = fight("Brute", start);

        // A short window, before either has arrived: this is about speed, and once
        // both have reached their fighting distance it would measure only that.
        runner.game().runHeadless(30);
        brute.game().runHeadless(30);

        assertTrue(gapBetween(runner) < gapBetween(brute),
                "the runner should have closed further, but was " + gapBetween(runner)
                        + " against the brute's " + gapBetween(brute));
    }

    /**
     * A monster told to stop short does, and fights from there.
     *
     * <p>What separates a skirmisher from a brawler is one number, and this is
     * the number. Proved on a monster the test declares rather than on a shipped
     * one, so that re-tuning the roster cannot quietly turn it into a test of
     * nothing.
     */
    @Test
    void aMonsterToldToKeepItsDistanceFightsFromOutThere() {
        var skirmisher = DungeonSettings.parse("""
                DungeonMonster Stalker
                  SenseRadius = 150
                  ChaseRadius = 260
                  CloseDistance = 55
                End
                """);
        var arena = Dungeon.world(ARENA, skirmisher);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 200f);
        game.spawn("Stalker", arena.dungeon(), 270f, 200f);
        game.runHeadless(1);
        var fight = new Fight(game, creature(game, "Rogue"), creature(game, "Stalker"));

        game.runHeadless(400);

        float settled = gapBetween(fight);
        assertTrue(settled > 30f, "it should keep its distance, but closed to " + settled);
        assertTrue(settled < 260f, "and it should still have come, rather than ignoring him");
    }

    /**
     * Every shipped monster stops inside its own reach.
     *
     * <p>The pairing the file can get wrong: stop further away than the weapon
     * reaches and the monster stands in front of the hero doing nothing, which
     * looks like broken AI rather than like two numbers that disagree. Now that
     * they are all brawlers the margin is wide, and the check costs nothing —
     * it is the day someone re-tunes one of them that it earns itself.
     */
    @Test
    void everyMonsterStopsInsideItsOwnReach() {
        var creatures = uz.duke.dungeon.content.Content.read(
                uz.duke.dungeon.content.Content.MONSTERS)
                + uz.duke.dungeon.content.Content.read(
                        uz.duke.dungeon.content.Content.CREATURES);
        for (var kind : SETTINGS.monsters()) {
            float reach = attackRangeOf(creatures, kind.name());
            assertTrue(kind.closeDistance() < reach,
                    kind.name() + " stops at " + kind.closeDistance()
                            + " but only reaches " + reach);
        }
    }

    /**
     * Everything that carries a launcher actually looses something.
     *
     * <p>A monster shoots when four separate things agree: it stops short of the
     * hero, its weapon reaches him from there, it carries a {@code Bow} naming
     * what it throws, and that projectile exists as a template. Any one of them
     * missing and the creature stands in front of him doing nothing at all — the
     * failure looks like broken AI and is in fact two lines in a file that do not
     * match. Three creatures depend on it now, one of them a boss, so it is
     * proved for every one of them by asking the templates rather than by naming
     * them here.
     */
    @Test
    void everyThrowerActuallyThrowsSomething() {
        int throwers = 0;
        for (var kind : SETTINGS.monsters()) {
            var launcher = launcherOf(kind.name());
            if (launcher == null) {
                continue; // a brawler; it has nothing to loose
            }
            throwers++;
            // Near enough to be seen and to be within reach, far enough that it
            // is standing and shooting rather than walking.
            float gap = Math.min(kind.closeDistance(), kind.senseRadius()) * 0.9f;
            var fight = fight(kind.name(), gap);
            float heroHealth = fight.hero().getBody().getHealth();

            boolean flew = false;
            for (int frame = 0; frame < 120 && !flew; frame++) {
                fight.game().runHeadless(1);
                flew = creature(fight.game(), launcher.projectile()) != null;
            }

            assertTrue(flew, kind.name() + " should have loosed a " + launcher.projectile()
                    + " but nothing left it");
            fight.game().runHeadless(60);
            assertTrue(fight.hero().getBody().getHealth() < heroHealth,
                    kind.name() + "'s " + launcher.projectile() + " never reached the hero");
        }
        assertTrue(throwers >= 3, "the roster should still hold the throwers, but found "
                + throwers);
    }

    /** The {@code Bow} a kind's template carries, or {@code null} if it has none. */
    private static uz.duke.dungeon.combat.Bow.Data launcherOf(String kind) {
        var fight = fight(kind, 900f);
        for (var module : fight.monster().getTemplate().getModules()) {
            if (module.data() instanceof uz.duke.dungeon.combat.Bow.Data bow) {
                return bow;
            }
        }
        return null;
    }

    /** {@code AttackRange} out of a creature block, read as the loader would. */
    private static float attackRangeOf(String iniText, String template) {
        boolean inside = false;
        for (var line : iniText.split("\n")) {
            var trimmed = line.trim();
            if (trimmed.startsWith("Object ")) {
                inside = trimmed.equals("Object " + template);
            } else if (inside && trimmed.startsWith("AttackRange")) {
                return Float.parseFloat(trimmed.substring(trimmed.indexOf('=') + 1).trim());
            }
        }
        return 0f;
    }

    /** The skeleton, by contrast, walks all the way in. */
    @Test
    void theSkeletonClosesAllTheWay() {
        var fight = fight("Skeleton", 30f);

        fight.game().runHeadless(400);

        assertTrue(gapBetween(fight) < 30f,
                "a melee monster should have closed, but stopped at " + gapBetween(fight));
    }

    /**
     * A blow it has begun, it finishes.
     *
     * <p>Step out of reach mid-swing and the monster stands there completing it,
     * rather than lowering its arm and setting off after you. That window is the
     * whole reward for moving: without it a monster followed the hero around the
     * room with its arm permanently raised, never landing anything and never
     * costing him anything either.
     *
     * <p>Fought with the brute, which has the longest swing in the dungeon and so
     * the widest window — the mechanism is the same for all of them, and a short
     * one would be measuring the frame counter rather than the behaviour.
     */
    @Test
    void aMonsterFinishesTheBlowItHasStarted() {
        var brute = SETTINGS.monsters().stream()
                .filter(kind -> kind.name().equals("Brute")).findFirst().orElseThrow();
        var fight = fight("Brute", 6f); // already within reach
        float health = fight.hero().getBody().getHealth();

        // Run until it actually lands one. Recovery follows the blow, not the
        // standing about beforehand.
        int waited = 0;
        while (fight.hero().getBody().getHealth() >= health && waited++ < 200) {
            fight.game().runHeadless(1);
        }
        assertTrue(waited < 200, "the brute never hit him, so there is no swing to finish");

        // Carried out of reach the instant it struck, so what follows is only the
        // swing. Walked, he would still be within reach for some of it.
        fight.hero().setPosition(new Coord3D(400f, 200f, 0f));
        var stoodAt = fight.monster().getPosition();
        fight.game().runHeadless(brute.swingFrames() - 2);

        assertEquals(0f, fight.monster().getPosition().distance(stoodAt), 0.5f,
                "it should still be finishing the blow, not chasing");

        fight.game().runHeadless(20);

        assertTrue(fight.monster().getPosition().distance(stoodAt) > 2f,
                "and then it should have come after him");
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
        int boss = experienceValueOf("Champion");

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
        game.spawn("Rogue", arena.hero(), 150f, 200f);
        // Beyond the hero's bow as well as its own sight. Being shot at rouses a
        // monster whatever its sense radius, which is right — but it is a second
        // way to wake one, and this test is about the first.
        game.spawn("Skeleton", arena.dungeon(), 300f, 200f);
        game.runHeadless(1);
        var monster = creature(game, "Skeleton");
        var startedAt = monster.getPosition();

        game.runHeadless(200);

        assertEquals(0f, monster.getPosition().distance(startedAt), 1f,
                "a skeleton that can see one unit ahead should never notice him");
    }

    /**
     * Shoot something and it comes for you, however deaf it is.
     *
     * <p>The same blind skeleton as the test above, at a distance it could never
     * notice anyone from — but this time inside the hero's bow. An archer who
     * outranges the whole floor would otherwise stand in the dark killing things
     * one at a time while they waited their turn, and a monster that can be shot
     * without answering is not a monster, it is a target.
     *
     * <p>Nothing tells it who fired. It does not need telling: there is one hero
     * down here, so losing health means he did it.
     */
    @Test
    void somethingShotComesForTheShooterHoweverDeafItIs() {
        var blind = DungeonSettings.parse("""
                DungeonMonster Skeleton
                  SenseRadius = 1
                  ChaseRadius = 1
                  CloseDistance = 4
                End
                """);
        var arena = Dungeon.world(ARENA, blind);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 200f);
        game.spawn("Skeleton", arena.dungeon(), 200f, 200f); // inside his bow, outside its ears
        game.runHeadless(1);
        var monster = creature(game, "Skeleton");
        var hero = creature(game, "Rogue");
        float gapBefore = monster.getPosition().distance(hero.getPosition());

        game.runHeadless(200);

        assertTrue(monster.getPosition().distance(hero.getPosition()) < gapBefore - 20f,
                "it was shot and stayed where it was, " + gapBefore + " away");
    }

    /** Being healed is not being hit — one of these mends itself as it fights. */
    @Test
    void mendingItselfDoesNotCountAsBeingAttacked() {
        var blind = DungeonSettings.parse("""
                DungeonMonster Revenant
                  SenseRadius = 1
                  ChaseRadius = 1
                  CloseDistance = 4
                End
                """);
        var arena = Dungeon.world(ARENA, blind);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), 150f, 200f);
        game.spawn("Revenant", arena.dungeon(), 320f, 200f); // beyond bow and ears alike
        game.runHeadless(1);
        var monster = creature(game, "Revenant");
        var startedAt = monster.getPosition();

        game.runHeadless(300);

        assertEquals(0f, monster.getPosition().distance(startedAt), 1f,
                "its own healing woke it up");
    }

    /** The arena is only the stage; the coordinates are the test's. */
    @Test
    void theArenaIsOpenGround() {
        var fight = fight("Skeleton", 100f);
        assertEquals(new Coord3D(150f, 200f, 0f).x(), fight.hero().getPosition().x(), 0.01f);
    }
}
