package uz.duke.dungeon.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;
import uz.duke.rts.message.GameMessage;
import uz.duke.rts.module.ExperienceModule;

/**
 * The hero getting stronger by killing things, in the game he is actually played
 * in — its creatures, its behaviour, its data files.
 *
 * <p>Fought in an open arena rather than a generated dungeon so the distances are
 * the test's own, and one skeleton at a time so the hero survives long enough to
 * have a career. Six at once would swarm and kill him, which is the game working
 * correctly but would tell you nothing about levelling.
 */
class HeroProgressTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Levels that arrive fast and hit hard, so a mechanism shows plainly. */
    private static final DungeonSettings BRISK = DungeonSettings.parse("""
            DungeonLeveling Progression
              MaxLevel = 10
              XpBase = 10
              XpStep = 0
              HealthPerLevel = 40
              DamagePercentPerLevel = 60
              ArmourPercentPerLevel = 10
              MinDamageTakenPercent = 40
            End
            """);

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

    private record Fight(Dungeon.Arena arena, HeroProgress progress) {
        DukeGame game() {
            return arena.game();
        }
    }

    private static Fight start(DungeonSettings settings) {
        var arena = Dungeon.world(ARENA, settings);
        var game = arena.game();
        game.spawn("Hero", arena.hero(), 200f, 150f);
        var progress = new HeroProgress(arena.hero(), settings.levelling(),
                settings.levelUpBannerFrames());
        game.onTick(progress::tick);
        game.runHeadless(1);
        return new Fight(arena, progress);
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(template))
                .findFirst().orElse(null);
    }

    private static GameObject hero(DukeGame game) {
        return creature(game, "Hero");
    }

    /**
     * One skeleton, fought to the death. Returns how many frames it took, so the
     * effect of levelling can be measured on the clock rather than read off a stat.
     */
    private static int killOne(Fight fight) {
        var game = fight.game();
        game.spawn("Skeleton", fight.arena().dungeon(), 260f, 150f);
        game.runHeadless(1);
        var skeleton = creature(game, "Skeleton");
        assertNotNull(skeleton, "a skeleton should have been spawned to fight");
        var skeletonId = skeleton.getId();

        game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                List.of(hero(game).getId()), skeletonId));

        int frames = 0;
        while (game.getLogic().findObject(skeletonId) != null && frames < 2000) {
            game.runHeadless(1);
            frames++;
        }
        assertTrue(frames < 2000, "the hero should have finished the skeleton off");
        assertNotNull(hero(game), "and survived doing it");
        return frames;
    }

    private static int experienceOf(DukeGame game) {
        return hero(game).findModule(ExperienceModule.class).getExperience();
    }

    @Test
    void killingASkeletonEarnsExperience() {
        var fight = start(SETTINGS);
        assertEquals(0, experienceOf(fight.game()), "he starts having killed nothing");

        killOne(fight);

        assertTrue(experienceOf(fight.game()) > 0, "a kill should be worth something");
    }

    @Test
    void enoughExperienceRaisesTheLevel() {
        var fight = start(SETTINGS);
        assertEquals(1, fight.progress().getLevel(), "he starts at the first level");

        for (int kills = 0; kills < 12 && fight.progress().getLevel() == 1; kills++) {
            killOne(fight);
        }

        assertTrue(fight.progress().getLevel() > 1,
                "enough kills should have levelled him, at "
                        + experienceOf(fight.game()) + " experience");
    }

    @Test
    void aLevelRaisesTheHealthCeilingAndLiftsHealthWithIt() {
        var fight = start(BRISK);
        var body = hero(fight.game()).getBody();
        float ceilingBefore = body.getMaxHealth();
        float healthBefore = body.getHealth();

        killOne(fight);

        int level = fight.progress().getLevel();
        assertTrue(level > 1, "one kill should level him under these rules");
        int gained = BRISK.levelling().bonusHealth(level);
        assertEquals(ceilingBefore + gained, body.getMaxHealth(), 0.01f,
                "the ceiling should rise by what the file says");
        assertTrue(body.getHealth() > healthBefore - gained,
                "and current health should have been lifted with it, not left behind");
    }

    /**
     * A stand-up fight at a fixed distance: both are placed just inside reach, so
     * neither walks and the only thing being measured is how hard the hero hits.
     * Returns the frames taken to put the skeleton down.
     */
    private static int standUpFight(Fight fight) {
        var game = fight.game();
        var hero = hero(game);
        hero.setPosition(new Coord3D(200f, 150f, 0f));
        // Twelve apart, which is exactly the closing distance for two bodies of
        // radius four — so neither takes a step and walking cannot skew the clock.
        game.spawn("Skeleton", fight.arena().dungeon(), 212f, 150f);
        game.runHeadless(1);
        var skeleton = creature(game, "Skeleton");
        assertNotNull(skeleton);
        var skeletonId = skeleton.getId();

        game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                List.of(hero.getId()), skeletonId));

        int frames = 0;
        while (game.getLogic().findObject(skeletonId) != null && frames < 2000) {
            game.runHeadless(1);
            frames++;
        }
        assertTrue(frames < 2000, "the hero should have finished the skeleton off");
        assertNotNull(hero(game), "and survived doing it");
        return frames;
    }

    /** Measured on the clock: a levelled hero puts a skeleton down sooner. */
    @Test
    void aLevelledHeroKillsFaster() {
        var fight = start(BRISK);
        int asANovice = standUpFight(fight);
        assertTrue(fight.progress().getLevel() > 1, "that kill should have levelled him");

        int afterLevelling = standUpFight(fight);

        assertTrue(afterLevelling < asANovice,
                "a stronger hero should kill faster, but took " + afterLevelling
                        + " frames against " + asANovice);
    }

    /** And armour is real: the same blow costs him less. */
    @Test
    void aLevelledHeroTakesLessDamage() {
        var fight = start(BRISK);
        killOne(fight);
        int level = fight.progress().getLevel();
        assertTrue(level > 1);

        var body = hero(fight.game()).getBody();
        float before = body.getHealth();
        float blow = 40f;
        body.damage(blow);
        float taken = before - body.getHealth();

        assertTrue(taken < blow, "armour should absorb some of a " + blow + " hit");
        assertEquals(blow * BRISK.levelling().damageTakenMultiplier(level), taken, 0.01f,
                "and absorb exactly what his level is worth");
    }

    /** Retuning the file retunes the hero, with nothing recompiled. */
    @Test
    void changingTheFileChangesHowFastHeLevels() {
        var shipped = start(SETTINGS);
        var brisk = start(BRISK);

        killOne(shipped);
        killOne(brisk);

        assertEquals(1, shipped.progress().getLevel(),
                "one kill is not a level under the shipped rules");
        assertTrue(brisk.progress().getLevel() > 1,
                "but it is under these — same code, different file");
    }

    /** Roguelike: dying takes the levels with it. */
    @Test
    void aNewRunStartsBackAtTheFirstLevel() {
        var session = Dungeon.newSession(4242L, BRISK);
        var game = session.game();
        game.runHeadless(1);

        for (int i = 0; i < 40 && session.progress().getLevel() == 1; i++) {
            var skeleton = creature(game, "Skeleton");
            if (skeleton != null && hero(game) != null) {
                game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                        List.of(hero(game).getId()), skeleton.getId()));
            }
            game.runHeadless(60);
            if (hero(game) == null) {
                break; // he died trying; the reset is what matters either way
            }
        }

        var beforeDeath = hero(game);
        if (beforeDeath != null) {
            game.getLogic().destroyObject(beforeDeath);
        }
        game.runHeadless(BRISK.respawnDelayFrames() + 5);

        assertNotNull(hero(game), "a new run should have begun");
        assertEquals(1, session.progress().getLevel(), "and it starts from nothing");
        assertEquals(0, experienceOf(game), "with no experience carried over");
    }
}
