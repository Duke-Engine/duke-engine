package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A track the game names: the old one fading out while it comes in, and the game's volume for music on top. */
class MusicFadeTest {

    /** A track as the sink plays it: how loud it was last made, and whether it was stopped. */
    private static final class Track implements SoundSink.Playing {
        final String path;
        float loudness;
        boolean stopped;

        Track(String path, float loudness) {
            this.path = path;
            this.loudness = loudness;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public void volume(float gain) {
            loudness = gain;
        }
    }

    private static final class Speakers implements SoundSink {
        final List<Track> tracks = new ArrayList<>();

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
        }

        @Override
        public Playing music(String assetPath, float gain) {
            var track = new Track(assetPath, gain);
            tracks.add(track);
            return track;
        }
    }

    private static SoundBank music() {
        return SoundBank.create()
                .cue("Shell", SoundBank.Channel.MUSIC, false, 1f, 0f, List.of("audio/music/shell.ogg"))
                .cue("Setup", SoundBank.Channel.MUSIC, false, 1f, 0f, List.of("audio/music/setup.ogg"))
                .build();
    }

    @Test
    void theTrackPlayingFadesOutOverTwoSecondsWhileTheNamedOneComesIn() {
        var speakers = new Speakers();
        var sounds = new Sounds(music(), speakers);
        sounds.music("Shell");
        var shell = speakers.tracks.getFirst();

        sounds.music("Setup", 2f, 0f);
        var setup = speakers.tracks.get(1);
        assertEquals(1f, setup.loudness, 1e-6f, "the new one at once, at its own loudness");
        assertFalse(shell.stopped);

        sounds.update(1f);
        assertEquals(0.5f, shell.loudness, 1e-6f, "half way out after a second");
        sounds.update(1f);
        assertTrue(shell.stopped, "and gone after two");
        assertFalse(setup.stopped);
    }

    @Test
    void theGamesVolumeForMusicTurnsThePlayingTrackDown() {
        var speakers = new Speakers();
        var sounds = new Sounds(music(), speakers);
        sounds.music("Shell");

        sounds.gameVolume(SoundBank.Channel.MUSIC, 0.7f);

        assertEquals(0.7f, speakers.tracks.getFirst().loudness, 1e-6f);
    }

    @Test
    void aTrackFadedInRisesToWhatTheKnobsSay() {
        var speakers = new Speakers();
        var sounds = new Sounds(music(), speakers);
        sounds.gameVolume(SoundBank.Channel.MUSIC, 0.5f);

        sounds.music("Shell", 0f, 2f);
        var shell = speakers.tracks.getFirst();
        assertEquals(0f, shell.loudness, 1e-6f, "from silence");
        sounds.update(1f);
        assertEquals(0.25f, shell.loudness, 1e-6f);
        sounds.update(1f);
        assertEquals(0.5f, shell.loudness, 1e-6f);
    }
}
