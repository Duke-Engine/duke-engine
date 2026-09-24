package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A game that speaks one line at a time is told when each has played out. */
class SoundEndedTest {

    /** Speakers with a clock: each file lasts as long as the test says, from when it was started. */
    private static final class Speakers implements SoundSink {
        private final Map<String, Float> lengths;
        float now;

        Speakers(Map<String, Float> lengths) {
            this.lengths = lengths;
        }

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
        }

        @Override
        public Playing playStoppable(String assetPath, float gain, Vector3f at) {
            float until = now + lengths.getOrDefault(assetPath, 0f);
            return new Playing() {
                private boolean stopped;

                @Override
                public void stop() {
                    stopped = true;
                }

                @Override
                public boolean ended() {
                    return stopped || now >= until;
                }
            };
        }

        @Override
        public Playing music(String assetPath, float gain) {
            return Playing.NONE;
        }
    }

    private final Speakers speakers = new Speakers(Map.of("audio/voice/eva_building_complete.ogg", 2f));
    private final Sounds sounds = new Sounds(SoundBank.create()
            .cue("eva.buildingComplete", SoundBank.Channel.VOICE, false, 1f, 0f,
                    List.of("audio/voice/eva_building_complete.ogg"))
            .cue("eva.silent", SoundBank.Channel.VOICE, false, 1f, 0f, List.of())
            .build(), speakers);

    /** The window's frames, a thirtieth of a second each, until {@code seconds} have gone by. */
    private void frames(float seconds) {
        for (float gone = 0f; gone < seconds; gone += 1f / 30f) {
            speakers.now += 1f / 30f;
            sounds.update(1f / 30f);
        }
    }

    @Test
    void aTwoSecondLineIsToldEndedAboutTwoSecondsAfterItStarted() {
        var endedAt = new ArrayList<Float>();

        sounds.flat("eva.buildingComplete", 0f, () -> endedAt.add(speakers.now));
        frames(1.9f);
        assertTrue(endedAt.isEmpty(), "still speaking");
        frames(0.3f);

        assertEquals(1, endedAt.size(), "told once");
        assertEquals(2f, endedAt.getFirst(), 1f / 30f + 1e-4f);
    }

    @Test
    void aCueWithNoFileEndsAtOnce() {
        var told = new ArrayList<String>();

        sounds.flat("eva.silent", 0f, () -> told.add("silent"));
        sounds.flat("eva.neverNamed", 0f, () -> told.add("unknown"));

        assertEquals(List.of("silent", "unknown"), told, "nothing played, so nothing to wait for");
    }

    @Test
    void theNextLineMayBeSpokenFromTheLastOnesEnd() {
        var spoken = new ArrayList<Float>();
        Runnable[] next = new Runnable[1];
        next[0] = () -> {
            spoken.add(speakers.now);
            if (spoken.size() < 2) {
                sounds.flat("eva.buildingComplete", speakers.now, next[0]);
            }
        };

        sounds.flat("eva.buildingComplete", 0f, next[0]);
        frames(4.5f);

        assertEquals(2, spoken.size(), "one line after the other, never over each other");
        assertEquals(4f, spoken.get(1), 2f / 30f + 1e-4f);
    }
}
