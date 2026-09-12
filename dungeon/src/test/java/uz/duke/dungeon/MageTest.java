package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ObjectStatus;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.skill.Skill;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.dungeon.skill.SkillEffect;
import uz.duke.game.DukeGame;

/**
 * The third hero: that he exists, that he is nobody else, and that his four
 * skills do the four things they are sold as.
 *
 * <p>Almost nothing here is a balance figure. The numbers are the file's and a
 * re-tune should move these tests with it rather than break them; what is held
 * still is the SHAPE — that a fireball can miss, that a nova leaves what it
 * caught slower than it was, that a blink puts him where he asked in one frame,
 * and that a meteor does nothing at all for the second and a half it is falling.
 *
 * <p>The last test is the one that matters most and asserts nothing about the
 * mage: the archer and the knight are untouched.
 */
class MageTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final String ARCHER = "Hero";
    private static final String KNIGHT = "Knight";
    private static final String MAGE = "Mage";

    // ---- who he is ----

    /** A whole hero: a creature, a block, four skills and a face. */
    @Test
    void heIsAWholeHero() {
        assertNotNull(templateOf(MAGE), "he has no creature block");
        assertEquals(MAGE, SETTINGS.heroNamed(MAGE).name(), "he has no DungeonHero block");
        assertEquals(4, SETTINGS.skillsFor(MAGE).size(), "he does not have four skills");
        assertTrue(SETTINGS.portraits().stream().anyMatch(art -> art.name().equals(MAGE)),
                "he has no portrait, so the panel would frame him by the general rule");
        assertFalse(SETTINGS.heroNamed(MAGE).title().isBlank(),
                "he would be offered as a name and nothing else");
    }

    /** And the four are the four he is sold as, in the four slots. */
    @Test
    void hisFourAreTheFourHeIsSoldAs() {
        assertEquals(SkillEffect.SKILLSHOT, skillOf(MAGE, 'Q').effect(), "Q is a fireball");
        assertEquals(SkillEffect.AREA_DAMAGE, skillOf(MAGE, 'W').effect(), "W is a nova");
        assertEquals(SkillEffect.BLINK, skillOf(MAGE, 'E').effect(), "E is a blink");
        assertEquals(SkillEffect.METEOR, skillOf(MAGE, 'R').effect(), "R is a meteor");
    }

    /**
     * The squishiest thing that has ever been playable here.
     *
     * <p>Both halves, because they are two different axes: health is how long he
     * lasts and armour is what each blow is worth, and a mage who was merely
     * low on one of them would be an archer with better skills.
     */
    @Test
    void heIsTheSquishiestOfTheThree() {
        assertTrue(health(MAGE) < health(ARCHER), "he is no tougher than the archer");
        assertTrue(health(MAGE) < health(KNIGHT) / 2f, "and nowhere near the knight");
        assertTrue(SETTINGS.heroNamed(MAGE).armourPercent()
                < SETTINGS.heroNamed(KNIGHT).armourPercent(),
                "a mage in the knight's plate is a knight who can also throw meteors");
    }

    /**
     * And his weapon is the feeblest, which is the other half of the same
     * bargain: everything he is worth is on a cooldown.
     */
    @Test
    void hisOwnWeaponIsTheFeeblestOfTheThree() {
        assertTrue(damagePerFrame(MAGE) < damagePerFrame(ARCHER),
                "a mage whose auto-attack carried him is an archer with a better ultimate");
        assertTrue(damagePerFrame(MAGE) < damagePerFrame(KNIGHT),
                "and the knight has to walk into the room for his");
        assertTrue(reach(MAGE) < reach(ARCHER), "nor does he outrange the archer");
    }

    // ---- Q: a shot that can miss ----

    /**
     * Fireball goes down a lane and takes the room with it where it stops.
     *
     * <p>Two skeletons, one behind the other and neither of them clicked on: the
     * shot is aimed at a PLACE. The first is what it runs into and the second is
     * near enough to be caught by the burst — which is the whole of what makes it
     * worth aiming rather than clicking.
     */
    @Test
    void hisFireballBurstsWhereItStops() {
        var arena = arena(200f, 150f, 216f, 150f);
        float firstBefore = health(arena, 0);
        float secondBefore = health(arena, 1);

        assertTrue(arena.book().cast('Q', 1, null, new Coord3D(260f, 150f, 0f)));
        arena.game().runHeadless(40);

        assertTrue(health(arena, 0) < firstBefore, "it did not hit what was in the way");
        assertTrue(health(arena, 1) < secondBefore,
                "the one standing beside it was not caught by the burst, so it is an arrow");
    }

    /**
     * And it is spent against a wall, which is the promise the lane makes.
     *
     * <p>The indicator draws a lane that stops at the stone. A shot that carried
     * on through one would make that picture a lie — so the skeleton on the far
     * side of the wall is untouched however squarely he is aimed at.
     */
    @Test
    void hisFireballIsSpentAgainstAWall() {
        // A room with a wall down the middle, and one skeleton on each side.
        var arena = arena(walledArena(), 190f, 150f, 260f, 150f);
        float behind = health(arena, 1);

        assertTrue(arena.book().cast('Q', 1, null, new Coord3D(280f, 150f, 0f)));
        arena.game().runHeadless(60);

        assertEquals(behind, health(arena, 1), 0.01f,
                "it went through the wall, so the lane the client draws is a lie");
    }

    // ---- W: the panic button ----

    /**
     * Frost Nova leaves what it caught dragging its feet.
     *
     * <p>The engine's own {@code SLOWED}, worn by the monster rather than
     * remembered by the mage: {@code MoveUpdate} already halves the step of
     * anything wearing it, so the slow costs the simulation no new idea at all.
     */
    @Test
    void hisFrostNovaSlowsWhatItCaught() {
        var arena = arena(180f, 150f);
        var caught = arena.skeletons().get(0);

        assertTrue(arena.book().cast('W', 1));

        assertTrue(caught.hasStatus(ObjectStatus.SLOWED),
                "it walked out of a frost nova at full speed");
    }

    /**
     * And it thaws on its own, after the number of frames the file names —
     * <em>with the mage dead</em>.
     *
     * <p>Which is the whole reason the status is worn by the monster rather than
     * remembered in the caster's skill book. A list kept by the mage would be
     * swept away with him, and every skeleton he chilled on the way out would
     * walk at half speed for the rest of the run.
     *
     * <p>Killing him is also what makes the test say anything: left alive he
     * shoots the skeleton he just chilled, the skeleton dies inside the ninety
     * frames, and "it is no longer slowed" would be true of a corpse.
     */
    @Test
    void theChillThawsEvenAfterItsCasterIsDead() {
        var arena = arena(180f, 150f);
        var caught = arena.skeletons().get(0);
        int frames = skillOf(MAGE, 'W').slowFrames();

        arena.book().cast('W', 1);
        assertTrue(caught.hasStatus(ObjectStatus.SLOWED), "the premise: it was chilled");
        arena.hero().markDestroyed();
        arena.game().runHeadless(frames + 2);

        assertFalse(caught.isEffectivelyDead(), "the premise: it outlived the nova");
        assertFalse(caught.hasStatus(ObjectStatus.SLOWED),
                "it is still slowed " + frames + " frames later, which is for ever");
    }

    // ---- E: the escape ----

    /** Blink puts him where he asked, in the frame he asked. */
    @Test
    void hisBlinkArrivesAtOnce() {
        var arena = arena();
        var from = arena.hero().getPosition();
        var wanted = new Coord3D(from.x() + 50f, from.y(), from.z());

        assertTrue(arena.book().cast('E', 1, null, wanted));

        assertTrue(arena.hero().getPosition().distance(from) > 40f,
                "he is still standing where he was, so the cast went nowhere");
    }

    /**
     * It is an escape and not a weapon: nothing it passes is any worse off.
     *
     * <p>The one thing that separates it from the knight's charge in the
     * simulation, and it is deliberate — a blink that also hurt would be his
     * charge with the drawbacks taken out.
     */
    @Test
    void hisBlinkHurtsNothingOnTheWay() {
        var arena = arena(180f, 150f);
        var passed = arena.skeletons().get(0);
        float before = passed.getBody().getHealth();

        arena.book().cast('E', 1, null, new Coord3D(210f, 150f, 0f));

        assertEquals(before, passed.getBody().getHealth(), 0.01f,
                "it was trampled, so this is a charge rather than a blink");
    }

    /** And it never puts him inside stone. */
    @Test
    void hisBlinkNeverLandsInAWall() {
        var arena = arena(walledArena());

        arena.book().cast('E', 1, null, new Coord3D(280f, 150f, 0f));

        var grid = arena.game().getLogic().getPathGrid();
        var standing = arena.hero().getPosition();

        assertFalse(grid.isBlocked(grid.toCellX(standing), grid.toCellY(standing)),
                "he is standing in the wall");
    }

    // ---- R: the one with a pause in it ----

    /**
     * The meteor does nothing at all while it is falling, and then everything.
     *
     * <p>The delay IS the skill, so this asserts the delay rather than the
     * damage: a skeleton standing under the mark is untouched for the whole of
     * the wind-up and hurt on the frame it lands. Anything else and a monster
     * could not walk out of it, which is the thing worth being able to do.
     */
    @Test
    void hisMeteorLandsAfterThePause() {
        var arena = arena(220f, 150f);
        var under = arena.skeletons().get(0);
        float before = under.getBody().getHealth();
        int falling = skillOf(MAGE, 'R').windUpFrames();

        assertTrue(arena.book().cast('R', 5, null, new Coord3D(220f, 150f, 0f)));
        arena.game().runHeadless(falling - 1);

        assertEquals(before, under.getBody().getHealth(), 0.01f,
                "it landed early, so there was nothing to walk out of");

        arena.game().runHeadless(3);
        assertTrue(under.getBody().getHealth() < before, "and then it never landed at all");
    }

    /**
     * The warning is a thing in the world, which is the whole reason it costs no
     * client code — and the reason everyone in the room can see it.
     */
    @Test
    void theWarningIsAnObjectAndThenIsGone() {
        var arena = arena(220f, 150f);

        arena.book().cast('R', 5, null, new Coord3D(220f, 150f, 0f));
        arena.game().runHeadless(2);

        assertNotNull(creature(arena.game(), "MeteorMark"),
                "nothing is standing on the floor, so the warning is invisible");

        arena.game().runHeadless(skillOf(MAGE, 'R').windUpFrames() + 4);
        assertFalse(arena.game().getLogic().getObjects().stream().anyMatch(
                object -> object.getTemplate().getName().equals("MeteorMark")),
                "the mark outlived its meteor, so the scene grows by one a cast");
    }

    // ---- and what the client is told about all of it ----

    /**
     * A cast says where it wants drawing, and the client is told nothing else.
     *
     * <p>The simulation deciding what a skill LOOKS like would be the wrong way
     * round, and this is the line it does not cross: it says where, which nothing
     * but the simulation knows, and names a block in the art file for the rest.
     */
    @Test
    void aCastSaysWhereItWantsDrawing() {
        var arena = arena(180f, 150f);

        arena.book().cast('W', 1);

        var marks = arena.book().getCastMarks();
        assertEquals(1, marks.size(), "a nova is one place");
        assertEquals(skillOf(MAGE, 'W').look(), marks.get(0).look());
        assertEquals(arena.hero().getPosition().x(), marks.get(0).x(), 0.01f,
                "a nova is drawn round him, wherever he is standing");
        assertEquals(skillOf(MAGE, 'W').radius(), marks.get(0).radius(), 0.01f,
                "and as wide as it actually reached, rather than as wide as the block guessed");
    }

    /**
     * A blink is two places, and that is the whole of what it looks like.
     *
     * <p>A flash where he was and a flash where he is, with nothing drawn between
     * them. Half a blink is a teleport with a bug — the man appears somewhere
     * else and the spot he left says nothing about it.
     */
    @Test
    void aBlinkIsDrawnAtBothEnds() {
        var arena = arena();
        var from = arena.hero().getPosition();

        arena.book().cast('E', 1, null, new Coord3D(from.x() + 50f, from.y(), 0f));

        var marks = arena.book().getCastMarks();
        assertEquals(2, marks.size(), "a blink drawn at one end is a man teleporting by accident");
        assertEquals(from.x(), marks.get(0).x(), 0.01f, "the first is where he left");
        assertTrue(marks.get(1).x() > marks.get(0).x() + 30f, "and the second is where he arrived");
    }

    /** The meteor marks the ground it is going to land on, not the man who called it. */
    @Test
    void theMeteorIsDrawnWhereItWillLand() {
        var arena = arena();
        var spot = new Coord3D(220f, 150f, 0f);

        arena.book().cast('R', 5, null, spot);

        var marks = arena.book().getCastMarks();
        assertEquals(1, marks.size());
        assertEquals(spot.x(), marks.get(0).x(), 0.01f);
        assertEquals(skillOf(MAGE, 'R').radius(), marks.get(0).radius(), 0.01f,
                "the warning has to be as wide as the blast, or it is not a warning");
    }

    // ---- and the two who were here first ----

    /**
     * Neither of the others was re-balanced to make room for him.
     *
     * <p>The figures are copied out of the file as it stood before there was a
     * mage. A hero is added by adding one, not by quietly lowering the two who
     * were already playable.
     */
    @Test
    void theArcherAndTheKnightWereNotRebalanced() {
        assertEquals(550f, health(ARCHER), 0.01f, "the archer's health moved");
        assertEquals(980f, health(KNIGHT), 0.01f, "the knight's health moved");
        assertEquals(60f, reach(ARCHER), 0.01f, "the archer's range moved");
        assertEquals(11f, reach(KNIGHT), 0.01f, "the knight's range moved");
        assertEquals(4, SETTINGS.skillsFor(ARCHER).size());
        assertEquals(4, SETTINGS.skillsFor(KNIGHT).size());
        for (var skill : SETTINGS.skillsFor(KNIGHT)) {
            assertEquals(0, skill.slowFrames(),
                    "the knight's " + skill.key() + " picked up a slow it never asked for");
        }
    }

    // ---- the arena ----

    private record Arena(DukeGame game, GameObject hero, SkillBook book,
            java.util.List<GameObject> skeletons) {
    }

    private static Arena arena(float... skeletonXy) {
        return arena(openArena(), skeletonXy);
    }

    private static Arena arena(String map, float... skeletonXy) {
        var world = Dungeon.world(map, SETTINGS, Content.read(Content.CREATURES));
        var game = world.game();
        game.spawn(MAGE, world.hero(), 150f, 150f);
        for (int i = 0; i + 1 < skeletonXy.length; i += 2) {
            game.spawn("Skeleton", world.dungeon(), skeletonXy[i], skeletonXy[i + 1]);
        }
        game.runHeadless(1);
        var hero = creature(game, MAGE);
        var skeletons = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals("Skeleton"))
                .sorted(java.util.Comparator.comparingInt(object -> object.getId().value()))
                .toList();
        return new Arena(game, hero, hero.findModule(SkillBook.class), skeletons);
    }

    /** An empty room, stone only round the edge. */
    private static String openArena() {
        return room(40, 30, -1);
    }

    /** The same room with a wall down the middle of it, at about x = 240. */
    private static String walledArena() {
        return room(40, 30, 24);
    }

    private static String room(int width, int height, int wallColumn) {
        var text = new StringBuilder();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean edge = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                text.append(edge || x == wallColumn ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject creature(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals(template))
                .findFirst().orElse(null);
    }

    private static float health(Arena arena, int which) {
        return arena.skeletons().get(which).getBody().getHealth();
    }

    // ---- the file, read the way the game reads it ----

    private static Skill skillOf(String hero, char key) {
        return SETTINGS.skillsFor(hero).stream().filter(skill -> skill.key() == key)
                .findFirst().orElseThrow(() -> new AssertionError(hero + " has no " + key));
    }

    /** Built once: a fresh dungeon per assertion is a generated map per assertion. */
    private static uz.duke.core.thing.ThingFactory templates;

    private static uz.duke.core.thing.ThingTemplate templateOf(String name) {
        if (templates == null) {
            var game = Dungeon.world(openArena(), SETTINGS, Content.read(Content.CREATURES))
                    .game();
            game.runHeadless(1);
            templates = game.getLogic().getThingFactory();
        }
        return templates.findTemplate(name);
    }

    private static float health(String hero) {
        for (var entry : templateOf(hero).getModules()) {
            if (entry.data() instanceof uz.duke.dungeon.level.GrowableBody.Data body) {
                return body.maxHealth();
            }
        }
        return 0f;
    }

    private static uz.duke.rts.module.WeaponUpdate.Data weaponOf(String hero) {
        for (var entry : templateOf(hero).getModules()) {
            if (entry.data() instanceof uz.duke.rts.module.WeaponUpdate.Data weapon) {
                return weapon;
            }
        }
        throw new AssertionError(hero + " carries no weapon");
    }

    private static float reach(String hero) {
        return weaponOf(hero).attackRange();
    }

    private static float damagePerFrame(String hero) {
        var weapon = weaponOf(hero);
        return weapon.damage() / (float) weapon.reloadFrames();
    }
}
