package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.rts.message.GameMessage;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.combat.message.CombatOrder;

/** The Unity-style promise, verified headlessly: a game in a few lines that runs. */
class DukeGameTest {

    @Test
    void starterGameBootsSpawnsAndSnapshots() {
        var game = DukeGame.create("test")
                .loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS)
                .map(30, 20);
        var you = game.addPlayer("You", Colour.BLUE);
        var foe = game.addPlayer("Foe", Colour.RED);
        game.enemies(you, foe);
        game.flavour(uz.dukeengine.rts.RtsFlavour.class).money(you::getIndex, 1000);
        game.spawn("Barracks", you, 50, 50)
                .spawn("Rifleman", you, 70, 50)
                .spawn("Tank", foe, 200, 150);

        game.runHeadless(10);

        assertEquals(3, game.getLogic().getObjectCount());
        assertEquals(1000, uz.dukeengine.rts.player.RtsPlayer.of(game.getLogic(), you.getIndex()).getMoney());

        var snapshot = game.getSnapshot();
        assertEquals(10, snapshot.frame());
        // Fog of war: the enemy tank at (200,150) is far outside vision range.
        assertEquals(2, snapshot.units().size());
        assertTrue(snapshot.units().stream().allMatch(u -> u.playerIndex() == you.getIndex()));
    }

    @Test
    void builtInCommandRoutingMovesUnits() {
        var game = DukeGame.create("test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        var you = game.addPlayer("You", Colour.BLUE);
        game.spawn("Rifleman", you, 0, 0);

        game.onStart(g -> g.postCommand(new CombatOrder.MoveTo(
                you.getIndex(), List.of(new ObjectId(1)), new Coord3D(100f, 0f, 0f))));
        game.runHeadless(60); // 2 seconds at speed 14 → ~28 units of progress

        var unit = game.getLogic().findObject(new ObjectId(1));
        assertNotNull(unit);
        assertTrue(unit.getPosition().x() > 20f, "built-in MoveTo routing should move the unit");
    }

    @Test
    void unitsOfOtherPlayersCannotBeCommanded() {
        var game = DukeGame.create("test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        var you = game.addPlayer("You", Colour.BLUE);
        var foe = game.addPlayer("Foe", Colour.RED);
        game.enemies(you, foe);
        game.spawn("Rifleman", foe, 0, 0); // id 1, owned by the enemy

        game.onStart(g -> g.postCommand(new CombatOrder.MoveTo(
                you.getIndex(), List.of(new ObjectId(1)), new Coord3D(100f, 0f, 0f))));
        game.runHeadless(30);

        assertEquals(0f, game.getLogic().findObject(new ObjectId(1)).getPosition().x(), 1e-4f,
                "you cannot order another player's units around");
    }

    @Test
    void everySecondsAndDefeatCallbacksFire() {
        var game = DukeGame.create("test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        var you = game.addPlayer("You", Colour.BLUE);
        var foe = game.addPlayer("Foe", Colour.RED);
        game.enemies(you, foe);
        game.spawn("Tank", you, 0, 0);
        game.spawn("Rifleman", foe, 20, 0); // within tank range 30 → auto-acquired

        var ticks = new AtomicInteger();
        var defeated = new AtomicReference<GamePlayer>();
        game.everySeconds(1, g -> ticks.incrementAndGet());
        game.onPlayerDefeated((g, p) -> defeated.set(p));

        game.runHeadless(121); // just past 4 seconds of game time

        assertEquals(4, ticks.get(), "everySeconds(1) fires once per game second");
        assertEquals(foe, defeated.get(), "the lone rifleman dies to the tank → foe annihilated");
    }

    @Test
    void unknownTemplateFailsWithHelpfulError() {
        var game = DukeGame.create("test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        game.addPlayer("You", Colour.BLUE);
        game.spawn("Battlecruiser", null, 0, 0);
        var error = assertThrows(IllegalArgumentException.class, () -> game.runHeadless(1));
        assertTrue(error.getMessage().contains("Battlecruiser"));
        assertTrue(error.getMessage().contains("loadUnits"));
    }

    @Test
    void configurationAfterStartIsRejected() {
        var game = DukeGame.create("test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        game.addPlayer("You", Colour.BLUE);
        game.runHeadless(1);
        assertThrows(IllegalStateException.class, () -> game.map(10, 10));
        assertThrows(IllegalStateException.class, () -> game.loadUnits("Object X\nEnd"));
    }

    /** A replay's controls: the pace of a booted game set and read, and one frame stepped while it is held. */
    @Test
    void theLogicRateAndOneStepAreTheGamesToAskOnceItIsBooted() throws InterruptedException {
        var game = DukeGame.create("test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        game.addPlayer("You", Colour.BLUE);
        assertThrows(IllegalStateException.class, () -> game.setLogicRate(2f), "a rate before there is an engine");
        assertThrows(IllegalStateException.class, game::stepOnce);

        var running = game.startEngineOnly();
        try {
            assertEquals(1f, game.getLogicRate());
            game.setLogicRate(2f);
            assertEquals(2f, game.getLogicRate());
            game.setLogicRate(100f);
            assertEquals(8f, game.getLogicRate(), "past the engine's fastest");

            game.setLogicRate(0f);
            Thread.sleep(150);
            int held = game.getLogic().getFrame();
            Thread.sleep(150);
            assertEquals(held, game.getLogic().getFrame(), "a world at a rate of 0 moved");
            game.stepOnce();
            long giveUp = System.nanoTime() + 2_000_000_000L;
            while (game.getLogic().getFrame() == held && System.nanoTime() < giveUp) {
                Thread.sleep(5);
            }
            Thread.sleep(150);
            assertEquals(held + 1, game.getLogic().getFrame(), "one step asked, not one frame stepped");
        } finally {
            game.stop();
            running.join(5000);
        }
    }

    /**
     * A save's moment: a task run between two frames — after a headless attempt's frame is whole, and on the engine's
     * own thread while the world is paused, where {@link #runOnSimThread}'s tasks wait for a frame and run inside it.
     */
    @Test
    void aTaskBetweenFramesRunsAfterAWholeFrameAndWhilePaused() throws InterruptedException {
        var game = DukeGame.create("test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        game.addPlayer("You", Colour.BLUE);
        assertThrows(IllegalStateException.class, () -> game.runBetweenFrames(() -> { }), "before there is an engine");
        var ticks = new java.util.concurrent.atomic.AtomicInteger();
        game.onTick(g -> ticks.incrementAndGet());
        game.runHeadless(3);
        var seen = new java.util.ArrayList<Integer>();
        game.runBetweenFrames(() -> seen.add(game.getLogic().getFrame() * 100 + ticks.get()));
        game.runHeadless(1);
        assertEquals(List.of(4 * 100 + 4), seen, "not after the attempt's whole frame");

        var paused = DukeGame.create("test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        paused.addPlayer("You", Colour.BLUE);
        var running = paused.startEngineOnly();
        try {
            paused.getLogic().setGamePaused(true);
            Thread.sleep(100);
            int held = paused.getLogic().getFrame();
            var ran = new java.util.concurrent.CountDownLatch(1);
            var thread = new String[1];
            var frame = new int[1];
            paused.runBetweenFrames(() -> {
                thread[0] = Thread.currentThread().getName();
                frame[0] = paused.getLogic().getFrame();
                ran.countDown();
            });
            assertTrue(ran.await(2, java.util.concurrent.TimeUnit.SECONDS), "a paused world never ran it");
            assertEquals("duke-sim", thread[0]);
            assertEquals(held, frame[0], "the paused world moved");
        } finally {
            paused.stop();
            running.join(5000);
        }
    }

    @Test
    void moveOrderOverridesCurrentTarget() {
        var game = DukeGame.create("test").loadUnits(uz.dukeengine.rts.RtsFlavour.STARTER_UNITS);
        var you = game.addPlayer("You", Colour.BLUE);
        var foe = game.addPlayer("Foe", Colour.RED);
        game.enemies(you, foe);
        game.spawn("Tank", you, 0, 0);        // id 1
        game.spawn("Barracks", foe, 25, 0);   // id 2, in range

        game.onStart(g -> g.postCommand(new CombatOrder.AttackObject(
                you.getIndex(), List.of(new ObjectId(1)), new ObjectId(2))));
        game.everySeconds(1, g -> g.postCommand(new CombatOrder.MoveTo(
                you.getIndex(), List.of(new ObjectId(1)), new Coord3D(0f, 100f, 0f))));
        game.runHeadless(40);

        var tank = game.getLogic().findObject(new ObjectId(1));
        assertTrue(tank.getPosition().y() > 1f, "explicit move order should take over from attacking");
        assertFalse(game.getSnapshot().units().isEmpty());
    }
}
