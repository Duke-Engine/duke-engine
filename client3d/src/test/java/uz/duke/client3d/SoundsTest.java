package uz.duke.client3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The rules about noise, which are worth checking and need no speaker.
 *
 * <p>Every one of them fails quietly if it is wrong. A cue nobody named plays
 * nothing; a gap that does not hold turns a walk into a buzz; a rotation that
 * does not rotate gives the sample away. None of that throws, none of it shows up
 * in a screenshot, and all of it is arithmetic — which is exactly the shape of
 * thing to hold still in a build rather than in an ear.
 */
class SoundsTest {

    /** A sink that writes down what it was asked to play. */
    private static final class Heard implements SoundSink {
        final List<String> files = new ArrayList<>();
        final List<Float> gains = new ArrayList<>();
        final List<Vector3f> places = new ArrayList<>();
        String music;

        @Override
        public void play(String assetPath, float gain, Vector3f at) {
            files.add(assetPath);
            gains.add(gain);
            places.add(at);
        }

        @Override
        public void music(String assetPath, float gain) {
            music = assetPath;
        }

        @Override
        public void musicGain(float gain) {
        }
    }

    private static SoundBank bank(String name, SoundBank.Channel channel, String... files) {
        return SoundBank.create()
                .cue(name, channel, true, 1f, 0f, List.of(files))
                .build();
    }

    // ---- what the game named, and what it did not ----

    /** A moment the game never described makes no sound and no trouble. */
    @Test
    void aMomentNobodyDescribedIsSilent() {
        var heard = new Heard();
        var sounds = new Sounds(SoundBank.silent(), heard);

        assertFalse(sounds.play("arrow_fired", 0f));

        assertEquals(List.of(), heard.files);
    }

    /**
     * A dotted name falls back to the plain one.
     *
     * <p>Which is what keeps every creature's name out of the client: it asks for
     * the particular thing and takes whatever the game troubled to write.
     */
    @Test
    void aParticularNameFallsBackToThePlainOne() {
        var bank = SoundBank.create()
                .cue("died", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("thud.ogg"))
                .cue("died.Boss", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("gong.ogg"))
                .build();
        var heard = new Heard();
        var sounds = new Sounds(bank, heard);

        sounds.play("died.Boss", 0f);
        sounds.play("died.Skeleton", 0f);

        assertEquals(List.of("gong.ogg", "thud.ogg"), heard.files,
                "the boss has its own; the skeleton takes the general one");
    }

    // ---- one of several ----

    /**
     * The same moment twice running does not sound the same twice running.
     *
     * <p>The whole reason a cue has more than one file. Left to chance alone a
     * three-file bow repeats itself one shot in three, and the repetition is
     * exactly what the ear catches — it is never the sample that gives a sample
     * away.
     */
    @Test
    void theSameMomentNeverSoundsTheSameTwiceRunning() {
        var heard = new Heard();
        var sounds = new Sounds(bank("shot", SoundBank.Channel.EFFECTS,
                "a.ogg", "b.ogg", "c.ogg"), heard);

        for (int i = 0; i < 60; i++) {
            sounds.play("shot", i);
        }

        assertEquals(60, heard.files.size());
        for (int i = 1; i < heard.files.size(); i++) {
            assertFalse(heard.files.get(i).equals(heard.files.get(i - 1)),
                    "heard " + heard.files.get(i) + " twice in a row at " + i);
        }
        assertTrue(heard.files.stream().distinct().count() == 3, "and all three were used");
    }

    /** With only one recording of a thing, that is what it sounds like every time. */
    @Test
    void oneFileIsPlayedEveryTime() {
        var heard = new Heard();
        var sounds = new Sounds(bank("gong", SoundBank.Channel.EFFECTS, "gong.ogg"), heard);

        sounds.play("gong", 0f);
        sounds.play("gong", 1f);

        assertEquals(List.of("gong.ogg", "gong.ogg"), heard.files);
    }

    // ---- gaps ----

    /**
     * A cue with a stride will not go again inside it.
     *
     * <p>Footsteps. The client knows he is walking on every frame he is walking,
     * and a sound per frame is not a walk.
     */
    @Test
    void aCueWithAStrideWaitsForIt() {
        var bank = SoundBank.create()
                .cue("footstep", SoundBank.Channel.EFFECTS, true, 1f, 0.3f,
                        List.of("a.ogg", "b.ogg"))
                .build();
        var heard = new Heard();
        var sounds = new Sounds(bank, heard);

        assertTrue(sounds.play("footstep", 10.0f));
        assertFalse(sounds.play("footstep", 10.2f), "inside the stride");
        assertTrue(sounds.play("footstep", 10.31f), "and past it");

        assertEquals(2, heard.files.size());
    }

    /**
     * One voice at a time, whatever it is saying.
     *
     * <p>Per channel rather than per line, because what is being prevented is two
     * people talking over each other and it is no better when they are saying
     * different things. A player clicking around the floor gives an order every
     * frame he feels like it.
     */
    @Test
    void sheFinishesOneLineBeforeStartingAnother() {
        var bank = SoundBank.create()
                .cue("vo.move", SoundBank.Channel.VOICE, false, 1f, 0f, List.of("go.ogg"))
                .cue("vo.attack", SoundBank.Channel.VOICE, false, 1f, 0f, List.of("at.ogg"))
                .build();
        var heard = new Heard();
        var sounds = new Sounds(bank, heard);
        sounds.voiceGap(1.5f);

        assertTrue(sounds.play("vo.move", 0f));
        assertFalse(sounds.play("vo.attack", 0.5f), "a different line is still a second voice");
        assertFalse(sounds.play("vo.move", 1.4f));
        assertTrue(sounds.play("vo.attack", 1.6f));

        assertEquals(List.of("go.ogg", "at.ogg"), heard.files);
    }

    /** And the gap is the voice's alone — an arrow does not wait for her. */
    @Test
    void theGapIsTheVoicesAlone() {
        var bank = SoundBank.create()
                .cue("vo.move", SoundBank.Channel.VOICE, false, 1f, 0f, List.of("go.ogg"))
                .cue("shot", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("shot.ogg"))
                .build();
        var heard = new Heard();
        var sounds = new Sounds(bank, heard);
        sounds.voiceGap(1.5f);

        sounds.play("vo.move", 0f);
        assertTrue(sounds.play("shot", 0.1f));
        assertTrue(sounds.play("shot", 0.2f));
    }

    // ---- the knobs ----

    /** A channel turned off is a channel that costs nothing to have. */
    @Test
    void aChannelTurnedOffPlaysNothing() {
        var heard = new Heard();
        var sounds = new Sounds(bank("vo.move", SoundBank.Channel.VOICE, "go.ogg"), heard);

        sounds.volume(SoundBank.Channel.VOICE, 0f);

        assertFalse(sounds.play("vo.move", 0f));
        assertEquals(List.of(), heard.files);
    }

    /** Loudness is the cue's own, its channel's, and the master's, multiplied. */
    @Test
    void loudnessIsEveryKnobAtOnce() {
        var bank = SoundBank.create()
                .cue("shot", SoundBank.Channel.EFFECTS, true, 0.5f, 0f, List.of("a.ogg"))
                .build();
        var heard = new Heard();
        var sounds = new Sounds(bank, heard);
        sounds.masterVolume(0.5f);
        sounds.volume(SoundBank.Channel.EFFECTS, 0.5f);

        sounds.play("shot", 0f);

        assertEquals(0.125f, heard.gains.get(0), 0.0001f);
    }

    // ---- where ----

    /** Something that happened somewhere is played there; a menu click is not. */
    @Test
    void onlyWhatHappenedSomewhereIsPlacedSomewhere() {
        var bank = SoundBank.create()
                .cue("shot", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("a.ogg"))
                .cue("click", SoundBank.Channel.UI, false, 1f, 0f, List.of("b.ogg"))
                .build();
        var heard = new Heard();
        var sounds = new Sounds(bank, heard);
        var where = new Vector3f(10f, 0f, 20f);

        sounds.play("shot", where, 0f);
        sounds.play("click", where, 0f);

        assertEquals(where, heard.places.get(0));
        assertNull(heard.places.get(1), "a click has no position to be quieter from");
    }

    // ---- music ----

    /** Asking for the loop that is already playing does not start it again. */
    @Test
    void theMusicIsNotRestartedByBeingAskedForTwice() {
        var bank = SoundBank.create()
                .cue("music.dungeon", SoundBank.Channel.MUSIC, false, 1f, 0f,
                        List.of("loop.ogg"))
                .build();
        var heard = new Heard();
        var sounds = new Sounds(bank, heard);

        sounds.music("music.dungeon");
        assertEquals("loop.ogg", heard.music);

        heard.music = "sentinel";
        sounds.music("music.dungeon");
        assertEquals("sentinel", heard.music, "it was already playing; nothing was said");

        sounds.music(null);
        assertNull(heard.music);
    }

    /** A game with no music runs, and says nothing about it. */
    @Test
    void aGameWithNoMusicIsSimplyQuiet() {
        var heard = new Heard();
        var sounds = new Sounds(SoundBank.silent(), heard);

        sounds.music("music.dungeon");

        assertNull(heard.music);
    }

    // ---- the machine with no speaker ----

    /**
     * On a machine that cannot make a sound, everything above still runs.
     *
     * <p>Which is what a headless build is, and what a test suite is. The
     * alternative is every caller asking first whether there is a speaker, and one
     * of them eventually forgetting.
     */
    @Test
    void aMachineWithNoSpeakerChangesNothingAboveIt() {
        var sounds = new Sounds(bank("shot", SoundBank.Channel.EFFECTS, "a.ogg", "b.ogg"),
                SoundSink.SILENT);

        assertTrue(sounds.play("shot", 0f), "the rules still ran");
        sounds.music("shot");
        sounds.masterVolume(0.3f);
    }

    /** Every file the game may want, once each, for reading before it is wanted. */
    @Test
    void itCanListEveryFileItMayNeed() {
        var bank = SoundBank.create()
                .cue("a", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of("x.ogg", "y.ogg"))
                .cue("b", SoundBank.Channel.UI, false, 1f, 0f, List.of("y.ogg", "z.ogg"))
                .build();

        assertEquals(List.of("x.ogg", "y.ogg", "z.ogg"),
                new Sounds(bank, SoundSink.SILENT).everyFile());
    }

    /** A cue declared with no files is a cue nobody has recorded yet. */
    @Test
    void aCueWithNoFilesIsNotACue() {
        var bank = SoundBank.create()
                .cue("nothing", SoundBank.Channel.EFFECTS, true, 1f, 0f, List.of())
                .build();

        assertTrue(bank.isEmpty());
    }
}
