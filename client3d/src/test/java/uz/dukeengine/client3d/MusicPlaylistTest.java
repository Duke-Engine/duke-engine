package uz.dukeengine.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Music the game chooses: one track on a loop, or a list of them in turn and round again. */
class MusicPlaylistTest {

    /** A track as the sink plays it: its file, whether it loops, how loud it is, and whether it ended or was stopped. */
    private static final class Track implements SoundSink.Playing {
        final String path;
        final boolean looping;
        float loudness;
        boolean ended;
        boolean stopped;

        Track(String path, boolean looping, float loudness) {
            this.path = path;
            this.looping = looping;
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

        @Override
        public boolean ended() {
            return ended;
        }
    }

    private static final class Speakers implements SoundSink {
        final List<Track> tracks = new ArrayList<>();

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
        }

        @Override
        public Playing music(String assetPath, float gain) {
            return add(new Track(assetPath, true, gain));
        }

        @Override
        public Playing musicOnce(String assetPath, float gain) {
            return add(new Track(assetPath, false, gain));
        }

        private Track add(Track track) {
            tracks.add(track);
            return track;
        }

        Track last() {
            return tracks.getLast();
        }
    }

    private final Speakers speakers = new Speakers();
    private final Sounds sounds = new Sounds(SoundBank.create()
            .cue("Battle1", SoundBank.Channel.MUSIC, false, 1f, 0f, List.of("audio/music/battle_1.ogg"))
            .cue("Battle2", SoundBank.Channel.MUSIC, false, 1f, 0f, List.of("audio/music/battle_2.ogg"))
            .cue("Victory", SoundBank.Channel.MUSIC, false, 1f, 0f, List.of("audio/music/victory.ogg"))
            .build(), speakers);

    @Test
    void aPlaylistPlaysEachTrackOnceInTurnAndRoundAgain() {
        sounds.playlist(List.of("Battle1", "Battle2"), 0f, 0f);
        assertEquals("audio/music/battle_1.ogg", speakers.last().path);
        assertFalse(speakers.last().looping, "once through, so the next can follow");

        speakers.last().ended = true;
        sounds.update(0.03f);
        assertEquals("audio/music/battle_2.ogg", speakers.last().path);

        speakers.last().ended = true;
        sounds.update(0.03f);
        assertEquals("audio/music/battle_1.ogg", speakers.last().path, "round again from the first");
        assertEquals(3, speakers.tracks.size());
    }

    @Test
    void theListPlayingAskedForAgainIsNotStartedAgain() {
        sounds.playlist(List.of("Battle1", "Battle2"), 0f, 0f);
        speakers.last().ended = true;
        sounds.update(0.03f);

        sounds.playlist(List.of("Battle1", "Battle2"), 0f, 0f);

        assertEquals(2, speakers.tracks.size());
        assertFalse(speakers.last().stopped, "the second goes on where it was");
    }

    @Test
    void nothingStopsTheListAndNothingFollows() {
        sounds.playlist(List.of("Battle1", "Battle2"), 0f, 0f);

        sounds.music(null);
        speakers.last().ended = true;
        sounds.update(0.03f);

        assertTrue(speakers.tracks.getFirst().stopped);
        assertEquals(1, speakers.tracks.size(), "silence, not the next in the list");
    }

    @Test
    void aTrackAskedForAloneReplacesTheListAndLoops() {
        sounds.playlist(List.of("Battle1", "Battle2"), 0f, 0f);

        sounds.music("Battle1");

        assertTrue(speakers.tracks.getFirst().stopped);
        assertEquals("audio/music/battle_1.ogg", speakers.last().path);
        assertTrue(speakers.last().looping, "alone, from its start, over and over");
        sounds.music("Battle1");
        assertEquals(2, speakers.tracks.size(), "and the same one again changes nothing");
    }

    @Test
    void theVolumeScalesTheListsTracks() {
        sounds.gameVolume(SoundBank.Channel.MUSIC, 0.5f);
        sounds.playlist(List.of("Battle1", "Battle2"), 0f, 0f);
        assertEquals(0.5f, speakers.last().loudness, 1e-6f);

        sounds.volume(SoundBank.Channel.MUSIC, 0.4f);
        assertEquals(0.2f, speakers.last().loudness, 1e-6f, "the player's knob times the game's");

        speakers.last().ended = true;
        sounds.update(0.03f);
        assertEquals(0.2f, speakers.last().loudness, 1e-6f, "and the next comes in as loud");
    }

    @Test
    void aTrackTheGameDoesNotHaveIsPassedOver() {
        sounds.playlist(List.of("Missing", "Victory"), 0f, 0f);

        assertEquals("audio/music/victory.ogg", speakers.last().path);
        speakers.last().ended = true;
        sounds.update(0.03f);
        assertEquals("audio/music/victory.ogg", speakers.last().path, "a list of one plays it again");
        assertEquals(2, speakers.tracks.size());
    }
}
