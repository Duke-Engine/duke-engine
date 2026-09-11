package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectId;
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

    /**
     * The shipped creatures with the hero's bow taken away.
     *
     * <p>His strike is no longer instant — he draws, looses, and the arrow crosses
     * the room — so a test about it has to let the world run, and a world that runs
     * is a world in which his ordinary bow is also shooting. Then "the skeleton
     * lost health" says nothing about the skill.
     *
     * <p>A weapon that reaches nothing acquires nothing, so with this file anything
     * that gets hurt was hurt by a skill. His skills are untouched: their range is
     * their own, in {@code dungeon.ini}.
     */
    private static String creaturesWithNoBow() {
        var lines = Content.read(Content.CREATURES).split("\n", -1);
        boolean inHero = false;
        var edited = new StringBuilder();
        for (var line : lines) {
            if (line.startsWith("Object ")) {
                inHero = line.trim().equals("Object Hero");
            }
            edited.append(inHero && line.trim().startsWith("AttackRange")
                    ? "    AttackRange = 0" : line).append('\n');
        }
        return edited.toString();
    }

    /** An arena where only his skills can hurt anything. */
    private static Arena skillsOnly(float... skeletonXy) {
        return arena(SETTINGS, creaturesWithNoBow(), skeletonXy);
    }

    /**
     * The hero and the boss, which is what to use when the test needs its target
     * to still be there afterwards — a skeleton dies to one heavy shot, and a
     * question about what he does next cannot be asked of an empty room.
     */
    private static Arena bossArena(String creaturesIni, float distance) {
        var world = Dungeon.world(arena(), SETTINGS, creaturesIni);
        var game = world.game();
        game.spawn("Hero", world.hero(), 150f, 150f);
        game.spawn(SETTINGS.bossKindAt(SETTINGS.finalDepth()), world.dungeon(),
                150f + distance, 150f);
        game.runHeadless(1);
        var hero = creature(game, "Hero");
        return new Arena(game, hero, hero.findModule(SkillBook.class));
    }

    /**
     * Cast, then run the world on until the shot has been drawn, loosed and landed.
     *
     * <p>What the older tests asked — "cast it, is it hurt?" — is now a question
     * about two moments rather than one.
     */
    private static boolean castAndWait(Arena arena, char key, int level, ObjectId at) {
        boolean cast = arena.book().cast(key, level, at, null);
        arena.game().runHeadless(untilItLands(key));
        return cast;
    }

    /** The skill's own wind-up, plus flight enough to cross its whole range. */
    private static int untilItLands(char key) {
        var skill = SETTINGS.skillsFor("Hero").stream()
                .filter(s -> s.key() == key).findFirst().orElseThrow();
        return skill.windUpFrames() + 60;
    }

    private static long arrowsInTheAir(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals(template))
                .count();
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
        var arena = skillsOnly(170f, 150f);
        var skeleton = creature(arena.game(), "Skeleton");
        float before = skeleton.getBody().getHealth();

        assertTrue(castAndWait(arena, 'Q', 1, null));

        assertTrue(skeleton.getBody().getHealth() < before,
                "it should have been hit, and was still on " + skeleton.getBody().getHealth());
    }

    /** And picks the nearest, not whichever the world happens to list first. */
    @Test
    void theStrikeChoosesTheNearest() {
        var arena = skillsOnly(180f, 150f, 160f, 150f);
        var far = arena.game().getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Skeleton"))
                .filter(object -> object.getPosition().x() > 170f)
                .findFirst().orElseThrow();
        float farBefore = far.getBody().getHealth();

        castAndWait(arena, 'Q', 1, null);

        assertEquals(farBefore, far.getBody().getHealth(), 0.01f,
                "the further skeleton should not have been touched");
    }

    // ---- the drawn shot ----

    /** The skill the file describes, by key. */
    private static uz.duke.dungeon.skill.Skill skillNamed(char key) {
        return SETTINGS.skillsFor("Hero").stream()
                .filter(skill -> skill.key() == key).findFirst().orElseThrow();
    }

    /**
     * He draws before he looses, and nothing happens while he is drawing.
     *
     * <p>The whole reason for the wind-up: an instant strike is a monster losing
     * health with nothing on screen to explain it. What the player has to be able
     * to see is the archer taking aim, and that is only visible if the damage
     * waits for him.
     */
    @Test
    void theStrikeIsDrawnBeforeItIsLoosed() {
        var q = skillNamed('Q');
        assertTrue(q.windUpFrames() > 1, "the shipped Q draws; this test is about that");
        var arena = skillsOnly(170f, 150f);
        var skeleton = creature(arena.game(), "Skeleton");
        float before = skeleton.getBody().getHealth();

        assertTrue(arena.book().cast('Q', 1));
        arena.game().runHeadless(q.windUpFrames() - 1);

        assertEquals(before, skeleton.getBody().getHealth(), 0.01f,
                "he is still drawing; nothing has left the bow");
        assertEquals(0, arrowsInTheAir(arena.game(), q.projectile()),
                "and nothing is in the air yet");
    }

    /**
     * And what he looses is a real arrow, which has to get there.
     *
     * <p>Same doctrine as his ordinary shot: the damage happens where and when the
     * arrow does. A skill that simply subtracted health at range would be the thing
     * the wind-up was added to stop being.
     */
    @Test
    void theDrawnShotIsAnArrowThatHasToArrive() {
        var q = skillNamed('Q');
        assertTrue(q.hasProjectile(), "the shipped Q looses something");
        var arena = skillsOnly(200f, 150f); // far enough that the flight is worth watching
        var skeleton = creature(arena.game(), "Skeleton");
        float before = skeleton.getBody().getHealth();

        arena.book().cast('Q', 1);
        arena.game().runHeadless(q.windUpFrames() + 1);

        assertEquals(1, arrowsInTheAir(arena.game(), q.projectile()),
                "it should have left the bow");
        assertEquals(before, skeleton.getBody().getHealth(), 0.01f, "and not arrived yet");

        arena.game().runHeadless(untilItLands('Q'));

        assertTrue(skeleton.getBody().getHealth() < before, "it should have got there");
        assertEquals(0, arrowsInTheAir(arena.game(), q.projectile()),
                "and been taken away once it had");
    }

    /**
     * One keypress, one arrow.
     *
     * <p>He loosed two: the skill points his weapon at the target so that he is
     * visibly taking aim, and his weapon — which updates before his skills do —
     * took that as an order and fired the ordinary shot on the spot. He has one
     * bow, so while he is drawing with it, it holds.
     *
     * <p>His real bow here, not the disarmed one the other strike tests use: the
     * whole question is what the bow does.
     */
    @Test
    void hisBowKeepsQuietWhileHeDraws() {
        var q = skillNamed('Q');
        // Inside his ordinary range as well as the skill's, which is the case that
        // was broken — out of the bow's reach it could not have fired anyway.
        var arena = bossArena(Content.read(Content.CREATURES), 50f);
        // He is standing in front of a skeleton, so of course he was already
        // shooting. What is being watched is whether a *new* arrow leaves, not
        // whether the air is empty.
        var already = ordinaryArrows(arena.game());

        assertTrue(arena.book().cast('Q', 1));
        for (int frame = 0; frame < q.windUpFrames(); frame++) {
            arena.game().runHeadless(1);
            assertTrue(already.containsAll(ordinaryArrows(arena.game())),
                    "he loosed an ordinary arrow while he was still drawing");
        }

        assertEquals(1, arrowsInTheAir(arena.game(), q.projectile()),
                "and the drawn one has gone");
    }

    /**
     * And picks his ordinary shooting straight back up once it has gone.
     *
     * <p>Held, not stopped: the reload ran underneath the draw, so he does not
     * start a fresh wait for having used a skill.
     */
    @Test
    void hisBowStartsAgainOnceTheShotHasGone() {
        var q = skillNamed('Q');
        var arena = bossArena(Content.read(Content.CREATURES), 50f);
        var already = ordinaryArrows(arena.game());

        arena.book().cast('Q', 1);
        // Watched frame by frame rather than looked at once at the end: an arrow
        // that has already arrived is not in the air to be counted, and this is a
        // question about whether one ever left.
        boolean shotAgain = false;
        int watch = q.windUpFrames() + heroReloadFrames() + 5;
        for (int frame = 0; frame < watch && !shotAgain; frame++) {
            arena.game().runHeadless(1);
            shotAgain = !already.containsAll(ordinaryArrows(arena.game()));
        }

        assertTrue(shotAgain, "he never went back to shooting");
    }

    /** Every ordinary arrow in the air right now, by id. */
    private static java.util.Set<Integer> ordinaryArrows(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(SETTINGS.arrowTemplate()))
                .map(o -> o.getId().value())
                .collect(java.util.stream.Collectors.toSet());
    }

    /** {@code ReloadFrames} from the hero's own block, rather than a copy of it. */
    private static int heroReloadFrames() {
        boolean inHero = false;
        for (var line : Content.read(Content.CREATURES).split("\n")) {
            var trimmed = line.trim();
            if (trimmed.startsWith("Object ")) {
                inHero = trimmed.equals("Object Hero");
            } else if (inHero && trimmed.startsWith("ReloadFrames")) {
                return Integer.parseInt(trimmed.substring(trimmed.indexOf('=') + 1).trim());
            }
        }
        throw new AssertionError("the hero has no ReloadFrames");
    }

    /**
     * The cooldown starts when he commits, not when the arrow lands.
     *
     * <p>Otherwise a shot across the room would be cheaper than one at his feet,
     * which is backwards — and a player would have no way of knowing when he could
     * cast again except by watching an arrow.
     */
    @Test
    void theCooldownStartsWhenHePressesTheKey() {
        var arena = skillsOnly(200f, 150f);

        assertTrue(arena.book().cast('Q', 1));

        assertEquals(skillNamed('Q').cooldownAt(1), arena.book().cooldownOf('Q'),
                "it should be recharging while he is still drawing");
    }

    // ---- the ultimate ----

    /**
     * The ultimate makes his skills hit harder, not only his bow.
     *
     * <p>Which is what {@code dungeon.ini} says it is for — "a window, during
     * which everything else he does is worth more" — and for a while it was not
     * true: the boost rode the engine's weapon seam, and a skill that damages a
     * body directly never passes through a weapon.
     *
     * <p>Fought against the boss because a skeleton dies to either version, and
     * two kills are not a comparison.
     */
    @Test
    void theUltimateMakesHisSkillsHitHarderToo() {
        int level = skillNamed('R').unlockLevel();

        assertTrue(damageFrom('Q', level, true) > damageFrom('Q', level, false),
                "the strike should have been worth more inside the window");
        assertTrue(damageFrom('W', level, true) > damageFrom('W', level, false),
                "and so should the burst");
    }

    /** What one cast takes off the boss, with or without the ultimate up first. */
    private static float damageFrom(char key, int level, boolean underTheUltimate) {
        var arena = bossArena(creaturesWithNoBow(), 20f);
        var game = arena.game();
        var book = arena.book();
        var boss = creature(game, SETTINGS.bossKindAt(SETTINGS.finalDepth()));

        if (underTheUltimate) {
            assertTrue(book.cast('R', level), "the ultimate should have gone up");
        }
        float before = boss.getBody().getHealth();
        book.cast(key, level);
        game.runHeadless(untilItLands(key));
        return before - boss.getBody().getHealth();
    }

    // ---- aiming ----

    /**
     * Pointed at one skeleton, it hits that one — not whichever happens to be
     * nearest when the frame comes round.
     *
     * <p>This is the whole point of aiming. A player who clicked the wounded one at
     * the back and hit the fresh one in front has been given a skill he cannot aim,
     * and has spent the cooldown he needed on the wrong creature.
     */
    @Test
    void aStrikeHitsTheOneItWasPointedAt() {
        var arena = skillsOnly(160f, 150f, 180f, 150f);
        var chosen = skeletonBeyond(arena.game(), 170f);
        var nearer = skeletonBeyond(arena.game(), 0f);
        float nearerBefore = nearer.getBody().getHealth();
        float chosenBefore = chosen.getBody().getHealth();

        assertTrue(castAndWait(arena, 'Q', 1, chosen.getId()));

        assertTrue(chosen.getBody().getHealth() < chosenBefore,
                "the one he clicked should have been hit");
        assertEquals(nearerBefore, nearer.getBody().getHealth(), 0.01f,
                "and the nearer one left alone");
    }

    /**
     * Aimed at something it cannot reach, it refuses — and keeps its cooldown.
     *
     * <p>Going off at something else would be worse than doing nothing, and
     * spending the cooldown for it worse again.
     */
    @Test
    void aStrikeAimedOutOfRangeIsRefusedAndCostsNothing() {
        var q = SETTINGS.skillsFor("Hero").stream()
                .filter(skill -> skill.key() == 'Q').findFirst().orElseThrow();
        var arena = arena(SETTINGS, 150f + q.range() + 60f, 150f);
        var far = skeletonBeyond(arena.game(), 0f);

        assertFalse(arena.book().cast('Q', 1, far.getId(), null), "it is out of reach");
        assertEquals(0, arena.book().cooldownOf('Q'), "so the cooldown is still his");
        assertEquals(far.getBody().getMaxHealth(), far.getBody().getHealth(), 0.01f);
    }

    /** And aiming it at himself is not aiming it at an enemy. */
    @Test
    void aStrikeCannotBeAimedAtSomethingThatIsNotAnEnemy() {
        var arena = arena(SETTINGS, 170f, 150f);

        assertFalse(arena.book().cast('Q', 1, arena.hero().getId(), null));
        assertEquals(0, arena.book().cooldownOf('Q'));
    }

    /**
     * The dash goes where he was pointed, whichever way he happened to be looking.
     *
     * <p>Along his facing is what it did before there was anything to point at,
     * and it is still the fallback — but a dash you cannot steer is one you use to
     * escape and then find you have run into the room you were escaping.
     */
    @Test
    void aDashGoesWhereItWasPointed() {
        var arena = arena(SETTINGS);
        var from = arena.hero().getPosition();
        // Straight up the room, which is not where he starts out facing.
        var towards = new Coord3D(from.x(), from.y() + 200f, 0f);

        assertTrue(arena.book().cast('E', 1, null, towards));

        var landed = arena.hero().getPosition();
        assertTrue(landed.y() - from.y() > 40f, "he should have gone that way: " + landed);
        assertEquals(from.x(), landed.x(), 6f, "and not sideways");
    }

    /**
     * Sent somewhere close, he stops there rather than being flung past it.
     *
     * <p>The click says where, and "where" is a place, not a direction with the
     * full distance attached to it.
     */
    @Test
    void aDashPointedNearbyStopsThere() {
        var arena = arena(SETTINGS);
        var from = arena.hero().getPosition();
        var towards = new Coord3D(from.x() + 20f, from.y(), 0f);

        assertTrue(arena.book().cast('E', 1, null, towards));

        float travelled = arena.hero().getPosition().distance(from);
        assertTrue(travelled <= 21f, "he overshot the click by " + (travelled - 20f));
        assertTrue(travelled > 10f, "and he should have gone most of the way: " + travelled);
    }

    /** Nothing pointed at is still the old behaviour, for the skills that need none. */
    @Test
    void anUnaimedCastStillWorksTheWayItDid() {
        var arena = skillsOnly(170f, 150f);
        var skeleton = skeletonBeyond(arena.game(), 0f);
        float before = skeleton.getBody().getHealth();

        assertTrue(castAndWait(arena, 'Q', 1, null), "the nearest, as before");

        assertTrue(skeleton.getBody().getHealth() < before);
    }

    /** The first living skeleton past {@code x}, in creation order. */
    private static GameObject skeletonBeyond(DukeGame game, float x) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Skeleton"))
                .filter(object -> object.getPosition().x() > x)
                .findFirst().orElseThrow();
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
     * And never into stone. A dash aimed further than there is room for falls
     * short of the rock rather than landing in it, and a unit whose centre is
     * inside a wall is a bug that costs a day.
     */
    @Test
    void theDashNeverLandsInStone() {
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
                "but not the whole way, because there is no floor that far east");
    }

    /** A room split down the middle, so a dash across it has to clear the wall. */
    private static String walledArena() {
        int width = 40;
        int height = 30;
        var text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                text.append(edge || x == 20 ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /**
     * He goes over the wall, not up to it.
     *
     * <p>Which is the whole of what the skill is for. An escape a corridor can
     * cancel is not an escape, and the thing a player most wants to be on the far
     * side of is usually the thing standing between him and the far side. Aimed
     * past a wall he clears it and comes down beyond.
     */
    @Test
    void theDashCarriesHimOverAWall() {
        var world = Dungeon.world(walledArena(), SETTINGS);
        var game = world.game();
        game.spawn("Hero", world.hero(), 155f, 155f);
        game.runHeadless(1);
        var hero = creature(game, "Hero");

        // The wall stands at cell 20, which is 200 to 210. He is west of it and
        // pointed at open floor four cells east of it.
        hero.findModule(SkillBook.class).cast('E', 1, null, new Coord3D(245f, 155f, 0f));

        assertTrue(hero.getPosition().x() > 210f,
                "the wall stopped him at " + hero.getPosition());
        assertFalse(game.getLogic().isGroundBlocked(hero.getPosition()),
                "and he came down on floor, not on the wall");
    }

    /**
     * And past whatever is standing in the way.
     *
     * <p>Same reasoning, one step further: a skeleton is exactly what he is
     * dashing away from, so being stopped by one is being stopped by the problem.
     */
    @Test
    void theDashCarriesHimPastACreature() {
        var arena = arena(SETTINGS, 200f, 155f);
        var hero = arena.hero();
        hero.setPosition(new Coord3D(155f, 155f, 0f));
        arena.game().runHeadless(1);

        assertTrue(arena.book().cast('E', 1, null, new Coord3D(245f, 155f, 0f)));

        assertTrue(hero.getPosition().x() > 210f,
                "the skeleton stopped him at " + hero.getPosition());
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
        var arena = skillsOnly(170f, 150f);
        var skeleton = creature(arena.game(), "Skeleton");
        float before = skeleton.getBody().getHealth();
        castAndWait(arena, 'Q', level, null);
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

    /**
     * And what the player pointed at survives the trip.
     *
     * <p>The click happens on the render thread and the cast on the simulation's,
     * a frame later, with a command in between. Everything above tests the far end
     * of that; this is the one test that would notice the near end quietly dropping
     * what it was carrying.
     */
    @Test
    void whatWasPointedAtSurvivesTheCommand() {
        // A real run, because the command is only routed by one — but with the two
        // skeletons carried to known spots, so the geometry is the test's rather
        // than the seed's.
        var session = Dungeon.newSession(11L, SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        var hero = creature(game, "Hero");
        var skeletons = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Skeleton"))
                .filter(object -> !object.isEffectivelyDead())
                .limit(2).toList();
        assertEquals(2, skeletons.size(), "this floor should have two to choose between");

        var nearer = skeletons.get(0);
        var chosen = skeletons.get(1);
        nearer.setPosition(new Coord3D(hero.getPosition().x() + 25f, hero.getPosition().y(), 0f));
        chosen.setPosition(new Coord3D(hero.getPosition().x() + 45f, hero.getPosition().y(), 0f));
        float nearerBefore = nearer.getBody().getHealth();
        float chosenBefore = chosen.getBody().getHealth();

        game.postCommand(new CastSkill(game.getLocalPlayerIndex(), 'Q',
                chosen.getId(), null));
        game.runHeadless(untilItLands('Q'));

        // His bow is his own here — this is a real run — and it takes the nearest,
        // always. So the far one being hurt can only be the strike, and the strike
        // could only have chosen it from the command.
        assertTrue(chosen.getBody().getHealth() < chosenBefore,
                "the one the command named should have been hit");
        assertTrue(nearerBefore - nearer.getBody().getHealth()
                        < chosenBefore - chosen.getBody().getHealth(),
                "the nearer one took the heavier hit, which is what a dropped target looks like");
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
