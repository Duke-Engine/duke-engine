package uz.duke.dungeon.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.GameConstants;
import uz.duke.core.math.Coord3D;
import uz.duke.core.module.MoveUpdate;
import uz.duke.core.thing.GameObject;
import uz.duke.dungeon.Dungeon;
import uz.duke.dungeon.content.Content;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.loot.Loot;
import uz.duke.dungeon.loot.LootBag;
import uz.duke.dungeon.loot.LootKind;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.game.DukeGame;
import uz.duke.rts.message.GameMessage;
import uz.duke.rts.module.ExperienceModule;

/**
 * The attributes in the world he is played in: in his body, his weapon, his legs and
 * his pool, and the same on every run.
 *
 * <p>Measured rather than read back wherever it can be — how far he walks in a second,
 * what his weapon is multiplied by, how much health comes back in two — because the
 * thing worth holding still is that the arithmetic reached the creature, not that the
 * arithmetic is right. {@code AttributesTest} holds that.
 */
class HeroAttributesTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();
    private static final int SECOND = GameConstants.LOGICFRAMES_PER_SECOND;

    private record Played(DukeGame game, HeroProgress progress, LootBag bag, String template) {

        GameObject body() {
            return find(game, template);
        }
    }

    private static Played play(String template) {
        return play(template, SETTINGS);
    }

    /** One hero alone in a room, with his progress ticking as a run's does. */
    private static Played play(String template, DungeonSettings settings) {
        var bag = new LootBag();
        var arena = Dungeon.world(room(60, 40), settings, Content.units(), bag);
        var game = arena.game();
        var progress = new HeroProgress(arena.hero(), settings.levelling(),
                settings.attributeRules(), settings.levelUpBannerFrames(), bag);
        progress.playing(settings.heroNamed(template));
        game.onTick(progress::tick);
        game.spawn(template, arena.hero(), 150f, 200f);
        game.runHeadless(2);
        assertNotNull(find(game, template), template + " was not spawned");
        return new Played(game, progress, bag, template);
    }

    // ---- built ----

    /** Every hero stands in the world as his first level, wherever he was spawned from. */
    @Test
    void everyHeroIsBuiltWithHisFirstLevelInHim() {
        for (var hero : new String[] {"Rogue", "Knight", "Mage"}) {
            var it = play(hero);
            var figures = it.progress().figuresOf(it.body(), HeroFigures.Found.NOTHING);

            assertEquals(figures.maxHealth(), it.body().getBody().getMaxHealth(), 0f,
                    hero + "'s strength is not in his body");
            assertEquals(figures.speed(), walkedInASecond(it), 0.01f,
                    hero + "'s agility is not in his legs");
            assertEquals(1f, bonusOf(it), 0f,
                    hero + "'s weapon was built with his first-level blow, so nothing is added to it");
        }
        // And those are the heroes the game has always had.
        assertEquals(550f, play("Rogue").body().getBody().getMaxHealth(), 0f);
        assertEquals(980f, play("Knight").body().getBody().getMaxHealth(), 0f);
        assertEquals(380f, play("Mage").body().getBody().getMaxHealth(), 0f);
    }

    // ---- a level ----

    /** A level grows all three, and all three reach the creature. */
    @Test
    void aLevelGrowsAllThreeAndEverythingTheyGive() {
        var it = play("Knight");
        var body = it.body();
        var before = it.progress().figuresOf(body, HeroFigures.Found.NOTHING);
        float walkedBefore = walkedInASecond(it);

        body.findModule(ExperienceModule.class).addExperience(SETTINGS.levelling().totalXpFor(4));
        it.game().runHeadless(1);

        assertEquals(4, it.progress().getLevel());
        var after = it.progress().figuresOf(body, HeroFigures.Found.NOTHING);
        assertEquals(SETTINGS.heroNamed("Knight").attributes().atLevel(4), after.attributes(),
                "three levels, in one step");
        for (int attribute = 0; attribute < SETTINGS.attributeRules().attributes().size(); attribute++) {
            assertTrue(after.attributes().at(attribute) > before.attributes().at(attribute),
                    SETTINGS.attributeRules().attributes().get(attribute).name() + " did not grow");
        }
        assertEquals(after.maxHealth(), body.getBody().getMaxHealth(), 0f, "strength reached his body");
        assertTrue(after.maxHealth() > before.maxHealth());
        assertEquals(after.attack() / before.attack(), bonusOf(it), 0f, "strength reached his sword");
        assertEquals(after.maxMana(), body.findModule(SkillBook.class).getMaxMana(),
                "intelligence reached his pool");
        float walkedAfter = walkedInASecond(it);
        assertEquals(after.speed(), walkedAfter, 0.01f, "agility reached his legs");
        assertTrue(walkedAfter > walkedBefore, "and he walks faster for it");
    }

    /** Two levels at once, or one at a time: the same hero to the bit. */
    @Test
    void levelsTakenTogetherOrOneAtATimeMakeTheSameHero() {
        var together = play("Mage");
        together.body().findModule(ExperienceModule.class)
                .addExperience(SETTINGS.levelling().totalXpFor(6));
        together.game().runHeadless(1);

        var stepwise = play("Mage");
        var experience = stepwise.body().findModule(ExperienceModule.class);
        for (int level = 2; level <= 6; level++) {
            experience.addExperience(SETTINGS.levelling().totalXpFor(level)
                    - SETTINGS.levelling().totalXpFor(level - 1));
            stepwise.game().runHeadless(1);
        }

        assertEquals(6, together.progress().getLevel());
        assertEquals(6, stepwise.progress().getLevel());
        assertEquals(Float.floatToIntBits(together.body().getBody().getMaxHealth()),
                Float.floatToIntBits(stepwise.body().getBody().getMaxHealth()), "health");
        assertEquals(Float.floatToIntBits(bonusOf(together)), Float.floatToIntBits(bonusOf(stepwise)),
                "weapon");
        assertEquals(together.body().findModule(SkillBook.class).getMaxMana(),
                stepwise.body().findModule(SkillBook.class).getMaxMana(), "pool");
        assertEquals(Float.floatToIntBits(walkedInASecond(together)),
                Float.floatToIntBits(walkedInASecond(stepwise)), "legs");
    }

    // ---- found ----

    /** A strength hero who picks up strength is paid twice: health and swing. */
    @Test
    void aKnightWhoFindsStrengthGetsHealthAndSwingBoth() {
        var it = play("Knight");
        float health = it.body().getBody().getMaxHealth();

        take(it, "STR", 5);

        assertEquals(health + 60f, it.body().getBody().getMaxHealth(), 0f, "five strength is sixty health");
        assertEquals(35f / 30f, bonusOf(it), 0f, "and five more on a swing of thirty");
    }

    /** An archer who picks up strength is paid once, and for agility twice. */
    @Test
    void anArcherWhoFindsStrengthGetsOnlyHealthAndForAgilityGetsBoth() {
        var it = play("Rogue");
        float health = it.body().getBody().getMaxHealth();

        take(it, "STR", 5);
        assertEquals(health + 60f, it.body().getBody().getMaxHealth(), 0f);
        assertEquals(1f, bonusOf(it), 0f, "strength is not his arrow");

        float walked = walkedInASecond(it);
        take(it, "AGI", 5);
        assertEquals(19f / 14f, bonusOf(it), 0f, "five more on an arrow of fourteen");
        assertEquals(walked + 0.75f, walkedInASecond(it), 0.01f, "and 0.75 on his legs");
        assertEquals(health + 60f, it.body().getBody().getMaxHealth(), 0f, "and no more health");
    }

    // ---- what comes back on its own ----

    /**
     * Health and mana come back at his own rate, and no attribute moves either.
     *
     * <p>Counted to the point over two whole seconds: a Knight mends two a second and
     * gets one mana back a second. Then he is given twenty strength and twenty
     * intelligence — a far bigger body and a far bigger pool — and it is still exactly
     * that.
     */
    @Test
    void whatComesBackOnItsOwnIsHisAndNoAttributeMovesIt() {
        var it = play("Knight");
        var knight = SETTINGS.heroNamed("Knight");
        var body = it.body();
        var book = body.findModule(SkillBook.class);
        int health = 2 * knight.healthRegen() / 10;
        int mana = 2 * knight.manaRegen() / 10;

        assertEquals(health, mendedOverTwoSeconds(it), "his health, before");
        assertTrue(book.cast('E', 1), "the guard should have gone up, and cost him");
        assertEquals(mana, regainedOverTwoSeconds(it), "his mana, before");

        take(it, "STR", 20);
        take(it, "INT", 20);
        assertTrue(book.getMana() < book.getMaxMana(), "the pool must still have room to fill");

        assertEquals(health, mendedOverTwoSeconds(it), "strength gave him a body, not a faster mend");
        assertEquals(mana, regainedOverTwoSeconds(it), "intelligence gave him a pool, not a trickle");
    }

    // ---- his legs ----

    /** New legs are still walking where the old ones were sent. */
    @Test
    void fasterLegsStillGoWhereHeWasSent() {
        var it = play("Rogue");
        var body = it.body();
        var goal = new Coord3D(450f, 200f, 0f);
        it.game().postCommand(new GameMessage.MoveTo(it.game().getLocalPlayerIndex(),
                List.of(body.getId()), goal));
        it.game().runHeadless(5);
        var legs = body.findModule(MoveUpdate.class);
        assertTrue(legs.isMoving(), "he should be on his way");

        take(it, "AGI", 10);

        var fresh = body.findModule(MoveUpdate.class);
        assertNotSame(legs, fresh, "faster legs are new legs");
        assertTrue(fresh.isMoving(), "and they are still walking");
        assertEquals(goal, fresh.getGoal(), "to the same place");
        it.game().runHeadless(15 * SECOND);
        assertTrue(body.getPosition().distance(goal) < 1f, "and they get him there");
    }

    // ---- the file ----

    /** A number changed in the file is a different hero standing in the world. */
    @Test
    void theFileIsWhatDecidesTheHeroInTheWorld() {
        float shipped = play("Knight").body().getBody().getMaxHealth();

        var stronger = DungeonSettings.parse(Content.world(), Content.data().replace("    STR = [22, 3.0]",
                "    STR = [30, 3.0]"));
        assertEquals(shipped + 8 * 12, play("Knight", stronger).body().getBody().getMaxHealth(), 0f,
                "eight more strength is ninety-six more health");

        var richer = DungeonSettings.parse(Content.world().replace("  HealthPerPoint = 12",
                "  HealthPerPoint = 20"), Content.data());
        assertEquals(shipped + 22 * 8, play("Knight", richer).body().getBody().getMaxHealth(), 0f,
                "eight more a point, twenty-two times");
    }

    // ---- the same every time ----

    /**
     * One seed and the same orders come out at the same checksum, twice.
     *
     * <p>A run with everything the attributes can do in it: levels taken, all three kinds
     * picked up, legs swapped while he walks, a fight going on around it — and the
     * world, his body, his pool, his level and his weapon compared at every step.
     */
    @Test
    void oneSeedAndTheSameOrdersComeOutTheSameTwice() {
        var first = playedOut();
        assertEquals(first, playedOut(), "two runs of one seed disagreed once attributes were counted");
        assertTrue(first.contains("L5"), "the run never levelled him, so it tested nothing: " + first);
    }

    private static String playedOut() {
        var session = Dungeon.newSession(20260914L);
        var game = session.game();
        var signature = new StringBuilder();
        for (int step = 0; step < 90; step++) {
            game.runHeadless(10);
            var hero = find(game, SETTINGS.playedHero());
            if (hero == null) {
                signature.append("gone|");
                continue;
            }
            int frame = game.getLogic().getFrame();
            switch (step) {
                case 5 -> hero.findModule(ExperienceModule.class)
                        .addExperience(SETTINGS.levelling().totalXpFor(5));
                case 12 -> session.progress().getLoot().take(item("STR", 4), frame, 30);
                case 20 -> session.progress().getLoot().take(item("AGI", 6), frame, 30);
                case 28 -> session.progress().getLoot().take(item("INT", 5), frame, 30);
                default -> {
                }
            }
            if (step % 9 == 4) {
                game.postCommand(new GameMessage.MoveTo(game.getLocalPlayerIndex(),
                        List.of(hero.getId()), new Coord3D(hero.getPosition().x() + 40f,
                                hero.getPosition().y(), 0f)));
            } else if (step % 6 == 0) {
                var prey = nearestMonster(game, hero);
                if (prey != null) {
                    game.postCommand(new GameMessage.AttackObject(game.getLocalPlayerIndex(),
                            List.of(hero.getId()), prey.getId()));
                }
            }
            var book = hero.findModule(SkillBook.class);
            var player = game.getLogic().getRtsPlayer(game.getLocalPlayerIndex());
            signature.append(frame).append(':').append(game.getLogic().checksum())
                    .append(":H").append(Float.floatToIntBits(hero.getBody().getMaxHealth()))
                    .append(":M").append(book.getMana()).append('/').append(book.getMaxMana())
                    .append(":L").append(session.progress().getLevel())
                    .append(":W").append(Float.floatToIntBits(player.getWeaponDamageBonus()))
                    .append('|');
        }
        return signature.toString();
    }

    // ---- helpers ----

    /** How far he walks in one second of a straight order east: his speed, measured. */
    private static float walkedInASecond(Played it) {
        var body = it.body();
        var from = body.getPosition();
        it.game().postCommand(new GameMessage.MoveTo(it.game().getLocalPlayerIndex(),
                List.of(body.getId()), new Coord3D(from.x() + 250f, from.y(), from.z())));
        it.game().runHeadless(3);
        float start = body.getPosition().x();
        it.game().runHeadless(SECOND);
        return body.getPosition().x() - start;
    }

    private static int mendedOverTwoSeconds(Played it) {
        var body = it.body().getBody();
        body.damage(300f);
        float wounded = body.getHealth();
        it.game().runHeadless(2 * SECOND);
        return Math.round(body.getHealth() - wounded);
    }

    private static int regainedOverTwoSeconds(Played it) {
        var book = it.body().findModule(SkillBook.class);
        int was = book.getMana();
        it.game().runHeadless(2 * SECOND);
        return book.getMana() - was;
    }

    private static float bonusOf(Played it) {
        return it.game().getLogic().getRtsPlayer(it.game().getLocalPlayerIndex())
                .getWeaponDamageBonus();
    }

    private static void take(Played it, String attribute, int value) {
        it.bag().take(item(attribute, value), it.game().getLogic().getFrame(), 30);
        it.game().runHeadless(1);
    }

    /** Whole points of the attribute the file calls {@code attribute}. */
    private static Loot item(String attribute, int value) {
        return new Loot(attribute, attribute, "", LootKind.ATTRIBUTE, value, 1, 1, attribute);
    }

    private static GameObject nearestMonster(DukeGame game, GameObject hero) {
        GameObject nearest = null;
        float best = Float.MAX_VALUE;
        for (var object : game.getLogic().getObjects()) {
            if (object.getPlayerIndex() == hero.getPlayerIndex() || object.getBody() == null) {
                continue;
            }
            float distance = hero.getPosition().distance(object.getPosition());
            if (distance < best) {
                best = distance;
                nearest = object;
            }
        }
        return nearest;
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
