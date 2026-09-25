package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * How many of a sound play at once and which gives way, as the reference holds its sounds to budgets
 * ({@code doesViolateLimit}, {@code killLowestPrioritySoundImmediately}): a cue's limit, and the game's budget of
 * sounds at once shared by priority.
 */
class SoundLimitsTest {

    /** A sink whose sounds play until stopped, writing down what started and what was stopped. */
    private static final class Heard implements SoundSink {
        final List<String> started = new ArrayList<>();
        final List<String> stopped = new ArrayList<>();

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            started.add(assetPath);
        }

        @Override
        public Playing playStoppable(String assetPath, float gain, Vector3f at) {
            int number = started.size();
            started.add(assetPath);
            var tag = assetPath + "#" + number;
            return new Playing() {
                boolean over;

                @Override
                public void stop() {
                    over = true;
                    stopped.add(tag);
                }

                @Override
                public boolean ended() {
                    return over;
                }
            };
        }

        @Override
        public Playing music(String assetPath, float gain) {
            return Playing.NONE;
        }
    }

    private static final Vector3f HERE = new Vector3f(10f, 0f, 10f);

    @Test
    void aCueLimitedToTwoAskedThreeTimesInAFramePlaysTwo() {
        var bank = SoundBank.create().cue("gun", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("gun.ogg"), null,
                SoundBank.Audience.EVERYONE, false, 2, SoundBank.Priority.NORMAL).build();
        var heard = new Heard();
        var sounds = new Sounds(bank, heard);

        assertTrue(sounds.play("gun", HERE, 0f));
        assertTrue(sounds.play("gun", HERE, 0f));
        assertFalse(sounds.play("gun", HERE, 0f), "the third past its limit");
        assertEquals(2, heard.started.size());
        assertTrue(sounds.play("gun", null, 0f), "a flat one is counted apart");
    }

    @Test
    void anInterruptingCueLimitedToOneAskedTwicePlaysTheSecondAndStopsTheFirst() {
        var bank = SoundBank.create().cue("voice", SoundBank.Channel.VOICE, false, 1f, 0f, List.of("yes.ogg"), null,
                SoundBank.Audience.EVERYONE, true, 1, SoundBank.Priority.HIGH).build();
        var heard = new Heard();
        var sounds = new Sounds(bank, heard);

        sounds.play("voice", null, 0f);
        assertTrue(sounds.play("voice", null, 0f));
        assertEquals(2, heard.started.size(), "the second plays");
        assertEquals(List.of("yes.ogg#0"), heard.stopped, "and the first is stopped");
    }

    @Test
    void withABudgetOfTwoHeldByTwoLowCuesAHighOneStopsOneAndPlaysALowestOneIsDropped() {
        var bank = SoundBank.create().budget(2, 4)
                .cue("birds", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("birds.ogg"), null,
                        SoundBank.Audience.EVERYONE, false, 0, SoundBank.Priority.LOW)
                .cue("nuke", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("nuke.ogg"), null,
                        SoundBank.Audience.EVERYONE, false, 0, SoundBank.Priority.HIGH)
                .cue("rustle", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("rustle.ogg"), null,
                        SoundBank.Audience.EVERYONE, false, 0, SoundBank.Priority.LOWEST)
                .build();
        var heard = new Heard();
        var sounds = new Sounds(bank, heard);
        sounds.play("birds", HERE, 0f);
        sounds.play("birds", HERE, 0f);

        assertTrue(sounds.play("nuke", HERE, 0f), "a high one plays");
        assertEquals(List.of("birds.ogg#0"), heard.stopped, "stopping the oldest of the low ones");
        assertFalse(sounds.play("rustle", HERE, 0f), "a lowest one finds nothing lower, and is dropped");
        assertEquals(List.of("birds.ogg", "birds.ogg", "nuke.ogg"), heard.started);
    }
}
