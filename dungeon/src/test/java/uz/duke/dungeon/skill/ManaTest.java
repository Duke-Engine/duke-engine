package uz.duke.dungeon.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uz.duke.core.GameConstants;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.game.DukeGame;

/**
 * What a skill costs, and what happens when he cannot pay it.
 *
 * <p>Mana is the second thing standing between a key and a spell — the first is
 * the cooldown, and the two have to refuse in different ways. A cooldown is a
 * promise that the skill is coming back; being broke is not, and the difference
 * is what the player is deciding about.
 *
 * <p>Asked of a real world rather than of a stub, because the thing worth
 * holding still is not the arithmetic but the <em>order</em>: a refusal has to
 * happen before the cooldown starts, before a charge is taken and before
 * anything is drawn. Every one of those is easy to get right in isolation and
 * easy to get wrong when they are four lines apart.
 */
class ManaTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final String ARENA = room(40, 30);

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

    private record Cast(DukeGame game, GameObject hero, SkillBook book) {
    }

    /** One hero of the named kind, alone, with a skeleton to aim at. */
    private static Cast arena(String heroTemplate) {
        var world = Dungeon.world(ARENA, SETTINGS);
        var game = world.game();
        game.spawn(heroTemplate, world.hero(), 150f, 150f);
        game.spawn("Skeleton", world.dungeon(), 190f, 150f);
        game.runHeadless(1);
        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(heroTemplate))
                .findFirst().orElseThrow();
        return new Cast(game, hero, hero.findModule(SkillBook.class));
    }

    /**
     * A pool the hero can actually be given, since a bare spawn has none: the
     * book is filled by {@code HeroProgress} out of his level, and nothing has
     * levelled anybody here.
     */
    private static void pool(SkillBook book, int max, int tenthsPerSecond) {
        book.poolOf(max, tenthsPerSecond);
        book.fillMana();
    }

    private static Skill skillOf(String hero, char key) {
        return SETTINGS.skillsFor(hero).stream()
                .filter(s -> s.key() == key).findFirst().orElseThrow();
    }

    // ---- spending ----

    /**
     * Casting takes the skill's price out of the pool.
     *
     * <p>The knight's guard throughout, wherever a cast has to actually go off:
     * it aims at himself, so it cannot be refused for want of a target and the
     * test stays about the mana. Everything aimed somewhere -- a meteor, a
     * skillshot -- refuses on its own terms when there is nowhere to put it, and
     * a test that could not tell the two refusals apart would be worthless.
     */
    @Test
    void castingCosts() {
        var it = arena("Knight");
        pool(it.book(), 200, 0);
        int cost = skillOf("Knight", 'E').manaAt(1);

        assertTrue(it.book().cast('E', 1), "the knight should have been able to guard");

        assertEquals(200 - cost, it.book().getMana(), "the cast should have cost him " + cost);
        assertTrue(cost > 0, "and the file should be charging something for it");
    }

    /**
     * Broke: nothing happens at all.
     *
     * <p>The whole of what the refusal has to be. A cooldown started, a charge
     * taken or an effect drawn would each leave the player believing he cast
     * something — and the cooldown in particular would be the cruellest of the
     * three, since the skill he could not afford would then also be the one he
     * has to wait for.
     */
    @Test
    void withoutTheManaNothingHappensAtAll() {
        var it = arena("Knight");
        int cost = skillOf("Knight", 'E').manaAt(1);
        pool(it.book(), cost - 1, 0);

        assertFalse(it.book().cast('E', 1), "he cannot afford it");

        assertEquals(0, it.book().cooldownOf('E'), "and the cooldown must not have started");
        assertTrue(it.book().isReady('E'), "the slot is still ready, it is the purse that is not");
        assertEquals(cost - 1, it.book().getMana(), "and nothing was taken from him");
    }

    /** And he is told, once, on the frame he asked. */
    @Test
    void andHeIsToldWhyRatherThanLeftGuessing() {
        var it = arena("Knight");
        pool(it.book(), 1, 0);

        assertEquals(0, it.book().getRefusedForManaFrame(), "nothing refused yet");
        it.book().cast('E', 1);

        assertTrue(it.book().getRefusedForManaFrame() > 0,
                "a refusal nobody is told about is a key that did not work");
    }

    /** A skill he can afford and one he cannot, side by side. */
    @Test
    void whatHeCanAffordIsAskedPerSkill() {
        var it = arena("Mage");
        int cheap = skillOf("Mage", 'Q').manaAt(1);
        int dear = skillOf("Mage", 'R').manaAt(1);
        pool(it.book(), cheap, 0);

        assertTrue(it.book().canAfford('Q', 1), "the cheapest is exactly affordable");
        assertFalse(it.book().canAfford('R', 1), "the ultimate is not");
        assertTrue(dear > cheap, "and the file should price them apart");
    }

    // ---- getting it back ----

    /**
     * The trickle comes out at the rate asked for, exactly, over a whole second.
     *
     * <p>The figure is in tenths and the frames are thirtieths, so the two only
     * agree if the carry is kept — which is the whole reason it exists. A float
     * would land near enough here and drift apart over a run.
     */
    @Test
    void aFullPoolHasNowhereToPutIt() {
        var it = arena("Mage");
        pool(it.book(), 40, 70);

        it.game().runHeadless(GameConstants.LOGICFRAMES_PER_SECOND);

        assertEquals(40, it.book().getMana(), "a full pool has nowhere to put it");
    }

    /** And an empty one fills at exactly the rate, second by second. */
    @Test
    void anEmptyPoolFillsAtExactlyTheRate() {
        var it = arena("Mage");
        it.book().poolOf(500, 70); // capacity only: a pool from nothing starts empty
        assertEquals(0, it.book().getMana(), "capacity is not contents");

        it.game().runHeadless(GameConstants.LOGICFRAMES_PER_SECOND);
        assertEquals(7, it.book().getMana(), "seven a second means seven in a second");

        it.game().runHeadless(GameConstants.LOGICFRAMES_PER_SECOND * 3);
        assertEquals(28, it.book().getMana(), "and twenty-eight in four");
    }

    /** A rate that does not divide the frame rate still comes out exact over time. */
    @Test
    void andARateThatDoesNotDivideEvenlyStillComesOutExact() {
        var it = arena("Mage");
        it.book().poolOf(500, 7); // 0.7 a second

        it.game().runHeadless(GameConstants.LOGICFRAMES_PER_SECOND * 10);

        assertEquals(7, it.book().getMana(),
                "seven tenths a second is seven points in ten seconds, not six or eight");
    }

    /** Nobody regenerates past the brim. */
    @Test
    void andItStopsAtTheBrim() {
        var it = arena("Mage");
        pool(it.book(), 20, 300);

        it.game().runHeadless(GameConstants.LOGICFRAMES_PER_SECOND * 5);

        assertEquals(20, it.book().getMana());
    }

    // ---- what a level is worth ----

    /** A bigger pool is a gift, not a refill: what was spent stays spent. */
    @Test
    void agrowingPoolIsAGiftRatherThanARefill() {
        var it = arena("Knight");
        pool(it.book(), 100, 0);
        assertTrue(it.book().cast('E', 1), "the guard should have gone up");
        int after = it.book().getMana();
        assertTrue(after < 100, "and it should have cost him something");

        it.book().poolOf(140, 0);

        assertEquals(after + 40, it.book().getMana(),
                "forty more to hold means forty more in hand, not a full purse");
        assertEquals(140, it.book().getMaxMana());
    }

    /** A hero whose file names no pool pays nothing, as the game worked before. */
    @Test
    void aHeroWithNoPoolCastsFree() {
        var it = arena("Knight");
        it.book().poolOf(0, 0);

        assertTrue(it.book().canAfford('E', 1), "no pool, no price");
        assertTrue(it.book().cast('E', 1), "and the cast goes through");
        assertEquals(0, it.book().getMana());
    }

    // ---- the file ----

    /** Every hero the file describes is given something to cast out of. */
    @Test
    void everyHeroHasAPoolAndEverySkillAPrice() {
        for (var hero : SETTINGS.heroes()) {
            assertTrue(hero.maxMana() > 0, hero.name() + " has no mana at all");
            assertTrue(hero.manaRegen() > 0, hero.name() + " never gets any back");
            for (var skill : SETTINGS.skillsFor(hero.name())) {
                assertTrue(skill.manaAt(1) > 0,
                        hero.name() + "'s " + skill.key() + " is free to cast");
            }
        }
    }

    /**
     * No hero can open with everything he has.
     *
     * <p>The one balance fact worth a test rather than a look. If all four fit
     * inside the pool then mana is a second cooldown and nothing else: he presses
     * all four and waits, which is what he did before any of this. It has to
     * force a choice at least once.
     */
    @Test
    void noHeroCanOpenWithAllFourAtOnce() {
        for (var hero : SETTINGS.heroes()) {
            int all = 0;
            for (var skill : SETTINGS.skillsFor(hero.name())) {
                all += skill.manaAt(1);
            }
            assertTrue(all > hero.maxMana(), hero.name() + " can cast all four for " + all
                    + " out of a pool of " + hero.maxMana() + ", so mana costs him no decision");
        }
    }

    /**
     * And nobody can sustain everything at once.
     *
     * <p>The figure that actually says what mana is worth, and the one the
     * obvious measurement gets wrong. Counting the frames on which he cannot
     * afford his cheapest reads as 80% for the rogue and 0% for the mage, which
     * says the rogue is starved and the mage is not -- when what it is really
     * measuring is that the rogue's cheapest is ready far more often.
     *
     * <p>What is comparable is the demand: every skill cast the instant it comes
     * back, in mana a second, against what he regains in a second. Every hero
     * sits near half, which is the sentence this whole thing is for -- mana
     * halves what he can do, and he chooses which half.
     */
    @Test
    void nobodyCanSustainEverythingAtOnce() {
        for (var hero : SETTINGS.heroes()) {
            float demand = 0f;
            for (var skill : SETTINGS.skillsFor(hero.name())) {
                demand += skill.manaAt(1)
                        / (skill.cooldownAt(1) / (float) GameConstants.LOGICFRAMES_PER_SECOND);
            }
            float regain = hero.manaRegen() / 10f;
            float share = regain / demand;
            assertTrue(share > 0.3f && share < 0.75f, hero.name() + " regains " + regain
                    + " a second against a demand of " + demand + " -- that is "
                    + Math.round(share * 100) + "% of everything at once, and the game wants"
                    + " something near half: much less is a hero who watches a bar, much more"
                    + " is a second cooldown");
        }
    }

    // ---- determinism ----

    /**
     * The same seed and the same keys come out at the same checksum.
     *
     * <p>Mana is counted inside the simulation and therefore inside the thing two
     * machines have to agree about. The danger is not the spending, which is
     * integers, but the trickle: a rate of tenths against frames of thirtieths is
     * exactly the arithmetic somebody reaches for a float to do, and a float
     * summed thirty times a second is two machines drifting apart until the frame
     * where one of them can afford a meteor and the other cannot.
     *
     * <p>Played twice from the same seed, casting on the same frames, and the two
     * walks compared. It would fail on the first float.
     */
    @Test
    void theSameRunCastsTheSameWayTwice() {
        assertEquals(walkCasting(), walkCasting(),
                "two runs of one seed disagreed once mana was counted");
    }

    private static String walkCasting() {
        var world = Dungeon.world(ARENA, SETTINGS);
        var game = world.game();
        game.spawn("Knight", world.hero(), 150f, 150f);
        for (int i = 0; i < 4; i++) {
            game.spawn("Skeleton", world.dungeon(), 190f + i * 25f, 150f);
        }
        game.runHeadless(1);
        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals("Knight"))
                .findFirst().orElseThrow();
        var book = hero.findModule(SkillBook.class);
        book.poolOf(60, 30);
        book.fillMana();

        var signature = new StringBuilder();
        for (int step = 0; step < 20; step++) {
            // Pressed every step whether or not he can pay, which is the point:
            // half of these are refusals and a refusal has to cost nothing.
            book.cast('E', 1);
            game.runHeadless(20);
            game.getSnapshot();
            signature.append(book.getMana()).append(':')
                    .append(game.getLogic().checksum()).append('|');
        }
        return signature.toString();
    }

    /**
     * And his cheapest is sustainable on its own cooldown.
     *
     * <p>The other end of the same question. A hero whose cheapest skill outruns
     * his trickle has nothing he can lean on, and what that feels like is
     * standing about waiting for a bar — which is the failure the brief named.
     */
    @Test
    void andHisCheapestIsSustainableOnItsOwnCooldown() {
        for (var hero : SETTINGS.heroes()) {
            Skill cheapest = null;
            for (var skill : SETTINGS.skillsFor(hero.name())) {
                if (cheapest == null || skill.manaAt(1) < cheapest.manaAt(1)) {
                    cheapest = skill;
                }
            }
            float seconds = cheapest.cooldownAt(1) / (float) GameConstants.LOGICFRAMES_PER_SECOND;
            float regained = seconds * hero.manaRegen() / 10f;
            assertTrue(regained >= cheapest.manaAt(1), hero.name() + "'s cheapest costs "
                    + cheapest.manaAt(1) + " and he regains " + regained + " over its "
                    + seconds + "s cooldown — he has nothing to lean on");
        }
    }
}
