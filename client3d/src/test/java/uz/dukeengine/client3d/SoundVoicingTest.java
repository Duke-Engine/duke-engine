package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * A sound's own variation, its parts and one line at a time from a thing — the reference's {@code
 * AudioEventRTS::generatePlayInfo}, {@code MilesAudioManager::startNextLoop} and {@code isObjectPlayingVoice}.
 */
class SoundVoicingTest {

    /** A sink whose sounds last as long as their files say, on a clock the test moves. */
    private static final class Timed implements SoundSink {
        record Started(String file, float at, float gain, float pitch) {
        }

        final Map<String, Float> lengths;
        final List<Started> started = new ArrayList<>();
        float now;

        Timed(Map<String, Float> lengths) {
            this.lengths = lengths;
        }

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            play(assetPath, gain, 1f, at, Range.OWN);
        }

        @Override
        public void play(String assetPath, float gain, float pitch, Vector3f at, Range range) {
            started.add(new Started(assetPath, now, gain, pitch));
        }

        @Override
        public Playing playStoppable(String assetPath, float gain, float pitch, Vector3f at, Range range) {
            play(assetPath, gain, pitch, at, range);
            float begun = now;
            float length = lengths.getOrDefault(assetPath, 1f);
            return new Playing() {
                boolean cut;

                @Override
                public void stop() {
                    cut = true;
                }

                @Override
                public boolean ended() {
                    return cut || now - begun >= length - 1e-4f;
                }
            };
        }

        @Override
        public Playing music(String assetPath, float gain) {
            return Playing.NONE;
        }

        List<String> files() {
            return started.stream().map(Started::file).toList();
        }
    }

    private static SoundBank.Cue cue(String name, List<String> files, SoundBank.Voicing voicing) {
        return new SoundBank.Cue(name, SoundBank.Channel.EFFECTS, true, 1f, 0f, files, null).voiced(voicing);
    }

    private static Sounds sounds(Timed sink, SoundBank.Cue cue) {
        return new Sounds(SoundBank.create().cue(cue).build(), sink, new SplittableRandom(17));
    }

    /** {@code seconds} of the window, a tenth at a time. */
    private static void pass(Sounds sounds, Timed sink, float seconds) {
        for (int step = 0; step < Math.round(seconds * 10f); step++) {
            sink.now += 0.1f;
            sounds.update(0.1f);
        }
    }

    @Test
    void aRateOf09To11DrawnAThousandTimesStaysWithinItAndSpreadsAndOneOf22PlaysAtTheDevicesMost() {
        var sink = new Timed(Map.of());
        var sounds = sounds(sink, cue("shot", List.of("x.wav"),
                new SoundBank.Voicing(0.9f, 1.1f, 1f, List.of(), List.of(), 0f, 0f, false)));
        for (int play = 0; play < 1000; play++) {
            sounds.play("shot", new Vector3f(), play);
        }
        var rates = sink.started.stream().map(Timed.Started::pitch).toList();
        assertTrue(rates.stream().allMatch(rate -> rate >= 0.9f && rate <= 1.1f), "every rate within it");
        assertTrue(rates.stream().anyMatch(rate -> rate < 0.95f) && rates.stream().anyMatch(rate -> rate > 1.05f));

        var high = new Timed(Map.of());
        sounds(high, cue("squeak", List.of("s.wav"),
                new SoundBank.Voicing(2.2f, 2.2f, 1f, List.of(), List.of(), 0f, 0f, false)))
                .play("squeak", new Vector3f(), 0f);
        assertEquals(Sounds.MOST_PITCH, high.started.getFirst().pitch(), "at the device's most");
    }

    @Test
    void aLoudnessOf08To1PlaysEachBetween08And1OfItsGain() {
        var sink = new Timed(Map.of());
        var sounds = sounds(sink, cue("shot", List.of("x.wav"),
                new SoundBank.Voicing(1f, 1f, 0.8f, List.of(), List.of(), 0f, 0f, false)));
        for (int play = 0; play < 200; play++) {
            sounds.play("shot", new Vector3f(), play);
        }
        assertTrue(sink.started.stream().allMatch(one -> one.gain() >= 0.8f && one.gain() <= 1f));
        assertTrue(sink.started.stream().anyMatch(one -> one.gain() < 0.9f));
    }

    @Test
    void aLoopPlaysItsAttackThenPassesAPauseApartAndStoppedEndsThePassAndDecays() {
        var sink = new Timed(Map.of("a.wav", 1f, "x.wav", 2f, "y.wav", 2f, "d.wav", 1f));
        var sounds = sounds(sink, cue("engine", List.of("x.wav", "y.wav"),
                new SoundBank.Voicing(1f, 1f, 1f, List.of("a.wav"), List.of("d.wav"), 6f, 10f, false)));
        var loop = sounds.loop("engine", new Vector3f(), true);
        while (sink.started.isEmpty() && sink.now < 20f) {
            pass(sounds, sink, 0.1f);
        }
        assertEquals(List.of("a.wav"), sink.files(), "its attack, after a pause of 6 to 10");
        float attack = sink.started.getFirst().at();
        assertTrue(attack >= 6f - 1e-3f && attack <= 10.1f, "at " + attack);

        pass(sounds, sink, 1f);
        assertEquals(2, sink.started.size(), "then a pass");
        assertTrue(List.of("x.wav", "y.wav").contains(sink.started.get(1).file()));
        assertEquals(attack + 1f, sink.started.get(1).at(), 0.11f, "as the attack ends");

        while (sink.started.size() < 3 && sink.now < 60f) {
            pass(sounds, sink, 0.1f);
        }
        assertEquals(3, sink.started.size(), "another pass after the pause");
        float silence = sink.started.get(2).at() - (sink.started.get(1).at() + 2f);
        assertTrue(silence >= 6f - 0.11f && silence <= 10f + 0.11f, "6 to 10 s of silence: " + silence);

        pass(sounds, sink, 1f); // halfway through that pass
        loop.stop();
        pass(sounds, sink, 0.5f);
        assertEquals(3, sink.started.size(), "the pass plays on");
        pass(sounds, sink, 1f);
        assertEquals("d.wav", sink.started.getLast().file(), "then the decay");
        pass(sounds, sink, 30f);
        assertEquals(4, sink.started.size(), "then nothing");
        assertTrue(loop.ended());
    }

    @Test
    void aOneShotPlaysItsAttackItsFileAndItsDecayBackToBackWithinAPauseOf08() {
        var sink = new Timed(Map.of("a.wav", 0.5f, "x.wav", 1f, "d.wav", 0.5f));
        var sounds = sounds(sink, cue("radio", List.of("x.wav"),
                new SoundBank.Voicing(1f, 1f, 1f, List.of("a.wav"), List.of("d.wav"), 0f, 0.8f, false)));
        sounds.play("radio", new Vector3f(), 0f);
        pass(sounds, sink, 3f);

        assertEquals(List.of("a.wav", "x.wav", "d.wav"), sink.files());
        var at = sink.started.stream().map(Timed.Started::at).toList();
        assertTrue(at.getFirst() <= 0.8f + 0.11f, "the attack within 0.8 s: " + at.getFirst());
        assertEquals(at.get(0) + 0.5f, at.get(1), 0.11f, "back to back");
        assertEquals(at.get(1) + 1f, at.get(2), 0.11f);
    }

    @Test
    void aVoiceAboutAThingIsNotPlayedWhileOneAboutItIsPlaying() {
        var sink = new Timed(Map.of("line.wav", 2f));
        var sounds = sounds(sink, cue("vo", List.of("line.wav"),
                new SoundBank.Voicing(1f, 1f, 1f, List.of(), List.of(), 0f, 0f, true)));
        assertTrue(sounds.play("vo", new Vector3f(), 0f, true, 1));
        pass(sounds, sink, 1f);
        assertFalse(sounds.play("vo", new Vector3f(), 1f, true, 1), "thing 1 still speaking");
        assertTrue(sounds.play("vo", new Vector3f(), 1f, true, 2), "thing 2 is not");
        pass(sounds, sink, 1.1f);
        assertTrue(sounds.play("vo", new Vector3f(), 2.1f, true, 1), "thing 1 done: it speaks again");
    }
}
