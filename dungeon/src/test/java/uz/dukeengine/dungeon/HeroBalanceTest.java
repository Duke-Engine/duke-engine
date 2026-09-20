package uz.dukeengine.dungeon;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.GameConstants;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.content.Content;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.level.HeroFigures;
import uz.dukeengine.dungeon.level.HeroProgress;
import uz.dukeengine.dungeon.loot.LootBag;
import uz.dukeengine.game.DukeGame;
import uz.dukeengine.game.GamePlayer;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.rts.module.ExperienceModule;

/**
 * The three heroes in a plain fight, on the clock.
 *
 * <p>Not the balance itself — that is tuned by playing — but its shape, held still.
 * Three skeletons that will not fall stand round him and he lasts as long as his body,
 * his armour and his mending let him; one ordinary skeleton is put down as fast as his
 * blow allows. At his first level and at his last.
 *
 * <p>What the same fights measured before the attributes, for whoever tunes this next
 * (lasting against three, at levels 1 and 15): the knight 56 s and 149 s, the archer 26 s
 * and 98 s, the mage 18 s and 78 s. After: 64 and 231, 28 and 118, 19 and 78 — the first
 * level moved only by the health that now comes back on its own, and strength growing
 * fastest on the one hero whose primary it is.
 */
class HeroBalanceTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int SECOND = GameConstants.LOGICFRAMES_PER_SECOND;
    private static final int CAP = 1200 * SECOND;
    private static final List<String> HEROES = List.of("Knight", "Rogue", "Mage");

    /** Skeletons that will not fall, so the clock measures him and not them. */
    private static final String STUBBORN = Content.units()
            .replace("    MaxHealth = 60\n", "    MaxHealth = 1000000\n");

    // ---- the shape ----

    /** The knight lasts, the archer less, the mage least — and by a margin, not a hair. */
    @Test
    void theKnightLastsLongestTheArcherLessAndTheMageLeast() {
        for (int level : new int[] {1, 15}) {
            float knight = lasts("Knight", level);
            float archer = lasts("Rogue", level);
            float mage = lasts("Mage", level);
            assertTrue(knight > archer * 1.5f, "at level " + level + " the knight lasts " + knight
                    + "s and the archer " + archer + "s: plate is not worth walking in for");
            assertTrue(archer > mage * 1.2f, "at level " + level + " the archer lasts " + archer
                    + "s and the mage " + mage + "s: nothing tells the two frail ones apart");
        }
    }

    /**
     * At the first level, long enough to do something and short enough that a mistake
     * is the run.
     */
    @Test
    void atTheFirstLevelNobodyIsOverAtOnceAndNobodyCannotLose() {
        within("Mage", 12f, 30f);
        within("Rogue", 18f, 45f);
        within("Knight", 40f, 100f);
    }

    /** Levels are worth taking: every one of them lasts far longer and kills sooner. */
    @Test
    void levelsMakeEveryHeroLastLongerAndKillSooner() {
        for (var hero : HEROES) {
            float first = lasts(hero, 1);
            float last = lasts(hero, 15);
            assertTrue(last > first * 2f, hero + " lasts " + first + "s at level one and only "
                    + last + "s at fifteen");
            assertTrue(killsOneIn(hero, 15) < killsOneIn(hero, 1),
                    hero + " kills no sooner for fifteen levels");
        }
    }

    /** Every one of them can put an ordinary skeleton down on his own. */
    @Test
    void everyHeroCanPutASkeletonDown() {
        for (var hero : HEROES) {
            float seconds = killsOneIn(hero, 1);
            assertTrue(seconds > 0f && seconds <= 8f,
                    hero + " takes " + seconds + "s over a single skeleton");
        }
    }

    /** The archer is quick, the knight slow, and levels do not turn that round. */
    @Test
    void theArcherIsQuickestAndTheKnightSlowest() {
        for (int level : new int[] {1, 15}) {
            float knight = speed("Knight", level);
            float archer = speed("Rogue", level);
            float mage = speed("Mage", level);
            assertTrue(archer > mage && mage > knight, "at level " + level + ": archer " + archer
                    + ", mage " + mage + ", knight " + knight);
        }
    }

    // ---- the fights ----

    private record Fight(DukeGame game, HeroProgress progress, GamePlayer dungeon,
            String template) {

        GameObject hero() {
            return find(game, template);
        }
    }

    /** Seconds he stands against three skeletons that will not fall. */
    private static float lasts(String template, int level) {
        var fight = fight(template, level, STUBBORN);
        for (int i = 0; i < 3; i++) {
            double angle = 2 * StrictMath.PI * i / 3;
            fight.game().spawn("Skeleton", fight.dungeon(), 200f + (float) (11 * StrictMath.cos(angle)),
                    150f + (float) (11 * StrictMath.sin(angle)));
        }
        int frames = 0;
        while (frames < CAP && fight.hero() != null) {
            fight.game().runHeadless(1);
            frames++;
        }
        return frames / (float) SECOND;
    }

    /** Seconds he takes to put one ordinary skeleton down, sent at it from across the room. */
    private static float killsOneIn(String template, int level) {
        var fight = fight(template, level, Content.units());
        var game = fight.game();
        game.spawn("Skeleton", fight.dungeon(), 270f, 150f);
        game.runHeadless(1);
        var skeleton = find(game, "Skeleton");
        assertNotNull(skeleton, "the skeleton should still be standing when he is sent at it");
        game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                List.of(fight.hero().getId()), skeleton.getId()));
        int frames = 0;
        while (frames < 60 * SECOND && find(game, "Skeleton") != null) {
            game.runHeadless(1);
            frames++;
        }
        return frames / (float) SECOND;
    }

    private static float speed(String template, int level) {
        var fight = fight(template, level, Content.units());
        return fight.progress().figuresOf(fight.hero(), HeroFigures.Found.NOTHING).speed();
    }

    /** Him, alone in a room at this level, with his progress ticking as a run's does. */
    private static Fight fight(String template, int level, String creatures) {
        var bag = new LootBag();
        var arena = Dungeon.world(room(40, 30), SETTINGS, creatures, bag);
        var game = arena.game();
        var progress = new HeroProgress(arena.hero(), SETTINGS.levelling(),
                SETTINGS.attributeRules(), SETTINGS.progression().levelUpBannerFrames(), bag);
        progress.playing(SETTINGS.heroNamed(template));
        game.onTick(progress::tick);
        game.spawn(template, arena.hero(), 200f, 150f);
        game.runHeadless(1);
        var fight = new Fight(game, progress, arena.dungeon(), template);
        assertNotNull(fight.hero(), template + " was not spawned");
        if (level > 1) {
            fight.hero().findModule(ExperienceModule.class)
                    .addExperience(SETTINGS.levelling().totalXpFor(level));
            game.runHeadless(1);
        }
        return fight;
    }

    private static void within(String hero, float least, float most) {
        float seconds = lasts(hero, 1);
        assertTrue(seconds >= least && seconds <= most, hero + " lasts " + seconds
                + "s against three at the first level, outside " + least + " to " + most);
    }

    private static GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static String room(int width, int height) {
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
}
