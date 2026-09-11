package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.level.GrowableBody;
import uz.duke.dungeon.skill.SkillBook;
import uz.duke.game.DukeGame;

/**
 * Who the player is asked to be, and the rule that he is asked at all.
 *
 * <p>The rule is the feature: there is no way into the game that does not go
 * through the question. A choice with a way past it is a setting — the player
 * presses Play, gets whatever a file happened to say, and never finds out he had
 * one — so what is held here is not "the menu works" but "nothing starts without
 * it".
 *
 * <p>The world is built before the window opens, because a client cannot show a
 * menu over a game that does not exist. So the hero standing in it when the
 * question is asked is whoever {@code DefaultHero} names, and nobody ever sees
 * him: taking a row lays the first floor again with whoever was picked. These
 * tests are mostly about that second part being true.
 */
class HeroChoiceTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static final String ARCHER = "Hero";
    private static final String KNIGHT = "Knight";

    private static uz.duke.dungeon.run.DungeonRun runOf(Dungeon.Session session) {
        return session.run();
    }

    private static uz.duke.core.thing.GameObject find(DukeGame game, String template) {
        return game.getLogic().getObjects().stream()
                .filter(object -> object.getTemplate().getName().equals(template))
                .findFirst().orElse(null);
    }

    // ---- the question the game asks ----

    /**
     * Every hero in the file is on the roster, and nothing else is.
     *
     * <p>The roster is not a list somebody keeps in step: a third hero appears by
     * having a block, which is the same promise his skills and his portrait make.
     */
    @Test
    void everyHeroInTheFileIsOfferedAndNoOneElseIs() {
        var offered = SETTINGS.heroes().stream().map(hero -> hero.name()).toList();

        assertEquals(2, offered.size(), "the roster is " + offered);
        assertTrue(offered.contains(ARCHER));
        assertTrue(offered.contains(KNIGHT));
    }

    /** And each row says what taking it means, in the game's own words. */
    @Test
    void eachRowSaysWhatItMeans() {
        for (var hero : SETTINGS.heroes()) {
            assertFalse(hero.title().isBlank(),
                    hero.name() + " would be offered as a name and nothing else");
        }
        assertFalse(SETTINGS.hudChooseHeroWord().isBlank(),
                "the screen has no heading, so the client would have to write one");
        assertFalse(SETTINGS.hudChooseHeroHint().isBlank(),
                "the screen has no footer, so the client would have to write one");
    }

    // ---- and what taking a row does ----

    /**
     * Chosen before anything has started, which is where the menu actually asks.
     *
     * <p>The case every other test here quietly skipped, and it crashed on the
     * first press of a row: the tests below all run a frame first, so they had a
     * world to replace, and the real menu has none. The first floor is placed when
     * the <em>engine</em> starts, and the engine starts after the question is
     * answered — so at the moment a row is taken there is no simulation to clear,
     * no terrain to apply and nothing to spawn into.
     *
     * <p>The choice is therefore only recorded here, and the floor that is laid a
     * moment later is laid with him. What this asserts is the outcome rather than
     * the mechanism: press the row, start the game, and the knight is the one
     * standing in it.
     */
    @Test
    void chosenBeforeTheWorldExistsHeIsTheOneWhoArrives() {
        var session = Dungeon.newSession(21L, SETTINGS);

        // Not a frame has run: getLogic() is null, exactly as it is on the menu.
        runOf(session).startWith(session.game(), KNIGHT);
        session.game().runHeadless(1);

        assertNotNull(find(session.game(), KNIGHT),
                "the hero chosen before the world existed never arrived in it");
        assertNull(find(session.game(), ARCHER), "the file's own hero was laid down anyway");
        assertEquals(KNIGHT, runOf(session).getHeroTemplate());
    }

    /** And the run that follows is a real one, not a world with a man in it. */
    @Test
    void aRunChosenFromTheMenuGoesOn() {
        var session = Dungeon.newSession(21L, SETTINGS);

        runOf(session).startWith(session.game(), KNIGHT);
        session.game().runHeadless(120);

        var knight = find(session.game(), KNIGHT);
        assertNotNull(knight, "he did not survive four seconds of the floor he was chosen for");
        assertTrue(knight.getBody().getHealth() > 0f);
        assertNotNull(knight.findModule(SkillBook.class));
    }

    /** Changing your mind on the menu, still before anything has started. */
    @Test
    void choosingTwiceBeforeTheWorldExistsLeavesTheSecond() {
        var session = Dungeon.newSession(21L, SETTINGS);

        runOf(session).startWith(session.game(), KNIGHT);
        runOf(session).startWith(session.game(), ARCHER);
        session.game().runHeadless(1);

        assertNotNull(find(session.game(), ARCHER));
        assertNull(find(session.game(), KNIGHT));
    }

    /**
     * Taking the knight puts the knight in the dungeon.
     *
     * <p>Whoever the file named is gone, not standing somewhere off screen: a
     * choice that added a hero rather than replacing one would be two heroes and
     * one panel.
     */
    @Test
    void takingARowPutsThatHeroInTheDungeon() {
        var session = Dungeon.newSession(21L, SETTINGS);
        session.game().runHeadless(1);
        assertNotNull(find(session.game(), ARCHER), "the file's own hero should be there first");

        runOf(session).startWith(session.game(), KNIGHT);
        session.game().runHeadless(1);

        assertNotNull(find(session.game(), KNIGHT), "the knight was never spawned");
        assertNull(find(session.game(), ARCHER), "the archer is still in the dungeon");
        assertEquals(KNIGHT, runOf(session).getHeroTemplate());
    }

    /** And taking the one the file already named is not a special case. */
    @Test
    void takingTheFilesOwnHeroIsStillAChoice() {
        var session = Dungeon.newSession(21L, SETTINGS);
        session.game().runHeadless(1);

        runOf(session).startWith(session.game(), ARCHER);
        session.game().runHeadless(1);

        assertNotNull(find(session.game(), ARCHER));
        assertNull(find(session.game(), KNIGHT));
    }

    /**
     * He arrives with his own skills, not the last hero's.
     *
     * <p>The skills come off his creature block, so this is really asking whether
     * the swap replaced the whole hero or only the model — and the two are
     * indistinguishable until something casts.
     */
    @Test
    void heArrivesWithHisOwnSkills() {
        var session = Dungeon.newSession(21L, SETTINGS);
        session.game().runHeadless(1);

        runOf(session).startWith(session.game(), KNIGHT);
        session.game().runHeadless(1);

        var book = find(session.game(), KNIGHT).findModule(SkillBook.class);
        assertNotNull(book, "he arrived without a skill book");
        assertEquals(SETTINGS.skillsFor(KNIGHT), book.getSkills(),
                "he is carrying somebody else's four");
    }

    /**
     * And with his own plate, which is the half that is not on his template.
     *
     * <p>A hero's armour is written from his level and his loot every time either
     * changes, so it lives in his block rather than his creature — and a swap that
     * moved the template and left the armour behind would put a knight in a
     * shirt. Invisible everywhere except in how long he lives.
     */
    @Test
    void heArrivesWearingHisOwnPlate() {
        var session = Dungeon.newSession(21L, SETTINGS);
        session.game().runHeadless(1);
        runOf(session).startWith(session.game(), KNIGHT);
        session.game().runHeadless(1);

        var knight = find(session.game(), KNIGHT);
        float before = knight.getBody().getHealth();
        knight.getBody().damage(100f);
        float sufferedInPlate = before - knight.getBody().getHealth();

        assertTrue(sufferedInPlate < 100f,
                "a hundred points cost him " + sufferedInPlate + " — his plate never arrived");
    }

    /**
     * Choosing starts the run over rather than swapping a body in.
     *
     * <p>Levels, cards and loot were the last hero's, and none of them was his.
     * The same road a death takes, and for the same reason.
     */
    @Test
    void choosingStartsTheRunOverRatherThanSwappingABodyIn() {
        var session = Dungeon.newSession(21L, SETTINGS);
        session.game().runHeadless(1);
        // Give the file's hero something to lose.
        session.progress().getLoot().take(SETTINGS.loot().getFirst(), 0, 30);

        runOf(session).startWith(session.game(), KNIGHT);
        session.game().runHeadless(1);

        assertTrue(session.progress().getLoot().getFound().isEmpty(),
                "the knight inherited the archer's bag");
        assertEquals(1, session.progress().getLevel(), "he inherited the archer's levels");
    }

    /** He starts at full health, as anything beginning a run does. */
    @Test
    void heStartsAtFullHealth() {
        var session = Dungeon.newSession(21L, SETTINGS);
        session.game().runHeadless(1);

        runOf(session).startWith(session.game(), KNIGHT);
        session.game().runHeadless(1);

        var body = find(session.game(), KNIGHT).getBody();
        assertTrue(body instanceof GrowableBody);
        assertEquals(body.getMaxHealth(), body.getHealth(), 0.001f);
    }

    /** And the run is playable afterwards, not merely populated. */
    @Test
    void theRunGoesOnAfterTheChoice() {
        var session = Dungeon.newSession(21L, SETTINGS);
        session.game().runHeadless(1);

        runOf(session).startWith(session.game(), KNIGHT);
        session.game().runHeadless(120); // four seconds of a real floor

        var knight = find(session.game(), KNIGHT);
        assertNotNull(knight, "he did not survive being chosen");
        assertTrue(knight.getBody().getHealth() > 0f);
    }

    /**
     * Choosing twice is choosing, not stacking.
     *
     * <p>Somebody will press Back and pick the other one. The second choice has to
     * leave exactly the world the first would have left on its own.
     */
    @Test
    void choosingAgainLeavesOnlyTheSecondHero() {
        var session = Dungeon.newSession(21L, SETTINGS);
        session.game().runHeadless(1);

        runOf(session).startWith(session.game(), KNIGHT);
        session.game().runHeadless(1);
        runOf(session).startWith(session.game(), ARCHER);
        session.game().runHeadless(1);

        assertNull(find(session.game(), KNIGHT), "the knight is still standing about");
        assertNotNull(find(session.game(), ARCHER));
        assertEquals(ARCHER, runOf(session).getHeroTemplate());
    }

    // ---- the file's own answer, for everything that is never asked ----

    /**
     * A headless run still has a hero, because nothing asked it anything.
     *
     * <p>{@code DefaultHero} did not stop meaning something when a menu appeared —
     * it means the narrower thing now: who plays when nobody is there to choose.
     * Which is every test in this suite and every run without a window.
     */
    @Test
    void nobodyAskedMeansTheFilesOwnAnswer() {
        var session = Dungeon.newSession(21L, SETTINGS);
        session.game().runHeadless(1);

        assertEquals(SETTINGS.playedHero(), runOf(session).getHeroTemplate());
        assertNotNull(find(session.game(), SETTINGS.playedHero()));
    }

    /** A stage does not name a hero either, and cannot: the menu decides. */
    @Test
    void aStageDoesNotDecideWhoPlaysIt() {
        var session = Dungeon.newSession(21L, SETTINGS);
        session.game().runHeadless(1);

        // Whatever world it is, the hero is the run's and the run takes it from
        // the choice. Nothing about the floor has a say.
        runOf(session).startWith(session.game(), KNIGHT);
        session.game().runHeadless(1);

        assertEquals(KNIGHT, runOf(session).getHeroTemplate());
    }
}
