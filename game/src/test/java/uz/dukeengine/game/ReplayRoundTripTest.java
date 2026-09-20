package uz.dukeengine.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.dukeengine.core.math.Coord3D;
import uz.dukeengine.core.thing.ObjectId;
import uz.dukeengine.rts.message.GameMessage;

/**
 * The determinism harness: a real game, recorded and played back, must arrive at
 * the same world.
 *
 * <p>This is the check that costs the least and finds the most. Any change that
 * lets a wall clock, a hash order or a platform-specific function into the
 * simulation shows up here, in a build, with a frame number — instead of in
 * somebody's multiplayer game, as two players quietly seeing different things.
 */
class ReplayRoundTripTest {

    private static final int FRAMES = 300;

    /** A skirmish that fights, produces and scripts, so the replay has work to do. */
    private static DukeGame newGame() {
        var game = DukeGame.create("replay-test")
                .loadUnits(DukeGame.STARTER_UNITS)
                .map(60, 40);
        var you = game.addPlayer("You", Color.BLUE);
        var foe = game.addPlayer("Foe", Color.RED);
        game.enemies(you, foe).localPlayer(you);
        game.money(you, 2000).money(foe, 2000);

        game.spawn("PowerPlant", you, 60, 300)
                .spawn("Barracks", you, 120, 340)
                .spawn("Rifleman", you, 180, 300)
                .spawn("Tank", you, 150, 260);
        game.spawn("PowerPlant", foe, 520, 90)
                .spawn("Barracks", foe, 460, 60)
                .spawn("Rifleman", foe, 400, 100);

        // A scripted attack order: the simulation issues commands of its own, which
        // the replay must not apply on top of the ones it already has recorded.
        game.everySeconds(2, g -> g.postCommand(new GameMessage.MoveTo(
                2, List.of(new ObjectId(7)), new Coord3D(200f, 300f, 0f))));
        return game;
    }

    @Test
    void aRecordedGamePlaysBackToTheSameWorld() {
        var recorded = newGame().recordReplay();
        recorded.runHeadless(FRAMES);
        var text = recorded.getReplayText();

        var replayed = newGame().playReplay(text);
        replayed.runHeadless(FRAMES);

        var replay = replayed.getReplay();
        assertNull(replay.getMismatch(),
                () -> "the simulation is no longer deterministic: " + replay.getMismatch());
        assertEquals(recorded.getLogic().getFrame(), replayed.getLogic().getFrame());
        assertEquals(recorded.getLogic().checksum(), replayed.getLogic().checksum(),
                "same inputs, same world");
    }

    @Test
    void aRecordingIsSmallBecauseItHoldsInputRatherThanTheWorld() {
        var game = newGame().recordReplay();
        game.runHeadless(FRAMES);

        int lines = game.getReplayText().strip().split("\n").length;
        assertTrue(lines < FRAMES / 2,
                "300 frames of a battle should not need a line each; got " + lines);
        assertTrue(game.getLogic().getObjectCount() > 0, "and it was a real game");
    }
}
