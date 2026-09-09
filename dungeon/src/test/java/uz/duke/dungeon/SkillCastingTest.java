package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.skill.CastSkill;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.game.DukeGame;

/**
 * Casting: what each key does, what stops it, and how it grows.
 *
 * <p>Fought in the game's own arena with the game's own creatures, so a skill is
 * tested against the hero the player actually has. Nothing here asserts a balance
 * figure — the numbers come from the file, and a re-tune moves the test with them
 * rather than breaking it. What is held still is the mechanism: that a cooldown
 * refuses, that an ultimate waits for its level, that a level makes a skill
 * stronger, that the file decides all three.
 */
class SkillCastingTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static String arena() {
        int width = 40;
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

    private record Arena(DukeGame game, GameObject hero, SkillBook book) {
    }

    /** The hero alone in a room, plus however many skeletons the test wants. */
    private static Arena arena(DungeonSettings settings, float... skeletonXy) {
        return arena(settings, Content.read(Content.CREATURES), skeletonXy);
    }

    private static Arena arena(DungeonSettings settings, String creaturesIni, float... skeletonXy) {
        var world = Dungeon.world(arena(), settings, creaturesIni);
        var game = world.game();
        game.spawn("Hero", world.hero(), 150f, 150f);
        for (int i = 0; i + 1 < skeletonXy.length; i += 2) {
            game.spawn("Skeleton", world.dungeon(), skeletonXy[i], skeletonXy[i + 1]);
        }
        game.runHeadless(1);
        var hero = creature(game, "Hero");
        return new Arena(game, hero, hero.findModule(SkillBook.class));
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals(template))
                .findFirst().orElse(null);
    }

    private static long livingSkeletons(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Skeleton"))
                .filter(object -> !object.isEffectivelyDead())
                .count();
    }

    /** The hero has a skill book at all, built from the file rather than from Java. */
    @Test
    void theHeroCarriesTheSkillsTheFileGaveHim() {
        var arena = arena(SETTINGS);

        assertNotNull(arena.book(), "his creature block asks for a SkillBook");
        assertEquals(SETTINGS.skillsFor("Hero"), arena.book().getSkills());
    }

    // ---- what each key does ----

    /** Q hurts one thing. */
    @Test
    void theStrikeWoundsTheNearestEnemy() {
        var arena = arena(SETTINGS, 170f, 150f);
        var skeleton = creature(arena.game(), "Skeleton");
        float before = skeleton.getBody().getHealth();

        assertTrue(arena.book().cast('Q', 1));

        assertTrue(skeleton.getBody().getHealth() < before,
                "it should have been hit, and was still on " + skeleton.getBody().getHealth());
    }

    /** And picks the nearest, not whichever the world happens to list first. */
    @Test
    void theStrikeChoosesTheNearest() {
        var arena = arena(SETTINGS, 180f, 150f, 160f, 150f);
        var far = arena.game().getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Skeleton"))
                .filter(object -> object.getPosition().x() > 170f)
                .findFirst().orElseThrow();
        float farBefore = far.getBody().getHealth();

        arena.book().cast('Q', 1);

        assertEquals(farBefore, far.getBody().getHealth(), 0.01f,
                "the further skeleton should not have been touched");
    }

    /** W hurts everything close. */
    @Test
    void theAreaSkillWoundsEveryoneAround() {
        var arena = arena(SETTINGS, 170f, 150f, 150f, 170f, 130f, 150f);
        var healths = arena.game().getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Skeleton"))
                .map(object -> object.getBody().getHealth())
                .toList();

        assertTrue(arena.book().cast('W', 1));

        var after = arena.game().getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Skeleton"))
                .map(object -> object.getBody().getHealth())
                .toList();
        for (int i = 0; i < healths.size(); i++) {
            assertTrue(after.get(i) < healths.get(i), "skeleton " + i + " was spared");
        }
    }

    /** E moves him, and moves him a long way. */
    @Test
    void theDashCarriesHimForward() {
        var arena = arena(SETTINGS);
        var from = arena.hero().getPosition();

        assertTrue(arena.book().cast('E', 1));

        float travelled = arena.hero().getPosition().distance(from);
        assertTrue(travelled > 50f, "he barely moved: " + travelled);
    }

    /**
     * And never into stone. A dash that simply added its distance would put his
     * centre inside a wall whenever the room was too small for it, and a unit
     * standing in stone is a bug that costs a day.
     */
    @Test
    void theDashStopsAtTheWall() {
        var world = Dungeon.world(arena(), SETTINGS);
        var game = world.game();
        // Facing the eastern wall from two cells away, with a 90-unit dash.
        game.spawn("Hero", world.hero(), 370f, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Hero");

        var from = hero.getPosition();
        hero.findModule(SkillBook.class).cast('E', 1);

        assertFalse(game.getLogic().isGroundBlocked(hero.getPosition()),
                "he landed in the wall at " + hero.getPosition());
        assertTrue(hero.getPosition().distance(from) > 0f,
                "and he did move — a dash that goes nowhere passes this trivially");
        assertTrue(hero.getPosition().distance(from) < 90f,
                "but not the whole way, because the wall was in it");
    }

    /** R makes him hit harder while it lasts. */
    @Test
    void theUltimateRaisesHisDamage() {
        var arena = arena(SETTINGS);
        float plain = arena.book().damageMultiplier();

        assertTrue(arena.book().cast('R', 5), "at the level that unlocks it");

        assertTrue(arena.book().damageMultiplier() > plain,
                "it should be worth something: " + arena.book().damageMultiplier());
    }

    /** And only while it lasts. */
    @Test
    void theUltimateWearsOff() {
        var arena = arena(SETTINGS);
        var r = SETTINGS.skillsFor("Hero").stream()
                .filter(skill -> skill.key() == 'R').findFirst().orElseThrow();
        arena.book().cast('R', 5);

        arena.game().runHeadless(r.durationFrames() + 2);

        assertEquals(1f, arena.book().damageMultiplier(), 0.0001f,
                "the window should have closed");
    }

    // ---- cooldown ----

    /** A skill refuses until its cooldown has run, then works again. */
    @Test
    void aSkillRefusesUntilItsCooldownHasRun() {
        var arena = arena(SETTINGS, 170f, 150f);
        var q = SETTINGS.skillsFor("Hero").stream()
                .filter(skill -> skill.key() == 'Q').findFirst().orElseThrow();

        assertTrue(arena.book().cast('Q', 1), "the first cast");
        assertFalse(arena.book().cast('Q', 1), "and not the second, one frame later");

        arena.game().runHeadless(q.cooldownAt(1) - 1);
        assertFalse(arena.book().cast('Q', 1), "still one frame short");

        arena.game().runHeadless(1);
        assertTrue(arena.book().cast('Q', 1), "and now ready");
    }

    /** It is counted in frames, so it is the same cooldown on every machine. */
    @Test
    void theCooldownIsCountedInFrames() {
        var arena = arena(SETTINGS);
        var q = SETTINGS.skillsFor("Hero").stream()
                .filter(skill -> skill.key() == 'Q').findFirst().orElseThrow();

        arena.book().cast('Q', 1);

        assertEquals(q.cooldownAt(1), arena.book().cooldownOf('Q'));
        arena.game().runHeadless(10);
        assertEquals(q.cooldownAt(1) - 10, arena.book().cooldownOf('Q'));
    }

    // ---- levels ----

    /** An ultimate is refused below its level and granted at it. */
    @Test
    void theUltimateWaitsForItsLevel() {
        var arena = arena(SETTINGS);
        var r = SETTINGS.skillsFor("Hero").stream()
                .filter(skill -> skill.key() == 'R').findFirst().orElseThrow();

        assertFalse(arena.book().cast('R', r.unlockLevel() - 1), "one level short");
        assertEquals(0, arena.book().cooldownOf('R'),
                "and a refused cast must not start the cooldown");
        assertTrue(arena.book().cast('R', r.unlockLevel()));
    }

    /** A levelled hero's skills hit harder, by the file's own step. */
    @Test
    void aLevelledHeroStrikesHarder() {
        float atOne = damageDealtByStrike(1);
        float atSeven = damageDealtByStrike(7);

        assertTrue(atSeven > atOne, atOne + " at level 1 but only " + atSeven + " at 7");
    }

    /** How much health one Q takes off a fresh skeleton, cast by a hero of {@code level}. */
    private static float damageDealtByStrike(int level) {
        var arena = arena(SETTINGS, 170f, 150f);
        var skeleton = creature(arena.game(), "Skeleton");
        float before = skeleton.getBody().getHealth();
        arena.book().cast('Q', level);
        return before - skeleton.getBody().getHealth();
    }

    // ---- the file decides ----

    /** Re-tune one skill in the file and that skill changes; the others do not. */
    @Test
    void changingTheFileChangesTheSkill() {
        var fierce = DungeonSettings.parse("""
                DungeonSkill Hero Q
                  Effect = AREA_DAMAGE
                  Damage = 500
                  Radius = 200
                  CooldownFrames = 5
                End
                """);
        var arena = arena(fierce, 170f, 150f, 150f, 190f);

        assertTrue(arena.book().cast('Q', 1));

        assertEquals(0, livingSkeletons(arena.game()),
                "Q was rewritten as a huge area blast; both should be gone");
        assertEquals(4, arena.book().getSkills().size(),
                "and naming one skill must not delete the other three");
    }

    /**
     * A second hero, described only in data, has his own skills and works.
     *
     * <p>The point of the whole shape: effects are code, heroes are file. Adding a
     * hero must never mean adding a class, or the fifth one never gets written.
     */
    @Test
    void aSecondHeroDescribedOnlyInDataWorks() {
        var withTwo = DungeonSettings.parse("""
                DungeonSkill Rogue A
                  Effect = DASH
                  Distance = 120
                  CooldownFrames = 60
                End
                DungeonSkill Rogue S
                  Effect = STRIKE
                  Damage = 500
                  Range = 60
                  CooldownFrames = 30
                End
                """);
        var creatures = Content.read(Content.CREATURES)
                .replace("Object Hero", "Object Rogue")
                .replace("DisplayName = Hero", "DisplayName = Rogue");

        var world = Dungeon.world(arena(), withTwo, creatures);
        var game = world.game();
        game.spawn("Rogue", world.hero(), 150f, 150f);
        game.spawn("Skeleton", world.dungeon(), 180f, 150f);
        game.runHeadless(1);
        var rogue = creature(game, "Rogue");
        var book = rogue.findModule(SkillBook.class);

        assertNotNull(book, "he asked for a SkillBook in the same way");
        assertEquals(2, book.getSkills().size(), "and got the two the file gave him");
        assertTrue(book.cast('S', 1), "on his own key, which the hero does not have");
        assertEquals(0, livingSkeletons(game));
        assertFalse(book.cast('Q', 1), "and not on a key that is not his");
    }

    // ---- through the command pipeline ----

    /**
     * A key press really does reach the skill, as a command, through the queue.
     *
     * <p>The rest of this file calls the book directly, which is the right way to
     * ask what a skill does. This one asks the other question: that pressing E in
     * the real game arrives — posted from outside, applied inside a frame, by the
     * simulation.
     */
    @Test
    void castingArrivesAsACommand() {
        var session = Dungeon.newSession(11L, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        var hero = creature(game, "Hero");
        var from = hero.getPosition();

        game.postCommand(new CastSkill(game.getLocalPlayerIndex(), 'E'));
        game.runHeadless(3);

        assertTrue(hero.getPosition().distance(from) > 20f,
                "the dash never happened; he is at " + hero.getPosition());
    }

    /** And a new run hands him a book with nothing on cooldown. */
    @Test
    void aNewRunGivesHimHisSkillsBack() {
        var session = Dungeon.newSession(11L, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        var book = creature(game, "Hero").findModule(SkillBook.class);
        book.cast('Q', 1);
        assertTrue(book.cooldownOf('Q') > 0);

        // Kill him, and wait out the pause before the next dungeon.
        creature(game, "Hero").getBody().damage(100000f);
        game.runHeadless(SETTINGS.respawnDelayFrames() + 10);

        var next = creature(game, "Hero").findModule(SkillBook.class);
        assertNotEquals(book, next, "a new run means a new hero");
        assertEquals(0, next.cooldownOf('Q'), "and nothing left over from the last one");
    }

    // ---- determinism ----

    /** The same casts on the same seed give the same world, to the bit. */
    @Test
    void theSameCastsGiveTheSameWorld() {
        assertEquals(playedOut(true), playedOut(true));
        assertNotEquals(playedOut(true), playedOut(false), "and the casting mattered");
    }

    private static long playedOut(boolean cast) {
        var session = Dungeon.newSession(11L, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        for (var key : new char[] {'Q', 'W', 'E', 'Q', 'W'}) {
            if (cast) {
                game.postCommand(new CastSkill(game.getLocalPlayerIndex(), key));
            }
            game.runHeadless(40);
        }
        return game.getLogic().checksum();
    }
}
