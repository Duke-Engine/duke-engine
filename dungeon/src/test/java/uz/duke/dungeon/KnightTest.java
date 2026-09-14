package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.level.GrowableBody;
import uz.duke.dungeon.level.HeroProgress;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.dungeon.skill.SkillEffect;
import uz.duke.rts.module.WeaponUpdate;

/**
 * The second hero, and the promise that adding him was a file rather than a
 * rewrite.
 *
 * <p>Two things are being held here and they pull in opposite directions. The
 * first is that he <em>is</em> the archer's opposite — a hero who played the same
 * way would be a reskin, and the numbers that make him different are the ones
 * worth failing a build over. The second is that the archer did not move: a
 * second hero that quietly rebalanced the first is the classic way this goes
 * wrong, and nothing here would notice it except a test that says so.
 *
 * <p>What is deliberately <em>not</em> asserted is his art. His model has not
 * arrived yet, and a hero without one is a coloured shape by design — see the
 * note over {@code DungeonHero Knight}. Naming a file that is not there would
 * make this class fail for the one reason that is not a fault.
 */
class KnightTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final String ARCHER = "Rogue";
    private static final String KNIGHT = "Knight";

    // ---- the two of them, as the file describes them ----

    @Test
    void theFileDescribesBothOfThem() {
        var names = SETTINGS.heroes().stream().map(hero -> hero.name()).toList();

        assertTrue(names.contains(ARCHER), "the archer went missing: " + names);
        assertTrue(names.contains(KNIGHT), "the knight is not in the file: " + names);
    }

    /** Each is a whole hero: a creature, a block, four skills and a face. */
    @Test
    void eachHeroIsComplete() {
        for (var name : java.util.List.of(ARCHER, KNIGHT)) {
            assertNotNull(templateOf(name), name + " has no creature block");
            assertEquals(name, SETTINGS.heroNamed(name).name(), name + " has no DungeonHero block");
            assertEquals(4, SETTINGS.skillsFor(name).size(), name + " does not have four skills");
            assertTrue(SETTINGS.portraits().stream().anyMatch(art -> art.name().equals(name)),
                    name + " has no portrait");
        }
    }

    /** And each is called something of his own, rather than the panel's one word. */
    @Test
    void eachHeroHasHisOwnTitle() {
        var archer = SETTINGS.heroNamed(ARCHER).title();
        var knight = SETTINGS.heroNamed(KNIGHT).title();

        assertFalse(archer.isBlank(), "the archer's title was left to the panel's");
        assertFalse(knight.isBlank(), "the knight would wear the archer's title");
        assertFalse(archer.equals(knight), "both heroes are called the same thing");
    }

    // ---- he is the archer's opposite, and that is the whole point of him ----

    /**
     * More of him to kill, and each blow worth less.
     *
     * <p>Both, rather than either: health is how long he lasts and armour is what
     * a blow is worth, and a knight who had only the first would be an archer who
     * takes longer to grind down.
     */
    @Test
    void heIsHarderToKillThanTheArcher() {
        assertTrue(health(KNIGHT) > health(ARCHER) * 1.5f,
                "the knight has " + health(KNIGHT) + " against the archer's " + health(ARCHER));
        assertTrue(SETTINGS.heroNamed(KNIGHT).armourPercent()
                        > SETTINGS.heroNamed(ARCHER).armourPercent(),
                "the knight wears no more than the archer does");
    }

    /**
     * And he pays for it in the two things that decide whether a mistake can be
     * walked away from.
     */
    @Test
    void hePaysForItInReachAndSpeed() {
        assertTrue(reach(KNIGHT) < reach(ARCHER) / 3f,
                "the knight reaches " + reach(KNIGHT) + " against the archer's " + reach(ARCHER)
                        + " — a knight who outranges a fist is not walking into anything");
        assertTrue(speed(KNIGHT) < speed(ARCHER),
                "the knight moves at " + speed(KNIGHT) + " against " + speed(ARCHER));
    }

    /** He hits harder for it, which is what makes a single swing worth taking. */
    @Test
    void heHitsHarderThanTheArcher() {
        assertTrue(damage(KNIGHT) > damage(ARCHER),
                "the knight swings for " + damage(KNIGHT) + " against " + damage(ARCHER));
    }

    /**
     * He cannot see as far, and it costs him: the floor opens up around exactly
     * this number, so it is map knowledge as well as sight.
     */
    @Test
    void heSeesLessOfTheFloorThanTheArcher() {
        assertTrue(vision(KNIGHT) < vision(ARCHER),
                "the knight sees " + vision(KNIGHT) + " against the archer's " + vision(ARCHER));
    }

    /**
     * Every one of those numbers is in the file.
     *
     * <p>The promise, held from the outside: change the figures and the hero
     * changes, with nothing rebuilt. If this ever fails it means a number moved
     * into Java, which is the thing the whole arrangement exists to prevent.
     */
    @Test
    void hisNumbersComeOutOfTheFile() {
        var rewritten = DungeonSettings.parse("""
                DungeonHero Knight
                  Title = Boshqacha
                  ArmourPercent = 44
                End
                """);

        assertEquals("Boshqacha", rewritten.heroNamed(KNIGHT).title());
        assertEquals(44, rewritten.heroNamed(KNIGHT).armourPercent());
    }

    // ---- his four ----

    /**
     * Four skills, four shapes, and none of them the archer's four.
     *
     * <p>Not a check that they are "different enough" — a check that he plays at a
     * different distance. The archer's longest reaches most of a room; nothing of
     * the knight's reaches past a doorway, and that is what forces the player in.
     */
    @Test
    void nothingHeCastsReachesAcrossARoom() {
        float furthest = 0f;
        for (var skill : SETTINGS.skillsFor(KNIGHT)) {
            furthest = Math.max(furthest,
                    Math.max(skill.range(), Math.max(skill.distance(), skill.radius())));
        }
        float archersLongest = 0f;
        for (var skill : SETTINGS.skillsFor(ARCHER)) {
            archersLongest = Math.max(archersLongest, skill.range());
        }

        assertTrue(furthest < archersLongest, "the knight's longest skill reaches " + furthest
                + " against the archer's " + archersLongest);
    }

    /** He has the one shape the archer has not, and it is his answer to being surrounded. */
    @Test
    void heHasSomethingTheArcherDoesNot() {
        var his = SETTINGS.skillsFor(KNIGHT).stream().map(skill -> skill.effect()).toList();
        var archers = SETTINGS.skillsFor(ARCHER).stream().map(skill -> skill.effect()).toList();

        assertTrue(his.contains(SkillEffect.GUARD), "the knight cannot survive being reached");
        assertFalse(archers.contains(SkillEffect.GUARD),
                "the archer was given the knight's answer as well");
    }

    /** His ultimate lasts, where the archer's is a window for other things. */
    @Test
    void hisUltimateGoesOnHappening() {
        var ultimate = skillOf(KNIGHT, 'R');

        assertTrue(ultimate.lasts(), "his whirlwind lands once and is over");
        assertTrue(ultimate.durationFrames() > ultimate.tickFrames(),
                "it lands once and calls itself lasting");
        assertTrue(ultimate.isUltimate(), "an ultimate has to be earned");
        assertTrue(ultimate.maxRank() < skillOf(KNIGHT, 'Q').maxRank(),
                "and it grows fewer times than an ordinary skill");
    }

    /** And his charge goes through what is in the way, where the archer's sprint does not. */
    @Test
    void hisChargeHurtsAndTheArchersSprintDoesNot() {
        var charge = skillOf(KNIGHT, 'W');
        var sprint = skillOf(ARCHER, 'E');

        assertEquals(SkillEffect.DASH, charge.effect());
        assertEquals(SkillEffect.DASH, sprint.effect());
        assertTrue(charge.damage() > 0f, "a charge that hurts nothing is a sprint");
        assertEquals(0f, sprint.damage(), 0.001f,
                "the archer's sprint was given damage it never had");
    }

    // ---- and the archer did not move ----

    /**
     * The archer is exactly what he was.
     *
     * <p>The failure this class is most likely to catch, and the least likely to
     * be noticed any other way: a second hero balanced by moving the first. These
     * are his shipped figures, written down.
     */
    @Test
    void theArcherWasNotRebalanced() {
        assertEquals(550f, health(ARCHER), 0.001f);
        assertEquals(29f, speed(ARCHER), 0.001f);
        assertEquals(14f, damage(ARCHER), 0.001f);
        assertEquals(60f, reach(ARCHER), 0.001f);
        assertEquals(0, SETTINGS.heroNamed(ARCHER).armourPercent(),
                "the archer was given armour he never had");
    }

    /** And his four skills are the four they were. */
    @Test
    void theArchersSkillsWereNotRebalanced() {
        assertEquals(SkillEffect.STRIKE, skillOf(ARCHER, 'Q').effect());
        assertEquals(45f, skillOf(ARCHER, 'Q').damage(), 0.001f);
        assertEquals(SkillEffect.AREA_DAMAGE, skillOf(ARCHER, 'W').effect());
        assertFalse(skillOf(ARCHER, 'W').lasts(), "his panic button became a whirlwind");
        assertEquals(SkillEffect.DASH, skillOf(ARCHER, 'E').effect());
        assertEquals(SkillEffect.EMPOWER, skillOf(ARCHER, 'R').effect());
    }

    // ---- who actually walks into the dungeon ----

    /**
     * The file says which of them is played, and it is one of them.
     *
     * <p>Which one is a content decision and not something to fail a build over —
     * the whole reason the line exists is that somebody will change it to try the
     * other hero. What is worth holding is that the name resolves: a typo there is
     * a dungeon with nobody in it, and nothing else would say so.
     */
    @Test
    void theFileNamesAHeroItAlsoDescribes() {
        var played = SETTINGS.playedHero();

        assertEquals(played, SETTINGS.playedHeroLook().name(),
                "DefaultHero names " + played + ", which has no DungeonHero block");
        assertEquals(4, SETTINGS.skillsFor(played).size(), played + " has no four skills");
        assertNotNull(templateOf(played), played + " has no creature block");
    }

    /** And naming the other one is all it takes to swap him in. */
    @Test
    void namingTheOtherOneSwapsHimIn() {
        var swapped = DungeonSettings.parse("""
                DungeonRun Loop
                  DefaultHero = Knight
                End
                """);

        assertEquals(KNIGHT, swapped.playedHero());
    }

    /**
     * Which really does decide who is standing in the dungeon.
     *
     * <p>The settings line and the spawned creature, checked together — the point
     * of the whole change is that those two agree, and they did not before: the
     * word was in Java, so a hero could be described in full and never walk in.
     */
    @Test
    void theHeroTheFileNamesIsTheHeroWhoSpawns() {
        var game = Dungeon.create(11L);
        game.runHeadless(1);

        var spawned = game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals(SETTINGS.playedHero()))
                .findFirst().orElse(null);

        assertNotNull(spawned, "nobody of the played hero's template is in the dungeon");
    }

    /**
     * And a whole run really starts with him in it.
     *
     * <p>The test the rest of this class is arithmetic for. Everything else asks
     * whether a number is in a file; this one generates a dungeon, puts the knight
     * in it, runs it and asks whether he is alive and armed at the end — which is
     * the only question that catches a hero who parses perfectly and cannot be
     * played.
     */
    @Test
    void aWholeRunStartsWithTheKnightInIt() {
        var asKnight = DungeonSettings.parse(withDefaultHero(KNIGHT));
        var game = Dungeon.create(4242L, asKnight);

        game.runHeadless(120); // four seconds of a real floor

        var him = find(game, KNIGHT);
        assertNotNull(him, "the knight never walked into the dungeon");
        assertTrue(him.getBody().getHealth() > 0f, "he did not survive four seconds of floor one");
        assertNotNull(him.findModule(SkillBook.class), "he arrived without his skills");
        assertEquals(4, him.findModule(SkillBook.class).getSkills().size(),
                "he arrived with somebody else's skills, or none");
        assertNull(find(game, ARCHER), "the archer came too");
    }

    /** And swapping back is the same one line. */
    @Test
    void andSwappingBackStartsTheArcher() {
        var game = Dungeon.create(4242L, DungeonSettings.parse(withDefaultHero(ARCHER)));

        game.runHeadless(30);

        assertNotNull(find(game, ARCHER), "the archer did not come back");
        assertNull(find(game, KNIGHT), "the knight is still in the dungeon");
    }

    /**
     * The shipped file with one line changed.
     *
     * <p>The whole file rather than a fragment, because a partial one keeps its
     * lists and loses its loose figures — a dungeon parsed from four lines has no
     * map size — and this has to be a real run.
     */
    private static String withDefaultHero(String template) {
        var file = Content.read(Content.SETTINGS);
        // Whatever it currently says, not "the archer's line": the point of the
        // line is that somebody changes it, so a test that only knew how to change
        // it away from one value would fail for the person using it.
        var swapped = file.replaceAll("(?m)^(\\s*)DefaultHero\\s*=.*$",
                "$1DefaultHero = " + template);
        assertFalse(swapped.equals(file) && !SETTINGS.playedHero().equals(template),
                "DefaultHero is not written the way this test expects to find it");
        return swapped;
    }

    // ---- what his armour is worth, once it reaches his body ----

    /**
     * His plate reaches the body that takes the blows.
     *
     * <p>Not a check that a number was stored: a check that the same blow costs
     * him less than it costs an archer. The armour is set through the level and
     * the loot, in one place, and a figure that never got there would be invisible
     * on every screen except the one that matters.
     */
    @Test
    void hisArmourReachesTheBodyThatTakesTheBlow() {
        float knightsLoss = blowSuffered(KNIGHT);
        float archersLoss = blowSuffered(ARCHER);

        assertTrue(knightsLoss < archersLoss, "a blow costs the knight " + knightsLoss
                + " and the archer " + archersLoss + " — his plate never arrived");
    }

    /** What one 100-point blow actually takes off that hero, armour and all. */
    private static float blowSuffered(String template) {
        var world = Dungeon.world(room(), SETTINGS, Content.read(Content.CREATURES));
        var game = world.game();
        game.spawn(template, world.hero(), 150f, 150f);
        game.runHeadless(1);
        var him = find(game, template);
        var progress = new HeroProgress(world.hero(), SETTINGS.levelling(),
                SETTINGS.attributeRules(), SETTINGS.levelUpBannerFrames(),
                new uz.duke.dungeon.loot.LootBag());
        progress.playing(SETTINGS.heroNamed(template));
        progress.carryOver(game, him);

        float before = him.getBody().getHealth();
        him.getBody().damage(100f);
        return before - him.getBody().getHealth();
    }

    // ---- the shapes themselves, cast in a real world ----

    /** Guard turns blows aside while it lasts, and stops when it runs out. */
    @Test
    void guardTurnsBlowsAsideAndThenStops() {
        var guard = skillOf(KNIGHT, 'E');
        var world = Dungeon.world(room(), SETTINGS, Content.read(Content.CREATURES));
        var game = world.game();
        game.spawn(KNIGHT, world.hero(), 150f, 150f);
        game.runHeadless(1);
        var book = find(game, KNIGHT).findModule(SkillBook.class);

        assertEquals(0, book.getGuardPercent(), "he is guarding before he cast anything");
        book.cast('E', 1, null, null);
        assertTrue(book.getGuardPercent() > 0, "the guard did not go up");

        game.runHeadless(guard.durationFrames() + 2);
        assertEquals(0, book.getGuardPercent(), "the guard never came down");
    }

    /** The whirlwind lands again while it turns, and stops when it is over. */
    @Test
    void theWhirlwindGoesOnLandingAndThenStops() {
        var ultimate = skillOf(KNIGHT, 'R');
        var world = Dungeon.world(room(), SETTINGS, Content.read(Content.CREATURES));
        var game = world.game();
        game.spawn(KNIGHT, world.hero(), 150f, 150f);
        // Close enough to be inside it and standing still: a skeleton that walked
        // out would make this a test about pathfinding.
        game.spawn("Skeleton", world.dungeon(), 150f + ultimate.radius() / 3f, 150f);
        game.runHeadless(1);
        var book = find(game, KNIGHT).findModule(SkillBook.class);
        var victim = find(game, "Skeleton");

        float full = victim.getBody().getHealth();
        // The rank he has put into it, which for a test is simply "he has it".
        book.cast('R', 1, null, null);
        game.runHeadless(1);
        float afterFirst = victim.getBody().getHealth();
        game.runHeadless(ultimate.tickFrames() * 3);
        float afterTurning = victim.getBody().getHealth();

        assertTrue(afterFirst < full, "it did not land at all");
        assertTrue(afterTurning < afterFirst, "it landed once and stopped: that is not a whirlwind");

        // And it ends. Run well past its duration and nothing more comes off.
        game.runHeadless(ultimate.durationFrames() + 10);
        float atRest = victim.getBody().getHealth();
        game.runHeadless(ultimate.tickFrames() * 3);
        assertEquals(atRest, victim.getBody().getHealth(), 0.001f,
                "the whirlwind is still turning long after it should have stopped");
    }

    // ---- what each of them carries ----

    /**
     * {@code Holds} is repeatable, and each one keeps its own lines.
     *
     * <p>The trap in a repeatable block with fields under it: the fields have to
     * attach to the {@code Holds} ABOVE them and not to the last one parsed or to
     * all of them at once. Written the obvious way, a knight's shield would have
     * ended up in the hand his sword is in — both lines say {@code HeldIn}, and
     * whichever was read last would have won for both.
     */
    @Test
    void eachThingCarriedKeepsItsOwnLines() {
        var carried = SETTINGS.heroNamed(KNIGHT).held();

        assertEquals(2, carried.size(), "a sword and a shield");
        assertTrue(carried.get(0).model().contains("sword"), "the sword is named first");
        assertTrue(carried.get(1).model().contains("shield"));
        assertNotEquals(carried.get(0).bone(), carried.get(1).bone(),
                "both are in the same hand, so the second line overwrote the first");
    }

    /** And every hero really does carry two things now, not one. */
    @Test
    void everyHeroCarriesMoreThanOneThing() {
        for (var him : SETTINGS.heroes()) {
            assertTrue(him.held().size() >= 2, him.name() + " carries "
                    + him.held().size() + " thing(s): a hero is rarely one hand");
            for (var held : him.held()) {
                assertTrue(held.isCarried(),
                        him.name() + " has a Holds with no bone under it, which hangs nothing");
            }
        }
    }

    /**
     * A hero who carries one thing is still written the way he always was.
     *
     * <p>The promise the repeatable form has to keep. Every monster in the game
     * names one {@code Holds} and one {@code HeldIn}, and none of them was
     * touched; this checks the hero side of the same shape by reading a block
     * that names exactly one.
     */
    @Test
    void oneThingCarriedIsStillOneBlock() {
        var one = DungeonSettings.parse("""
                DungeonHero Solo
                  Model = models/heroes/rogue.glb
                  Holds = models/heroes/bow.gltf
                  HeldIn = handslot.l
                  HeldScale = 2
                  HeldRoll = 180
                End
                """).heroNamed("Solo").held();

        assertEquals(1, one.size());
        assertEquals("models/heroes/bow.gltf", one.get(0).model());
        assertEquals("handslot.l", one.get(0).bone());
        assertEquals(2f, one.get(0).scale(), 0.001f);
        assertEquals(180f, one.get(0).roll(), 0.001f);
    }

    /** And a spot on a bone is three numbers on one line, because a place is one fact. */
    @Test
    void aCarriedThingMayBeShiftedOffItsBone() {
        var quiver = SETTINGS.heroNamed("Rogue").held().stream()
                .filter(held -> held.model().contains("quiver"))
                .findFirst().orElseThrow(() -> new AssertionError("the archer carries no arrows"));

        assertEquals("chest", quiver.bone(),
                "this rig has two attachment points and both are hands, so a quiver"
                        + " has nowhere to go but a body bone");
        assertTrue(quiver.x() != 0f || quiver.y() != 0f || quiver.z() != 0f,
                "left on the bone with no shift it sits inside him");
    }

    // ---- helpers ----

    private static String room() {
        var text = new StringBuilder();
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 30; x++) {
                boolean edge = x == 0 || y == 0 || x == 29 || y == 29;
                text.append(edge ? '#' : '.');
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static GameObject find(uz.duke.game.DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals(template))
                .findFirst().orElse(null);
    }

    private static uz.duke.dungeon.skill.Skill skillOf(String hero, char key) {
        return SETTINGS.skillsFor(hero).stream()
                .filter(skill -> skill.key() == key)
                .findFirst().orElseThrow(() -> new AssertionError(hero + " has no " + key));
    }

    /**
     * The creature definitions, read once.
     *
     * <p>One world for the class rather than one per question. A game has no logic
     * until it has run a frame — {@code getLogic()} is null before that — and
     * building a fresh dungeon to ask what a knight's health is would be a
     * generated map per assertion.
     */
    private static uz.duke.core.thing.ThingFactory templates;

    private static ThingTemplate templateOf(String name) {
        if (templates == null) {
            var game = Dungeon.world(room(), SETTINGS, Content.read(Content.CREATURES)).game();
            game.runHeadless(1);
            templates = game.getLogic().getThingFactory();
        }
        return templates.findTemplate(name);
    }

    /**
     * What he is at his first level: his creature block and his attributes together,
     * the way the game builds him. The block alone is only what the attributes are
     * added to.
     */
    private static uz.duke.dungeon.level.HeroFigures firstLevel(String template) {
        var look = SETTINGS.heroNamed(template);
        return uz.duke.dungeon.level.HeroFigures.of(
                uz.duke.dungeon.level.HeroBase.of(templateOf(template)), look.maxMana(),
                look.attributes(), SETTINGS.attributeRules(), 1,
                uz.duke.dungeon.level.HeroFigures.Found.NOTHING);
    }

    private static float health(String template) {
        return firstLevel(template).maxHealth();
    }

    private static float damage(String template) {
        return firstLevel(template).attack();
    }

    private static float reach(String template) {
        for (var entry : templateOf(template).getModules()) {
            if (entry.data() instanceof WeaponUpdate.Data weapon) {
                return weapon.attackRange();
            }
        }
        return 0f;
    }

    private static float speed(String template) {
        return firstLevel(template).speed();
    }

    private static float vision(String template) {
        return templateOf(template).getVisionRange();
    }
}
