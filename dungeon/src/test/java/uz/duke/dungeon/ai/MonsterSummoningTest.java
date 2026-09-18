package uz.duke.dungeon.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.combat.DepthBonus;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.skill.Skill;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.dungeon.skill.Summoned;
import uz.duke.dungeon.skill.Summoning;
import uz.duke.game.DukeGame;
import uz.duke.rts.module.ExperienceModule;

/**
 * A monster that calls up more of them: how many, where, for how long, what they are
 * worth, and never past its ceiling.
 *
 * <p>Fought through the seam the dungeon is built on, with a hero standing inside the
 * summoner's band who picks no fights -- so what it calls up walks over and hits a man
 * who does not answer, and lives out its time.
 */
class MonsterSummoningTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final String SUMMONER = "SkeletonSummoner";
    private static final float CELL = 10f;
    private static final float ROW = 205f;

    /** An open room 60 cells by 40. */
    private static String room() {
        return map((x, y) -> x > 0 && y > 0 && x < 59 && y < 39);
    }

    /** A corridor one cell wide along row 20, and stone everywhere else. */
    private static String corridor() {
        return map((x, y) -> y == 20 && x > 0 && x < 59);
    }

    /** One floor cell, at 10 across and 20 down, in solid rock. */
    private static String pocket() {
        return map((x, y) -> x == 10 && y == 20);
    }

    private interface Floor {
        boolean at(int x, int y);
    }

    private static String map(Floor floor) {
        var text = new StringBuilder();
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 60; x++) {
                text.append(floor.at(x, y) ? '.' : '#');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static float at(int cell) {
        return (cell + 0.5f) * CELL;
    }

    private record Circle(DukeGame game, GameObject summoner) {
    }

    /** A hero standing in the summoner's band, told to pick no fights, and the summoner. */
    private static Circle circle(DungeonSettings settings, String map, float heroX, float summonerX) {
        var arena = Dungeon.world(map, settings);
        var game = arena.game();
        game.spawn("Rogue", arena.hero(), heroX, ROW);
        game.spawn(SUMMONER, arena.dungeon(), summonerX, ROW);
        game.runHeadless(1);
        // A player has an index once the game has started, and not before.
        arena.orders().hold(arena.hero().getIndex(), true);
        return new Circle(game, first(game, SUMMONER));
    }

    private static Circle circle(DungeonSettings settings) {
        return circle(settings, room(), 150f, 200f);
    }

    private static GameObject first(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().name().equals(template))
                .findFirst().orElse(null);
    }

    private static Skill summoning() {
        return SETTINGS.skillsFor(SUMMONER).get(0);
    }

    /** The shipped summoning with its numbers changed, said whole because a block replaces a skill. */
    private static DungeonSettings summoningWith(int count, int most, int lasts, int percent,
            int cooldown) {
        var skill = summoning();
        return DungeonSettings.parse("""
                DungeonSkill SkeletonSummoner Q
                  Effect = SUMMON
                  Summons = %s
                  SummonCount = %d
                  MaxSummoned = %d
                  Radius = %s
                  DurationFrames = %d
                  SummonExperiencePercent = %d
                  WindUpFrames = %d
                  Projectile = %s
                  CooldownFrames = %d
                  MaxRank = 1
                End
                """.formatted(skill.summons(), count, most, skill.radius(), lasts, percent,
                skill.windUpFrames(), skill.projectile(), cooldown));
    }

    /** Long enough for it to finish a throw it had started and for its rifts to land. */
    private static int oneSummoning() {
        return SETTINGS.monster(SUMMONER).swingFrames() + summoning().windUpFrames() + 10;
    }

    /** One thing it called up: which, on what frame it rose, and where. */
    private record Rising(int id, int frame, Coord3D at) {
    }

    /** Everything called up over the next frames, in the order it rose. */
    private static List<Rising> risings(DukeGame game, int frames) {
        var seen = new HashSet<Integer>();
        var found = new ArrayList<Rising>();
        for (int frame = 0; frame < frames; frame++) {
            game.runHeadless(1);
            for (var object : game.getLogic().getObjects()) {
                if (object.findModule(Summoned.class) != null && seen.add(object.getId().value())) {
                    found.add(new Rising(object.getId().value(), game.getLogic().getFrame(),
                            object.getPosition()));
                }
            }
        }
        return found;
    }

    /** What it called up that is standing now. */
    private static long standing(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.findModule(Summoned.class) != null
                        && !object.isDestroyed() && !object.isEffectivelyDead())
                .count();
    }

    // ---- how many, and where ----

    @Test
    void itCallsUpItsCountRoundItselfTowardHimFirst() {
        var circle = circle(SETTINGS);
        var from = circle.summoner().getPosition();

        var risen = risings(circle.game(), oneSummoning());

        assertEquals(summoning().summonCount(), risen.size(), "one casting: " + risen);
        for (var one : risen) {
            assertEquals(summoning().summons(),
                    circle.game().getLogic().findObject(new uz.duke.core.thing.ObjectId(one.id()))
                            .getTemplate().name());
            float away = (float) Math.hypot(one.at().x() - from.x(), one.at().y() - from.y());
            assertEquals(summoning().radius(), away, 0.5f, "each rises its Radius from it");
        }
        assertTrue(risen.get(0).at().x() < from.x(), "and the first on his side of it: " + risen);
    }

    /** How many a cast calls up is the file's to say. */
    @Test
    void howManyRiseIsTheFiles() {
        var three = circle(summoningWith(3, 6, 600, 0, 3000));

        assertEquals(3, risings(three.game(), oneSummoning()).size());
    }

    /** Quick to cast and slow to fall down, so the only thing that stops it is its ceiling. */
    @Test
    void itNeverHasMoreStandingThanItsCeiling() {
        var circle = circle(summoningWith(2, 4, 3000, 0, 60));
        long most = 0;

        for (int frame = 0; frame < 300; frame++) {
            circle.game().runHeadless(1);
            long now = standing(circle.game());
            assertTrue(now <= 4, now + " standing on frame " + frame + ", over a ceiling of 4");
            most = Math.max(most, now);
        }

        assertEquals(4, most, "it should have reached its ceiling");
        var book = circle.summoner().findModule(SkillBook.class);
        assertEquals(4, book.summonedStanding());
        assertTrue(book.isReady(summoning().key()), "and a cast it could not make spent no cooldown");
    }

    /** In a corridor one cell wide most of the circle round it is rock, and none rise in it. */
    @Test
    void nothingRisesInStoneOrOutOfItsSight() {
        var circle = circle(summoningWith(4, 4, 3000, 0, 3000), corridor(), at(14), at(10));
        var from = circle.summoner().getPosition();
        var world = circle.game().getLogic();

        var risen = risings(circle.game(), oneSummoning());

        assertEquals(2, risen.size(), "the corridor has room ahead of it and behind it, and no more: "
                + risen);
        for (var one : risen) {
            assertFalse(world.isGroundBlocked(one.at()), "one rose in stone at " + one.at());
            assertTrue(SightLine.clear(world, from, one.at()), "one rose out of its sight at " + one.at());
        }
    }

    /** Shut in rock there is nowhere to open a rift at all. */
    @Test
    void shutInRockThereIsNowhereToCallThemUp() {
        var arena = Dungeon.world(pocket(), SETTINGS);
        var game = arena.game();
        game.spawn(SUMMONER, arena.dungeon(), at(10), at(20));
        game.runHeadless(1);

        var spots = Summoning.spots(game.getLogic(), first(game, SUMMONER), null,
                summoning().radius(), 4, 8f, SETTINGS.summonTurnDegrees(), SETTINGS.summonTurns());

        assertTrue(spots.isEmpty(), "spots found in solid rock: " + spots);
    }

    // ---- for how long, and what they are worth ----

    /** The rift lies a moment, and what it holds rises when it lands. */
    @Test
    void theyRiseWhenTheRiftLandsAndNotBefore() {
        var circle = circle(SETTINGS);
        var rift = summoning().projectile();
        int opened = -1;
        int rose = -1;
        for (int frame = 0; frame < oneSummoning() + 10 && rose < 0; frame++) {
            boolean lying = circle.game().getLogic().getObjects().stream().anyMatch(object ->
                    object.getTemplate().name().equals(rift) && !object.isDestroyed());
            if (lying && opened < 0) {
                opened = circle.game().getLogic().getFrame();
            }
            if (standing(circle.game()) > 0) {
                rose = circle.game().getLogic().getFrame();
            }
            circle.game().runHeadless(1);
        }

        assertTrue(opened >= 0 && rose > opened, "opened on " + opened + ", rose on " + rose);
        assertTrue(Math.abs(rose - opened - summoning().windUpFrames()) <= 1,
                "the rift lay " + (rose - opened) + " frames against a wind-up of "
                        + summoning().windUpFrames());
    }

    @Test
    void whatItCallsUpFallsDownWhenItsTimeIsOut() {
        int lasts = 90;
        var circle = circle(summoningWith(2, 4, lasts, 0, 3000));
        var risen = risings(circle.game(), oneSummoning());
        assertEquals(2, risen.size());
        var world = circle.game().getLogic();
        int rose = risen.get(0).frame();

        circle.game().runHeadless(rose + lasts - 3 - world.getFrame());
        for (var one : risen) {
            var it = world.findObject(new uz.duke.core.thing.ObjectId(one.id()));
            assertTrue(it != null && !it.isEffectivelyDead(), "still standing a moment before its time");
        }
        circle.game().runHeadless(6);
        for (var one : risen) {
            var it = world.findObject(new uz.duke.core.thing.ObjectId(one.id()));
            assertTrue(it == null || it.isEffectivelyDead(), "and down once its " + lasts + " frames are up");
        }
    }

    /** A share of what its own kind is worth: nothing as shipped, and half when the file says half. */
    @Test
    void whatItCallsUpIsWorthTheShareTheFileSays() {
        for (int percent : new int[] {summoning().summonExperiencePercent(), 50}) {
            var arena = Dungeon.world(room(), summoningWith(2, 4, 3000, percent, 3000));
            var game = arena.game();
            game.spawn("Rogue", arena.hero(), 150f, ROW);
            game.spawn(SUMMONER, arena.dungeon(), 200f, ROW);
            // One placed on the floor rather than called up, far off in a corner: what its
            // own kind is worth.
            game.spawn(summoning().summons(), arena.dungeon(), at(55), at(35));
            game.runHeadless(1);
            arena.orders().hold(arena.hero().getIndex(), true);
            int whole = first(game, summoning().summons())
                    .findModule(ExperienceModule.class).getExperienceValue();

            var risen = risings(game, oneSummoning());

            assertEquals(2, risen.size());
            for (var one : risen) {
                var it = game.getLogic().findObject(new uz.duke.core.thing.ObjectId(one.id()));
                assertEquals(whole * percent / 100,
                        it.findModule(ExperienceModule.class).getExperienceValue(),
                        "at " + percent + "% of " + whole);
            }
        }
    }

    /** Found as deep as what called it up, so it hits as hard as one placed there would. */
    @Test
    void whatItCallsUpWasFoundAsDeepAsItsCaller() {
        var circle = circle(summoningWith(2, 4, 3000, 0, 60));
        var before = new HashSet<Integer>();
        risings(circle.game(), oneSummoning()).forEach(one -> before.add(one.id()));
        assertEquals(2, before.size(), "the first casting, before it was found any deeper");
        circle.summoner().addModule(new DepthBonus(circle.summoner(), 1.6f, 1.9f));

        var after = risings(circle.game(), 60 + oneSummoning()).stream()
                .filter(one -> !before.contains(one.id())).toList();

        assertEquals(2, after.size(), "a second casting, once it was: " + after);
        var world = circle.game().getLogic();
        for (var one : after) {
            var bonus = world.findObject(new uz.duke.core.thing.ObjectId(one.id()))
                    .findModule(DepthBonus.class);
            assertTrue(bonus != null, "called up without its caller's depth");
            assertEquals(1.6f, bonus.damageMultiplier(), 0.001f);
            assertEquals(1.9f, bonus.healthMultiplier(), 0.001f);
        }
        for (int id : before) {
            assertTrue(world.findObject(new uz.duke.core.thing.ObjectId(id))
                    .findModule(DepthBonus.class) == null,
                    "the first two were called up before the depth was on it");
        }
    }

    // ---- the same every time ----

    @Test
    void theSameSummoningPlaysOutTheSameEveryTime() {
        assertEquals(playedOut(), playedOut());
    }

    private static String playedOut() {
        var circle = circle(summoningWith(2, 4, 200, 0, 120));
        var line = new StringBuilder();
        for (var one : risings(circle.game(), 240)) {
            line.append(one.frame()).append('@').append(one.at()).append(';');
        }
        for (int i = 0; i < 8; i++) {
            circle.game().runHeadless(30);
            line.append(circle.game().getLogic().checksum()).append('|');
        }
        return line.toString();
    }
}
