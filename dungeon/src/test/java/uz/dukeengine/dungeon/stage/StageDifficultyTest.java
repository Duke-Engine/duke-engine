package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.gen.Layout;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.game.DukeGame;

/**
 * A stage's difficulty is the depth it is fought at.
 *
 * <p>It used to be a label — a number the author typed and the panel showed, which
 * meant it could say anything and be wrong. Now it is the depth, so it has a
 * meaning anybody can go and check: difficulty seven is what the seventh floor of
 * the descent is, and every piece of arithmetic that makes a floor dangerous is
 * already written against depth and already tuned.
 *
 * <p>That is also what lets a stage be <em>harder than this game gets on its own</em>.
 * The descent stops at its last boss; a stage can be built below that, which is
 * most of the reason to build one.
 */
class StageDifficultyTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static Stage stageAt(long seed, int difficulty) {
        var floor = DungeonGenerator.generate(seed, SETTINGS, difficulty, Layout.of(SETTINGS));
        return new Stage("d" + difficulty, "Depth " + difficulty, "", difficulty, 1, seed, floor);
    }

    /** The toughest thing standing in the world, whoever it is. */
    private static float strongest(DukeGame game) {
        float most = 0f;
        for (var object : game.getLogic().getObjects()) {
            if (object.getBody() != null) {
                most = Math.max(most, object.getBody().getMaxHealth());
            }
        }
        return most;
    }

    private static DukeGame opened(Stage stage) {
        var game = Dungeon.createStage(stage, SETTINGS);
        game.runHeadless(1);
        return game;
    }

    /**
     * A stage built deep is fought deep — the monsters standing in it carry the
     * health of the floor its author picked, not of the first one.
     */
    @Test
    void aStageIsFoughtAtItsOwnDifficulty() {
        float shallow = strongest(opened(stageAt(99L, 1)));
        float deep = strongest(opened(stageAt(99L, 9)));

        assertTrue(deep > shallow * 1.5f,
                "a stage at depth 9 should be far harder than the same seed at 1: "
                        + shallow + " then " + deep);
    }

    /** And the run knows which floor it is on, rather than always calling it the first. */
    @Test
    void theRunOpensAtTheStagesOwnDepth() {
        var session = Dungeon.newStageSession(stageAt(5L, 7), SETTINGS);
        session.game().runHeadless(1);
        assertEquals(7, session.run().getDepth());
    }

    /**
     * Dying on a hard stage brings back a hard stage.
     *
     * <p>A run that ended used to reset to depth one, which is right for the
     * descent and would quietly turn the second attempt at a difficult stage into
     * an easy one — the worst possible failure for a level whose whole purpose is
     * being attempted twice.
     */
    @Test
    void dyingOnAHardStageComesBackJustAsHard() {
        var session = Dungeon.newStageSession(stageAt(21L, 8), SETTINGS);
        var game = session.game();
        game.runHeadless(1);
        float before = strongest(game);

        var hero = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(session.run().getHeroTemplate()))
                .findFirst().orElse(null);
        assertNotNull(hero);
        game.getLogic().destroyObject(hero);
        game.runHeadless(1 + SETTINGS.run().respawnDelayFrames() + 3);

        assertEquals(DungeonRun.State.RUNNING, session.run().getState());
        assertEquals(8, session.run().getDepth(), "the same stage means the same depth");
        assertEquals(before, strongest(game), 0.01f, "and the same monsters as before");
    }

    /** Killing its boss wins, at whatever depth it was built — there is no floor below. */
    @Test
    void killingTheBossOnADeepStageWins() {
        var stage = stageAt(31L, 9);
        var session = Dungeon.newStageSession(stage, SETTINGS);
        var game = session.game();
        game.runHeadless(1);

        var boss = game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(stage.floor().boss().kind()))
                .findFirst().orElse(null);
        assertNotNull(boss, "the stage's boss should be standing in it");
        game.getLogic().destroyObject(boss);
        game.runHeadless(2);

        assertEquals(DungeonRun.State.WON, session.run().getState(),
                "a stage has no floor under it, however deep it was built");
        assertEquals(9, session.run().getDepth(), "and nothing descended");
    }

    /** The panel says the one floor it is, rather than counting the descent's. */
    @Test
    void thePanelCountsTheStagesOwnDepth() {
        var game = opened(stageAt(5L, 6));
        game.runHeadless(1);
        assertTrue(game.getSnapshot().status().contains("depth=VI / VI"),
                game.getSnapshot().status());
    }

    /**
     * The descent still starts at the top.
     *
     * <p>The one thing all of this must not have changed. Depth is now asked of
     * {@code Floors} rather than assumed to be one, and the endless dungeon's
     * answer is one.
     */
    @Test
    void theDescentStillStartsAtTheTop() {
        var session = Dungeon.newSession(1234L, SETTINGS);
        session.game().runHeadless(1);
        assertEquals(1, session.run().getDepth());
    }

    /**
     * The large stage the game ships is actually large, and actually hard.
     *
     * <p>Pinned because it is the whole demonstration that a stage can be what a
     * generated floor never is. If somebody re-rolls it smaller or shallower by
     * accident, the file still loads and plays and nothing else would notice.
     */
    @Test
    void theLargeShippedStageIsLargeAndDeep() {
        var stage = Stages.load("deep", SETTINGS);
        var rows = stage.floor().asciiMap().strip().split("\n");

        assertTrue(rows.length > SETTINGS.mapHeight(),
                "taller than a floor of the descent: " + rows.length);
        assertTrue(rows[0].length() > SETTINGS.mapWidth(),
                "and wider: " + rows[0].length());
        assertTrue(stage.floor().rooms().size() > SETTINGS.maxRooms(),
                "with more rooms than the descent ever draws: " + stage.floor().rooms().size());
        assertTrue(stage.difficulty() > SETTINGS.finalDepth(),
                "and deeper than the descent itself goes: " + stage.difficulty());
        assertTrue(stage.floor().monsters().size() > 100,
                "and full: " + stage.floor().monsters().size() + " monsters");
    }

    /** A difficulty below one is not a depth, and the game says so rather than guessing. */
    @Test
    void aDifficultyBelowOneIsRefused() {
        var floor = DungeonGenerator.generate(2L, SETTINGS, 1, Layout.of(SETTINGS));
        var stage = new Stage("zero", "Zero", "", 0, 1, 2L, floor);
        assertTrue(StageCheck.problems(stage, SETTINGS).stream()
                        .anyMatch(p -> p.contains("not a depth")),
                StageCheck.problems(stage, SETTINGS).toString());
    }
}
