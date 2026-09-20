package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.message.Command;
import uz.dukeengine.core.replay.FrameLog;

/**
 * A game may declare commands of its own, and they travel the same road as the
 * standard orders.
 *
 * <p>{@link Command} has always said a game declares its own command set, but the
 * RTS library then dropped anything that was not one of <em>its</em> orders — so
 * no game built on it could actually have one. A roguelike's "cast the third
 * ability" is not an RTS order and never will be, yet it has to be queued from
 * the input thread, applied at the start of a frame, and written to the replay
 * log, because that pipeline is what makes input deterministic at all. Reaching
 * around it would have worked, and would have quietly cost all three.
 */
class GameCommandTest {

    /** A game's own command. Deterministic data, like every command must be. */
    private record Shout(int playerIndex, String word) implements Command {
    }

    private static DukeGame game() {
        var game = DukeGame.create("game-command-test")
                .loadUnits(DukeGame.STARTER_UNITS)
                .map(40, 30);
        var you = game.addPlayer("You", Color.BLUE);
        game.localPlayer(you);
        return game;
    }

    /** A game command posted from outside reaches the game's handler. */
    @Test
    void aCommandTheGameInventedIsDelivered() {
        var heard = new ArrayList<String>();
        var game = game().onCommand(command -> {
            if (command instanceof Shout shout) {
                heard.add(shout.word());
            }
        });
        game.runHeadless(1);

        game.postCommand(new Shout(0, "hello"));
        game.runHeadless(5);

        assertEquals(List.of("hello"), heard);
    }

    /**
     * And not before a frame runs. Input is applied on a frame boundary, on the
     * simulation thread — the property that makes two peers agree and a replay
     * reproduce. A command that took effect the moment it was posted would be
     * running on whichever thread happened to post it.
     */
    @Test
    void itIsAppliedInsideAFrameRatherThanWhenItIsPosted() {
        var heard = new ArrayList<Integer>();
        var game = game();
        game.onCommand(command -> heard.add(game.getLogic().getFrame()));
        game.runHeadless(3);
        int postedAt = game.getLogic().getFrame();

        game.postCommand(new Shout(0, "now"));

        assertTrue(heard.isEmpty(), "posting is not applying");
        game.runHeadless(5);
        assertEquals(1, heard.size(), "it was applied exactly once");
        assertTrue(heard.get(0) > postedAt,
                "and on a later frame, not the one it was posted during");
    }

    /**
     * It is in the same stream the replay is made of.
     *
     * <p>This is the whole point of routing it through commands rather than
     * running it directly: the frame log sees it, so a recording holds it, so the
     * run can be played back. A side channel would have left a replay that
     * silently omits every ability the player used.
     */
    @Test
    void itIsRecordedAsInputForTheFrameItLandsOn() {
        var logged = new ArrayList<Command>();
        var game = game().onCommand(command -> { });
        game.runHeadless(1);
        game.getLogic().setFrameLog(new FrameLog() {
            @Override
            public void commands(int frame, List<Command> commands) {
                logged.addAll(commands);
            }

            @Override
            public boolean wantsCheckpoint(int frame) {
                return false;
            }

            @Override
            public void checkpoint(int frame, long checksum) {
            }
        });

        game.postCommand(new Shout(0, "recorded"));
        game.runHeadless(5);

        assertEquals(List.of(new Shout(0, "recorded")), logged,
                "the game's own command belongs in the recording like any other");
    }

    /**
     * A game that never declared a command set is untouched: a foreign command is
     * still ignored, and the simulation carries on.
     */
    @Test
    void withoutAHandlerNothingChanges() {
        var game = game();
        game.runHeadless(1);
        long before = game.getLogic().checksum();

        game.postCommand(new Shout(0, "into the void"));
        game.runHeadless(5);

        assertEquals(before, game.getLogic().checksum(), "an ignored command changes nothing");
    }

    /**
     * The same commands on the same frames give the same world — the property the
     * whole pipeline exists for, now holding for a command the engine never heard
     * of.
     */
    @Test
    void theSameCommandsGiveTheSameWorld() {
        assertEquals(playedOut(true), playedOut(true));
        assertNotEquals(playedOut(true), playedOut(false),
                "and the commands really did do something");
    }

    /** Spawn a rifleman per shout, so the command has a visible effect on the world. */
    private static long playedOut(boolean shout) {
        var game = DukeGame.create("game-command-test")
                .loadUnits(DukeGame.STARTER_UNITS)
                .map(40, 30);
        var you = game.addPlayer("You", Color.BLUE);
        game.localPlayer(you);
        game.onCommand(command -> {
            if (command instanceof Shout call) {
                game.spawn("Rifleman", you, 100f + call.word().length() * 10f, 100f);
            }
        });
        game.runHeadless(1);
        for (int step = 0; step < 5; step++) {
            if (shout) {
                game.postCommand(new Shout(0, "abc".repeat(step + 1)));
            }
            game.runHeadless(20);
        }
        return game.getLogic().checksum();
    }
}
