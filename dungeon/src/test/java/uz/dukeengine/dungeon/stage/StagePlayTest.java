package uz.dukeengine.dungeon.stage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.thing.GameObject;
import uz.dukeengine.dungeon.Dungeon;
import uz.dukeengine.dungeon.content.DungeonSettings;
import uz.dukeengine.dungeon.gen.DungeonGenerator;
import uz.dukeengine.dungeon.run.DungeonRun;
import uz.dukeengine.game.DukeGame;

/**
 * A frozen dungeon, played.
 *
 * <p>Three promises, and they are the whole of what a stage is for. It plays
 * exactly like the floor it was cut from; dying puts the player back at the top
 * of the <em>same</em> floor rather than somewhere new; and killing the boss on it
 * wins, because there is nothing underneath.
 *
 * <p>The first is held to a checksum rather than to a list of things that look
 * right. The engine's checksum is what the network uses to prove two machines are
 * playing the same game — so a stage session and a generated session agreeing on
 * it after a hundred frames is the same proof, turned on a file instead of a peer.
 */
class StagePlayTest {

    private static final DungeonSettings SETTINGS = DungeonSettings.load();

    private static Stage frozen(long seed) {
        var stage = new Stage("test", "Test", "", 1, 1, seed,
                DungeonGenerator.generate(seed, SETTINGS, 1));
        // Through the file rather than straight from the generator: the claim is
        // about what survives being written down, not about what is in memory.
        return StageFile.read(StageFile.write(stage), "test");
    }

    private static GameObject heroOf(DukeGame game) {
        return game.getLogic().getObjects().stream()
                .filter(o -> o.getTemplate().name().equals(SETTINGS.run().defaultHero()))
                .findFirst()
                .orElse(null);
    }

    private static List<String> creatures(DukeGame game) {
        var standing = new ArrayList<String>();
        for (var object : game.getLogic().getObjects()) {
            standing.add(object.getTemplate().name() + " at "
                    + Math.round(object.getPosition().x()) + ","
                    + Math.round(object.getPosition().y()));
        }
        standing.sort(null); // spawn order is not the claim; what is standing where is
        return standing;
    }

    @Test
    void aStagePlaysExactlyLikeTheDungeonItWasCutFrom() {
        long seed = 4242L;
        var generated = Dungeon.newSession(seed, SETTINGS).game();
        var staged = Dungeon.newStageSession(frozen(seed), SETTINGS).game();

        generated.runHeadless(100);
        staged.runHeadless(100);

        assertEquals(creatures(generated), creatures(staged),
                "the same creatures should be standing in the same places");
        assertEquals(generated.getLogic().checksum(), staged.getLogic().checksum(),
                "a hundred frames in, the two worlds should be one world");
    }

    /** And the stage keeps its name, so the player knows which one he opened. */
    @Test
    void theStageSaysWhatItIs() {
        var stage = new Stage("first", "The First Descent", "a short walk", 2, 1, 1L,
                DungeonGenerator.generate(1L, SETTINGS, 1));
        var game = Dungeon.createStage(stage, SETTINGS);
        assertTrue(game.getSubtitle().contains("The First Descent"), game.getSubtitle());
        assertTrue(game.getSubtitle().contains("a short walk"), game.getSubtitle());
    }

    /**
     * Dying begins the same stage again — not a new dungeon.
     *
     * <p>The difference the whole mode rests on. In the endless descent a death
     * draws a fresh floor from the next seed, which is the right answer there and
     * exactly the wrong one here: a stage is something you are meant to learn, and
     * you cannot learn a room that moves every time you lose.
     */
    @Test
    void dyingPutsHimBackOnTheSameFloor() {
        var session = Dungeon.newStageSession(frozen(77L), SETTINGS);
        var game = session.game();

        game.runHeadless(1);
        var before = creatures(game);
        var hero = heroOf(game);
        assertNotNull(hero);

        game.getLogic().destroyObject(hero);
        game.runHeadless(1);
        assertEquals(DungeonRun.State.DEAD, session.run().getState());

        game.runHeadless(SETTINGS.run().respawnDelayFrames() + 3);
        assertEquals(DungeonRun.State.RUNNING, session.run().getState(), "a fresh attempt");
        assertEquals(1, session.run().getDepth(), "and it is the same one floor");
        assertEquals(before, creatures(game),
                "the same stage means the same monsters back in the same corners");
    }

    /**
     * Killing the boss wins, rather than opening a floor that is not there.
     *
     * <p>In the descent the boss is a door: it dies, a banner shows the next depth,
     * and the world is rebuilt one floor lower. A stage has no floor below it, so
     * the same death has to mean the opposite thing.
     */
    @Test
    void killingTheBossWinsTheStage() {
        var stage = frozen(31L);
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
                "the boss of a stage is the bottom of the game, not a door down");
        assertEquals(1, session.run().getDepth(), "and nothing descended");
    }

    /**
     * The stage the game ships with is one the game will actually play.
     *
     * <p>Loaded the way a player's copy loads it — off the classpath, through the
     * same check — so a stage that stopped being valid because the creature file
     * was re-tuned fails here rather than in front of somebody who installed it.
     * Rebuild it with {@code ./gradlew :dungeon:writeExampleMaps}.
     */
    @Test
    void theShippedStageCanBePlayed() {
        var stage = Stages.load("first", SETTINGS);
        var game = Dungeon.createStage(stage, SETTINGS);

        game.runHeadless(60);

        assertNotNull(heroOf(game), "the hero should be standing in the stage he was put in");
        assertTrue(game.getLogic().getObjects().size() > 5,
                "and so should everything the file puts around him");
    }

    /**
     * And so can every other one the game offers.
     *
     * <p>Asked through the same listing the menu is built from, so a stage that
     * ships but cannot be played fails here rather than being quietly dropped off
     * the menu in front of a player. There is more than one now, and the second
     * exists to be unlike the first — much larger, and much deeper.
     */
    @Test
    void everyShippedStageCanBePlayed() {
        var offered = Stages.all();
        assertTrue(offered.size() >= 2,
                "the game should ship more than one stage, and offers " + offered.size());
        for (var listed : offered) {
            var game = Dungeon.createStage(Stages.load(listed.map(), SETTINGS), SETTINGS);
            game.runHeadless(30);
            assertNotNull(heroOf(game), listed.name() + " put no hero in the world");
        }
    }

    /** Nothing named is the endless dungeon, which is what this game is by default. */
    @Test
    void namingNoStageIsTheEndlessDungeon() {
        assertEquals(null, Stages.chosen(new String[0]),
                "the shipped game must not quietly turn the descent into one stage");
    }

    /** And the command line wins, which is what an author uses while building one. */
    @Test
    void theCommandLineBeatsTheGameFile() {
        assertEquals("first", Stages.chosen(new String[] {"--map=first"}));
    }

    /** The panel says one floor of one, rather than promising nine that do not exist. */
    @Test
    void thePanelCountsTheStagesOwnFloors() {
        var session = Dungeon.newStageSession(frozen(5L), SETTINGS);
        var game = session.game();
        game.runHeadless(2);
        assertTrue(game.getSnapshot().status().contains("depth=I / I"),
                "the stage is one floor deep: " + game.getSnapshot().status());
    }
}
