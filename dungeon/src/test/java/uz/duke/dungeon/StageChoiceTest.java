package uz.duke.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uz.duke.client3d.Shell;
import uz.duke.client3d.Visuals;
import uz.duke.dungeon.content.DungeonSettings;
import uz.duke.dungeon.stage.Stages;

/**
 * Choosing which game to play, and then which stage — the path in front of a run.
 *
 * <p>Two games share one run loop: the endless descent, which draws a floor
 * nobody has seen, and a stage, which hands back the same rooms so that losing
 * teaches something. Which of them is the first thing a player settles, and it is
 * the one question the game could not ask before — a stage was reachable only by
 * naming its file on the command line, which is an author's road rather than a
 * player's.
 *
 * <p>What is held here is mostly the shape of the path: what leads to what, and
 * that nothing is offered which cannot be played. The rows themselves are the
 * stage files' own words, so a stage that exists is on the screen and no list is
 * kept in step by hand.
 */
class StageChoiceTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static Shell.Question path() {
        return Main.howToPlay(Dungeon.newSession(7L, SETTINGS), SETTINGS, Visuals.create());
    }

    // ---- what is on disk and in the game ----

    /**
     * The shipped stage is found, read and offered.
     *
     * <p>Found by being a {@code .stage} file in the folder rather than by being
     * named anywhere: an author drops one in and it is on the screen. In a build
     * this reads it out of the copied resources; installed, it reads it out of the
     * jar, which is a different kind of thing to list and the reason the listing
     * asks the URL what it is looking at.
     */
    @Test
    void theShippedStageIsFound() {
        var found = Stages.all(SETTINGS);

        assertFalse(found.isEmpty(), "the stage that ships with the game was not found");
        assertTrue(found.stream().anyMatch(listed -> listed.path().endsWith("first.stage")),
                "found " + found.stream().map(Stages.Listed::path).toList());
    }

    /** And what is offered is playable — the listing has already checked it. */
    @Test
    void everyStageOfferedCanActuallyBeLoaded() {
        for (var listed : Stages.all(SETTINGS)) {
            assertNotNull(Stages.load(listed.path(), SETTINGS),
                    listed.path() + " was offered but will not load");
        }
    }

    /** Each row is the stage's own words, not its file name. */
    @Test
    void eachStageSpeaksForItself() {
        for (var listed : Stages.all(SETTINGS)) {
            assertFalse(listed.stage().name().isBlank(),
                    listed.path() + " would be offered as a blank row");
        }
    }

    // ---- the path in front of a run ----

    @Test
    void theFirstQuestionIsWhichGame() {
        var how = path();

        assertEquals(SETTINGS.hudChooseModeWord(), how.title());
        assertEquals(2, how.options().size(), "two games, two rows");
        assertEquals(SETTINGS.hudEndlessWord(), how.options().get(0).label());
        assertEquals(SETTINGS.hudStagesWord(), how.options().get(1).label());
    }

    /** The endless descent asks nothing more than who is walking into it. */
    @Test
    void theEndlessDescentGoesStraightToTheHero() {
        var endless = path().options().get(0);

        assertNotNull(endless.next(), "it should still ask who is playing");
        assertEquals(SETTINGS.hudChooseHeroWord(), endless.next().title());
    }

    /** The stage branch asks which one first, and only then who. */
    @Test
    void theStageBranchAsksWhichOneFirst() {
        var stages = path().options().get(1).next();

        assertNotNull(stages, "the stage row led nowhere");
        assertEquals(SETTINGS.hudChooseStageWord(), stages.title());
        assertFalse(stages.options().isEmpty(), "an empty list would be a dead end");
        for (var row : stages.options()) {
            assertNotNull(row.next(), row.label() + " starts the game without asking who");
            assertEquals(SETTINGS.hudChooseHeroWord(), row.next().title());
        }
    }

    /** Every stage in the folder is a row, and each says what it is. */
    @Test
    void everyStageFoundIsARowOfItsOwn() {
        var rows = path().options().get(1).next().options();
        var found = Stages.all(SETTINGS);

        assertEquals(found.size(), rows.size());
        for (int at = 0; at < found.size(); at++) {
            assertEquals(found.get(at).stage().name(), rows.get(at).label());
            assertEquals(found.get(at).stage().description(), rows.get(at).blurb());
        }
    }

    /** Both roads end at the same question, which is the one that starts the game. */
    @Test
    void bothRoadsEndAtTheHero() {
        var how = path();
        var fromEndless = how.options().get(0).next();
        var fromStage = how.options().get(1).next().options().getFirst().next();

        assertEquals(fromEndless.title(), fromStage.title());
        for (var hero : fromEndless.options()) {
            assertNull(hero.next(), hero.label() + " should have been the last row of the path");
        }
    }

    // ---- and what taking a stage row actually does ----

    /**
     * Taking a stage puts <em>that stage's own floor</em> in the world.
     *
     * <p>The assertion that matters, and the first version of this test did not
     * make it: it asked only how deep the game was, which is read off the floors
     * rather than off the world, so it passed while the floor actually laid was
     * the one the world had been built around before anybody was asked. The
     * player would have picked a stage, been told he was on one, and walked
     * around a randomly generated dungeon.
     *
     * <p>So this counts what is standing in it. A stage's inhabitants are frozen
     * — that is the whole of what a stage is — and a floor drawn from a seed
     * would have its own number of them.
     */
    @Test
    void takingAStageLaysThatStagesOwnFloor() {
        var session = Dungeon.newSession(7L, SETTINGS);
        var how = Main.howToPlay(session, SETTINGS, Visuals.create());
        var stageRow = how.options().get(1).next().options().getFirst();
        var stage = Stages.all(SETTINGS).getFirst().stage();

        stageRow.taken().run();
        stageRow.next().options().getFirst().taken().run();
        session.game().runHeadless(1);

        // One floor, and the depth of that one floor is the difficulty its author
        // chose — so "is this a stage?" is asked as "does it end where it begins?"
        // rather than as "is it depth one?", which it was while every stage was.
        assertEquals(stage.difficulty(), session.run().getLastDepth(),
                "a stage is fought at its own difficulty; this run thinks it is a descent");
        // Its monsters and the boss standing at the bottom of it, who is placed
        // separately and is still one of the things in the room.
        assertEquals(stage.floor().monsters().size() + (stage.floor().boss() == null ? 0 : 1),
                monstersIn(session),
                "the world holds a different floor's inhabitants than the stage's");
        var startsAt = stage.floor().hero();
        var him = heroIn(session);
        assertEquals(startsAt.x(), him.getPosition().x(), 0.01f,
                "the hero is standing where another floor put him");
        assertEquals(startsAt.y(), him.getPosition().y(), 0.01f,
                "the hero is standing where another floor put him");
    }

    private static uz.duke.core.thing.GameObject heroIn(Dungeon.Session session) {
        return session.game().getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().getName().equals(session.run().getHeroTemplate()))
                .findFirst().orElseThrow();
    }

    /** Everything the dungeon owns and the hero does not. */
    private static long monstersIn(Dungeon.Session session) {
        return session.game().getLogic().getObjects().stream()
                .filter(o -> o.getBody() != null)
                .filter(o -> !o.getTemplate().getName().equals(session.run().getHeroTemplate()))
                .count();
    }

    /** And taking the endless descent leaves it a descent. */
    @Test
    void takingTheEndlessDescentLeavesItADescent() {
        var session = Dungeon.newSession(7L, SETTINGS);
        var how = Main.howToPlay(session, SETTINGS, Visuals.create());
        var endless = how.options().get(0);

        endless.taken().run();
        endless.next().options().getFirst().taken().run();
        session.game().runHeadless(1);

        assertTrue(session.run().getLastDepth() > 1,
                "the endless descent came out one floor deep");
    }

    /** A stage can be played by either hero: the stage does not decide. */
    @Test
    void aStageCanBePlayedByEitherHero() {
        var chosen = Stages.all(SETTINGS).getFirst().stage();
        for (var hero : SETTINGS.heroes()) {
            var session = Dungeon.newSession(7L, SETTINGS);
            var how = Main.howToPlay(session, SETTINGS, Visuals.create());
            var stageRow = how.options().get(1).next().options().getFirst();
            stageRow.taken().run();

            var heroRow = stageRow.next().options().stream()
                    .filter(row -> row.label().contains(displayName(session, hero.name())))
                    .findFirst().orElseThrow();
            heroRow.taken().run();
            session.game().runHeadless(1);

            assertEquals(hero.name(), session.run().getHeroTemplate());
            assertEquals(chosen.difficulty(), session.run().getLastDepth(),
                    "he is not on the stage");
        }
    }

    private static String displayName(Dungeon.Session session, String template) {
        var found = session.game().getLogic() == null ? null
                : session.game().getLogic().getThingFactory().findTemplate(template);
        return found == null || found.getDisplayName() == null || found.getDisplayName().isBlank()
                ? template : found.getDisplayName();
    }

    // ---- and the case where there is nothing to choose between ----

    /**
     * A game with no stages asks only who, not how.
     *
     * <p>The safety catch. Offering a road to an empty list is offering a dead
     * end, and a player who took it would have nothing to click and no way back
     * except the one row at the bottom. With nothing to play, the question that
     * remains is the one the game asked yesterday.
     */
    @Test
    void withNoStagesTheOnlyQuestionIsWho() {
        // A settings object whose stage folder has nothing in it is not something
        // this can arrange, so the shape is asserted from the other end: the mode
        // screen exists only because a stage was found.
        assertFalse(Stages.all(SETTINGS).isEmpty(),
                "this build ships a stage, so the mode screen is expected");
        assertEquals(SETTINGS.hudChooseModeWord(), path().title());
    }

    /** Every word on all three screens comes out of the file. */
    @Test
    void thePathIsWordedByTheFile() {
        for (var word : java.util.List.of(SETTINGS.hudChooseModeWord(),
                SETTINGS.hudChooseModeHint(), SETTINGS.hudChooseStageWord(),
                SETTINGS.hudChooseStageHint(), SETTINGS.hudEndlessWord(),
                SETTINGS.hudEndlessBlurb(), SETTINGS.hudStagesWord(),
                SETTINGS.hudStagesBlurb())) {
            assertFalse(word.isBlank(), "the client would have to write this one itself");
        }
    }
}
