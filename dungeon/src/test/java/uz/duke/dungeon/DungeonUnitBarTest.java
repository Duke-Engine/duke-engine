package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.module.ActiveBody;
import uz.duke.core.thing.ThingTemplate;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.level.GrowableBody;

/**
 * The segment table in the settings file divides everything this game makes.
 *
 * <p>The table is what lets a player read a bar he has never seen: every
 * creature alive is marked off in the same lots, so one that has been counted
 * teaches the next. That only works while the count stays in a band — too few
 * marks is not a scale, too many is texture — and nothing about a badly chosen
 * rung looks wrong. A monster with four marks looks like a decision.
 *
 * <p>So it is checked three ways, and they fail at different times. The first is
 * against the creatures the game ships <em>today</em>, which is what is on
 * screen. The second is against the whole range the table claims to cover, which
 * is what catches a new monster before anybody has drawn it. The third is the
 * arithmetic that makes the other two possible at all, which is what says why
 * there are nine rungs rather than seven.
 *
 * <p>The rules themselves live on the client and are tested there — see
 * {@code uz.duke.client3d.UnitBarLookTest}. This is about the file.
 */
class DungeonUnitBarTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    /** Fewer than this is not a scale, it is four blocks. */
    private static final int FEWEST = 8;
    /** More, and the marks are closer together than they are wide. */
    private static final int MOST = 20;

    private static uz.duke.client3d.UnitBarLook look() {
        return Main.unitBars(SETTINGS);
    }

    /**
     * What a template's body is worth before any depth has been applied to it.
     *
     * <p>★ IT IS A {@code GrowableBody}, NOT THE ENGINE'S OWN. Every creature in
     * this game has one, because every creature's maximum moves — the hero's with
     * his levels, a monster's with the depth it was spawned at — and the engine
     * fixes its own body's maximum when the unit is built. Matching only
     * {@link uz.duke.core.module.ActiveBody.Data} therefore found nothing, in
     * silence, and the whole of what this test measured was the two fixture
     * creatures: seven sizes across 60 to 280 health, against a table built for
     * 30 to 1320. It passed. It was caught by printing what it had looked at.
     *
     * <p>The engine's body is still read, for anything that has one.
     */
    private static float baseHealth(ThingTemplate template) {
        if (template == null) {
            return 0f;
        }
        for (var module : template.modules()) {
            switch (module) {
                case GrowableBody.Data body -> {
                    return body.maxHealth();
                }
                case uz.duke.core.module.ActiveBody.Data body -> {
                    return body.maxHealth();
                }
                default -> { }
            }
        }
        return 0f;
    }

    /** Every creature in the game, at every size the game can present it at. */
    private static List<float[]> everySizeTheGameMakes() {
        var game = Dungeon.create(1234L);
        game.runHeadless(1); // boots the world, which is what loads the real files
        var factory = game.getLogic().getThingFactory();
        var sizes = new ArrayList<float[]>();
        int floors = Math.max(1, SETTINGS.finalDepth());

        for (var kind : SETTINGS.monsters()) {
            float base = baseHealth(factory.findTemplate(kind.name()));
            assertTrue(base > 0f, kind.name() + " has no body in the shipped files");
            for (int depth = Math.max(1, kind.minDepth()); depth <= floors; depth++) {
                sizes.add(new float[] {base * SETTINGS.monsterHealthAt(depth), depth});
            }
        }
        // The boss of a floor climbs faster than its underlings, and is the one
        // creature the table is most likely to be short at the top for.
        for (int depth = 1; depth <= floors; depth++) {
            float base = baseHealth(factory.findTemplate(SETTINGS.bossKindAt(depth)));
            assertTrue(base > 0f, "the boss of depth " + depth + " has no body");
            sizes.add(new float[] {base * SETTINGS.bossHealthAt(depth), depth});
        }
        // And the hero, who is the bar the player looks at most: at the bottom
        // and at the top of what levelling can add to him.
        int top = SETTINGS.levelling().maxLevel();
        for (var hero : SETTINGS.heroes()) {
            var template = factory.findTemplate(hero.name());
            assertTrue(baseHealth(template) > 0f, hero.name() + " has no body");
            // His block is what his strength is added to, so the bar is sized by the
            // two together -- at his first level and at the last.
            for (int level : new int[] {1, top}) {
                sizes.add(new float[] {uz.duke.dungeon.level.HeroFigures.of(
                        uz.duke.dungeon.level.HeroBase.of(template), hero.maxMana(),
                        hero.attributes(), SETTINGS.attributeRules(), level,
                        uz.duke.dungeon.level.HeroFigures.Found.NOTHING).maxHealth(), 1});
            }
        }
        return sizes;
    }

    /**
     * Every creature the game actually makes is divided into a countable number
     * of marks.
     *
     * <p>Measured off the shipped files rather than against a round range:
     * monsters at every depth they can be met, each floor's boss on its own
     * floor with its own steeper curve, and both ends of what a hero grows into.
     * A monster added to the file with an awkward maximum fails here before
     * anybody has looked at it.
     */
    @Test
    void everyCreatureThisGameMakesIsDividedIntoACountableBar() {
        var look = look();
        var wrong = new ArrayList<String>();
        for (var size : everySizeTheGameMakes()) {
            float health = size[0];
            if (health <= 0f) {
                continue; // no body: a prop, and it is drawn no bar
            }
            int marks = look.segmentsFor(health);
            if (marks < FEWEST || marks > MOST) {
                wrong.add(Math.round(health) + " health at depth " + (int) size[1]
                        + " gives " + marks + " marks (one worth "
                        + look.valueFor(health) + ")");
            }
        }
        if (!wrong.isEmpty()) {
            fail("the table divides these badly:\n  " + String.join("\n  ", wrong));
        }
    }

    /**
     * And everything the table claims to cover, not only what exists today.
     *
     * <p>The band is the table's promise, so it is worth holding across the whole
     * range rather than across the handful of creatures that happen to be in the
     * file. This is the test that fails when somebody moves a rung, which is a
     * one-character edit whose effect is invisible anywhere else.
     */
    @Test
    void theWholeRangeTheTableCoversIsCountable() {
        var look = look();
        int ceiling = look.steps().get(look.steps().size() - 1).value() * MOST;
        for (int health = SETTINGS.unitBar().shortestAt(); health <= ceiling; health++) {
            int marks = look.segmentsFor(health);
            assertTrue(marks >= FEWEST && marks <= MOST,
                    health + " health gives " + marks + " marks, one worth "
                            + look.valueFor(health));
        }
    }

    /**
     * Why there are nine rungs and not seven.
     *
     * <p>A rung covering health from just over {@code LO} up to {@code HI} in
     * lots of {@code V} needs {@code HI / V} at most twenty and {@code LO / V} at
     * least eight. Both can hold only while {@code HI} is at most two-and-a-half
     * times {@code LO} — so the rungs climb in steps of two or two-and-a-half and
     * never in the powers of ten anybody would reach for first.
     *
     * <p>Stated as its own test because it is the constraint the table was built
     * from, and a rung added later by eye will satisfy neither it nor the band
     * above. This one says which rung and by how much; the band test only says
     * that some health somewhere came out wrong.
     */
    @Test
    void noRungClimbsFasterThanItsOwnArithmeticAllows() {
        var steps = look().steps();
        assertTrue(steps.size() >= 2, "a table of one rung is not a table");
        for (int at = 1; at < steps.size(); at++) {
            var rung = steps.get(at);
            int below = steps.get(at - 1).upTo();
            if (rung.upTo() == 0) {
                continue; // the open end has no ceiling to be too far above
            }
            assertTrue(rung.upTo() <= below * 2.5f + 0.001f,
                    "the rung ending at " + rung.upTo() + " starts just above "
                            + below + ", which is further than "
                            + FEWEST + "-to-" + MOST + " marks can stretch");
            assertTrue(rung.value() * MOST >= rung.upTo(),
                    "at " + rung.upTo() + " health, lots of " + rung.value()
                            + " come to more than " + MOST + " marks");
            assertTrue(rung.value() * FEWEST <= below + 1,
                    "just above " + below + " health, lots of " + rung.value()
                            + " come to fewer than " + FEWEST + " marks");
        }
    }

    /** The file says enough for the client to draw anything at all. */
    @Test
    void theFileSaysEnoughToDrawWith() {
        var look = look();

        assertTrue(look.draws(), "no table, no bars");
        assertTrue(look.hasMana(), "this game has mana, so a creature with any gets a bar");
        assertTrue(look.longest() > look.shortest(), "a boss's bar has to be the longer one");
        assertTrue(look.widthFor(20_000f) <= look.longest(),
                "nothing draws a bar past the longest");
    }
}
