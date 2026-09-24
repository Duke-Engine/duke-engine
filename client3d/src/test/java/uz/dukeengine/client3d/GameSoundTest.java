package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/** A sound the game plays itself — a button pressed, a row picked — and how loud the game says each kind is. */
class GameSoundTest {

    private static final class Heard implements SoundSink {
        final List<String> files = new ArrayList<>();
        final List<Float> gains = new ArrayList<>();
        final List<Vector3f> places = new ArrayList<>();

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            files.add(assetPath);
            gains.add(gain);
            places.add(at);
        }

        @Override
        public Playing music(String assetPath, float gain) {
            return Playing.NONE;
        }
    }

    /** An interface click, written as a placed cue: played flat, it is heard at no place whatever it says. */
    private static SoundBank clicks() {
        return SoundBank.create()
                .cue("GUIClick", SoundBank.Channel.UI, true, 0.5f, 0f, List.of("audio/ui/click.wav"))
                .build();
    }

    @Test
    void aCuePlayedByTheGameReachesTheSinkOnceAndAtNoPlace() {
        var heard = new Heard();
        var sounds = new Sounds(clicks(), heard);

        assertTrue(sounds.flat("GUIClick", 0f));

        assertEquals(List.of("audio/ui/click.wav"), heard.files);
        assertEquals(1, heard.places.size());
        assertNull(heard.places.getFirst(), "flat: no position, so nothing falls away with distance");
        assertEquals(0.5f, heard.gains.getFirst(), 1e-6f, "at the cue's own loudness");
    }

    @Test
    void aCueTurnedUpSixfoldReachesTheSinkSixTimesAsLoud() {
        var heard = new Heard();
        var sounds = new Sounds(clicks(), heard);

        sounds.flat("GUIClick", 0f);
        sounds.cueVolume("GUIClick", 6f);
        sounds.flat("GUIClick", 1f);
        sounds.cueVolume("GUIClick", 1f);
        sounds.flat("GUIClick", 2f);

        assertEquals(6f * heard.gains.get(0), heard.gains.get(1), 1e-6f,
                "handed on six times as loud, for the sink to clamp where it clamps");
        assertEquals(heard.gains.get(0), heard.gains.get(2), 1e-6f, "and back to its own at 1");
    }

    @Test
    void theGamesVolumeForAChannelIsMultipliedWithThePlayers() {
        var heard = new Heard();
        var sounds = new Sounds(clicks(), heard);
        sounds.volume(SoundBank.Channel.UI, 0.5f);
        sounds.gameVolume(SoundBank.Channel.UI, 0.2f);

        sounds.flat("GUIClick", 0f);
        sounds.gameVolume(SoundBank.Channel.UI, 0f);

        assertEquals(0.5f * 0.5f * 0.2f, heard.gains.getFirst(), 1e-6f);
        assertFalse(sounds.flat("GUIClick", 1f), "turned down to nothing, nothing is played");
    }

    @Test
    void aNameTheGameNeverWrotePlaysNothingAndIsSaidOnce() {
        var heard = new Heard();
        var sounds = new Sounds(clicks(), heard);
        var said = new ArrayList<String>();
        var logger = Logger.getLogger(Sounds.class.getName());
        var listening = new Handler() {
            @Override
            public void publish(LogRecord record) {
                said.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(listening);
        try {
            assertFalse(sounds.flat("GUIClik", 0f));
            assertFalse(sounds.flat("GUIClik", 1f));
        } finally {
            logger.removeHandler(listening);
        }

        assertEquals(List.of(), heard.files);
        assertEquals(1, said.size(), "said once, not every time: " + said);
        assertTrue(said.getFirst().contains("GUIClik"));
    }
}
