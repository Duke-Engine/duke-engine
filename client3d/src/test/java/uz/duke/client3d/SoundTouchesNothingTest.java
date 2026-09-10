package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import uz.duke.core.math.Coord3D;
import uz.duke.core.thing.ObjectId;
import uz.duke.game.DukeGame;
import uz.duke.rts.message.GameMessage;

/**
 * A game with the sound on runs exactly the game a game with the sound off runs.
 *
 * <p>Worth holding still rather than asserting from the design. The design is
 * sound — {@link GameSounds} is handed a snapshot and has no way to reach the
 * simulation, and it picks its files at random precisely because that choice
 * reaches nothing that is checksummed — but "there is no way to do X" is a claim
 * about the whole of a class, and it is a claim that decays. A wire from the
 * client back into the world would be an easy thing to add without noticing, and
 * it would show up as a replay that no longer replays.
 */
class SoundTouchesNothingTest {

    private static DukeGame newGame() {
        var game = DukeGame.create("sound-test").loadUnits(DukeGame.STARTER_UNITS);
        var one = game.addPlayer("One", Color.BLUE);
        var two = game.addPlayer("Two", Color.RED);
        game.enemies(one, two);
        game.spawn("Rifleman", one, 0, 0);
        game.spawn("Rifleman", two, 120, 0);
        return game;
    }

    /** A bank with something to say about everything, so nothing is skipped early. */
    private static Sounds noisy(SoundSink sink) {
        var bank = SoundBank.create()
                .cue("died", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("d1.ogg", "d2.ogg"))
                .cue("arrow_fired", SoundBank.Channel.EFFECTS, true, 1f, 0f,
                        List.of("f1.ogg", "f2.ogg"))
                .cue("walking.Rifleman", SoundBank.Channel.EFFECTS, true, 1f, 0.3f,
                        List.of("w1.ogg", "w2.ogg"))
                .cue("hurt.Rifleman", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("h.ogg"))
                .cue("gone.Rifleman", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("g.ogg"))
                .cue("struck.Rifleman", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("s.ogg"))
                .cue("spawned.Rifleman", SoundBank.Channel.EFFECTS, true, 1f, 0f,
                        List.of("a.ogg"))
                .cue("vo.kill", SoundBank.Channel.VOICE, false, 1f, 0f, List.of("k.ogg"))
                .build();
        return new Sounds(bank, sink);
    }

    /** Every checksum of a run, frame by frame. */
    private static List<Long> run(GameSounds listening) {
        var game = newGame();
        game.runHeadless(1);
        game.postCommand(new GameMessage.AttackObject(1, List.of(new ObjectId(1)),
                new ObjectId(2)));
        var checksums = new ArrayList<Long>();
        for (int frame = 0; frame < 400; frame++) {
            game.runHeadless(1);
            checksums.add(game.getLogic().checksum());
            if (listening != null) {
                listening.frame(game.getSnapshot(), 1, frame / 30f);
            }
        }
        return checksums;
    }

    /**
     * The same seed, once in silence and once with everything making a noise,
     * frame for frame.
     */
    @Test
    void theSameGameWithTheSoundOnIsTheSameGame() {
        var quiet = run(null);
        var heard = new Recorder();
        var loud = run(new GameSounds(noisy(heard), 20f));

        assertEquals(quiet, loud, "the worlds diverged with the sound on");
        assertTrue(heard.count > 0, "and there was actually something to hear");
    }

    /**
     * Twice over with the sound on, the two runs still agree.
     *
     * <p>The interesting half: the file chosen for each noise is drawn at random
     * and differs between the two runs, which is exactly what must not matter.
     */
    @Test
    void twoRunsThatSoundedDifferentStillMatch() {
        var first = new Recorder();
        var one = run(new GameSounds(noisy(first), 20f));
        var second = new Recorder();
        var two = run(new GameSounds(noisy(second), 20f));

        assertEquals(one, two);
        assertTrue(first.count > 0 && second.count > 0);
    }

    private static final class Recorder implements SoundSink {
        int count;

        @Override
        public void play(String assetPath, float gain, com.jme3.math.Vector3f at) {
            count++;
        }

        @Override
        public void music(String assetPath, float gain) {
        }

        @Override
        public void musicGain(float gain) {
        }
    }

    /** And the snapshot it was handed is not something it could write to anyway. */
    @Test
    void aSnapshotIsNotSomethingTheClientCanWriteTo() {
        var game = newGame();
        game.runHeadless(2);
        var snapshot = game.getSnapshot();

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> snapshot.units().clear());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> snapshot.events().clear());
    }

    /** A world replaced is a world forgotten: nothing from the old one is news. */
    @Test
    void anewWorldIsNotFullOfThingsThatJustArrived() {
        var heard = new Recorder();
        var sounds = new GameSounds(noisy(heard), 20f);
        var game = newGame();
        game.runHeadless(2);

        sounds.frame(game.getSnapshot(), 1, 0f);
        int afterFirst = heard.count;
        sounds.forget();
        sounds.frame(game.getSnapshot(), 1, 1f);

        assertTrue(heard.count > afterFirst,
                "after forgetting, everything standing there arrived again");
    }
}
